package pro.sketchware.ai.agent;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

import pro.sketchware.ai.live.UiPoster;
import pro.sketchware.ai.core.AIMessage;
import pro.sketchware.ai.core.AIProvider;
import pro.sketchware.ai.core.AIRequest;
import pro.sketchware.ai.core.AIResponse;
import pro.sketchware.ai.core.StreamCallbacks;
import pro.sketchware.ai.core.ToolCall;
import pro.sketchware.ai.core.ToolSpec;

public final class AgentOrchestrator {

    /** Stable identity and operating policy shared by every agent request/provider. */
    public static final String DEFAULT_SYSTEM_PROMPT = AgentSystemPrompt.DEFAULT;

    private final AIProvider provider;
    private final ToolRegistry registry;
    private final Tool.ToolContext context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean isCancelled = false;
    private final AtomicBoolean runActive = new AtomicBoolean(false);
    private static final ConcurrentHashMap<String, AtomicBoolean> PROJECT_RUNS = new ConcurrentHashMap<>();
    private volatile Thread workerThread;
    private volatile boolean waitingForApproval = false;
    private volatile AIProvider.Handle activeHandle;
    private volatile SessionState state = SessionState.IDLE;

    public SessionState getState() { return state; }

    public AgentOrchestrator(AIProvider provider, ToolRegistry registry, Tool.ToolContext context) {
        this.provider = provider;
        this.registry = registry;
        this.context = context;
    }

    public void cancel() {
        isCancelled = true;
        AIProvider.Handle handle = activeHandle;
        if (handle != null) handle.cancel();
        // Interrupts blocking approval waits as well as interruptible provider waits.
        Thread worker = workerThread;
        if (waitingForApproval && worker != null) worker.interrupt();
    }

    public void skipWait() {
        if (activeHandle != null) activeHandle.skipWait();
    }

    public void run(String systemPrompt, List<AIMessage> messages, String model, float temperature, StreamCallbacks callbacks) {
        final String projectId = context.sc_id;
        if (projectId == null || projectId.trim().isEmpty()) {
            UiPoster.post(() -> callbacks.onError(new IllegalStateException(
                    "Agent task rejected because no active project is bound to it.")));
            return;
        }
        final AtomicBoolean projectRun = PROJECT_RUNS.computeIfAbsent(projectId, key -> new AtomicBoolean(false));
        if (!projectRun.compareAndSet(false, true)) {
            UiPoster.post(() -> callbacks.onError(new IllegalStateException(
                    "Another agent task is already modifying this project. Wait for it to finish or cancel it.")));
            return;
        }
        if (!runActive.compareAndSet(false, true)) {
            projectRun.set(false);
            UiPoster.post(() -> callbacks.onError(
                    new IllegalStateException("This agent instance already has a running task.")));
            return;
        }
        isCancelled = false;
        state = SessionState.MODEL_STREAMING;
        try {
        executor.execute(() -> {
            workerThread = Thread.currentThread();
            AIResponse lastResponse = null;
            boolean terminalCallbackSent = false;
            List<String> taskToolFailures = new ArrayList<>();
            try {
                List<AIMessage> conversation = new ArrayList<>(messages);
                int iterations = 0;
                final int MAX_ITERATIONS = 10;
                final long taskDeadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);

                while (iterations < MAX_ITERATIONS && !isCancelled
                        && System.currentTimeMillis() < taskDeadline) {
                    iterations++;

                    List<pro.sketchware.ai.core.ToolSpec> toolSpecs = new ArrayList<>();
                    for (Tool t : registry.all()) {
                        Tool.ToolSpec declared = registry.spec(t);
                        if (declared == null) {
                            throw new IllegalStateException("A registered tool has no validated schema snapshot.");
                        }
                        toolSpecs.add(new pro.sketchware.ai.core.ToolSpec(
                                declared.name, declared.description, declared.parameters));
                    }

                    AIRequest request = new AIRequest.Builder()
                            .model(model)
                            .systemPrompt(systemPrompt)
                            .messages(conversation)
                            .temperature(temperature)
                            .tools(toolSpecs)
                            .build();

                    try {
                        state = SessionState.MODEL_STREAMING;
                        AIResponse response = fetchBlocking(request, callbacks, taskDeadline);
                        lastResponse = response;

                        if (isCancelled) break;

                        if (response.toolCalls.isEmpty()) {
                            break;
                        } else {
                            conversation.add(AIMessage.assistantWithToolCalls(response.toolCalls));
                        }

                        state = SessionState.TOOL_RUNNING;
                        for (ToolCall call : response.toolCalls) {
                            if (isCancelled) break;

                            // StreamCallbacks is a background-thread contract; UI consumers post once themselves.
                            callbacks.onToolStart(call.name);

                            boolean success = false;
                            String toolSummary = "Tool execution did not produce a result.";
                            try {
                                Tool tool = registry.get(call.name);
                                Tool.ToolResult result;
                                if (tool != null) {
                                    String validationError = ToolArgumentValidator.validate(registry.spec(call.name), call.arguments);
                                    if (validationError == null) {
                                        validationError = validateActiveProjectArgs(call.arguments);
                                    }
                                    if (validationError != null) {
                                        result = Tool.ToolResult.structured(call.name, "INVALID_ARGUMENTS", null,
                                                validationError, null, true);
                                    } else if (tool.riskLevel(call.arguments, context) == Tool.RiskLevel.DESTRUCTIVE) {
                                        boolean approved;
                                        waitingForApproval = true;
                                        try {
                                            approved = context.requestConfirmation(buildApprovalPrompt(call, tool, context));
                                        } finally {
                                            waitingForApproval = false;
                                        }
                                        if (!approved || isCancelled) {
                                            result = Tool.ToolResult.structured(call.name, "DENIED", null,
                                                    "The user denied or did not complete the required approval. No tool operation was started.",
                                                    null, true);
                                        } else {
                                            String projectChanged = validateActiveProjectArgs(call.arguments);
                                            if (projectChanged != null) {
                                                result = Tool.ToolResult.structured(call.name, "PROJECT_CHANGED", null,
                                                        projectChanged + " The approved operation was not started.", null, true);
                                            } else {
                                                context.beginApprovedMutation();
                                                try {
                                                    result = tool.run(call.arguments, context);
                                                } finally {
                                                    context.endApprovedMutation();
                                                }
                                            }
                                        }
                                    } else {
                                        result = tool.run(call.arguments, context);
                                    }
                                    conversation.add(AIMessage.toolResult(call.id, result.content));
                                    success = !result.error;
                                    toolSummary = summarizeToolResult(call.name, result.content, success);
                                } else {
                                    result = new Tool.ToolResult("Tool not found: " + call.name, true);
                                    conversation.add(AIMessage.toolResult(call.id, result.content));
                                    toolSummary = "ERROR: No executor is registered for this tool.";
                                }
                            } catch (Exception e) {
                                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                                if (reason.length() > 240) reason = reason.substring(0, 240) + "…";
                                toolSummary = "ERROR: " + reason;
                                conversation.add(AIMessage.toolResult(call.id, "Error executing tool: " + reason));
                            } finally {
                                if (!success) {
                                    if (!taskToolFailures.contains(call.name)) taskToolFailures.add(call.name);
                                }
                                final boolean ok = success;
                                final String summary = toolSummary;
                                callbacks.onToolResult(call.name, summary, ok);
                                callbacks.onToolEnd(call.name, ok);
                            }
                        }
                    } catch (Exception e) {
                        state = SessionState.IDLE;
                        terminalCallbackSent = true;
                        if (isCancelled) {
                            UiPoster.post(callbacks::onCancel);
                        } else {
                            UiPoster.post(() -> callbacks.onError(e));
                        }
                        return;
                    }
                }

                state = SessionState.IDLE;
                if (isCancelled) {
                    terminalCallbackSent = true;
                    UiPoster.post(callbacks::onCancel);
                    return;
                }

                if (lastResponse == null) {
                    terminalCallbackSent = true;
                    UiPoster.post(() -> callbacks.onError(
                            new java.util.concurrent.TimeoutException("Agent task ended without a model response; no completion is claimed.")));
                    return;
                }
                if (!lastResponse.toolCalls.isEmpty()) {
                    terminalCallbackSent = true;
                    UiPoster.post(() -> callbacks.onError(new IllegalStateException(
                            "Agent reached its step or time budget while tool calls were still pending. Earlier actions may have run; inspect their results before retrying. No full completion is claimed.")));
                    return;
                }

                if (lastResponse != null) {
                    AIResponse finalResp = lastResponse;
                    if (!taskToolFailures.isEmpty()) {
                        String report = "\n\n⚠️ Execution history warning: these tools failed at least once during this task ("
                                + String.join(", ", new java.util.LinkedHashSet<>(taskToolFailures))
                                + "). A later attempt may have repaired some failures, but verify the final file state and all partial effects before treating the task as fully completed.";
                        finalResp = new AIResponse(lastResponse.text + report, lastResponse.toolCalls,
                                lastResponse.finishReason, lastResponse.promptTokens, lastResponse.completionTokens);
                    }
                    final AIResponse deliver = finalResp;
                    terminalCallbackSent = true;
                    UiPoster.post(() -> callbacks.onComplete(deliver));
                }
            } finally {
                state = SessionState.IDLE;
                workerThread = null;
                waitingForApproval = false;
                runActive.set(false);
                projectRun.set(false);
                if (!terminalCallbackSent && !isCancelled && lastResponse == null) {
                    UiPoster.post(() -> callbacks.onError(
                            new IllegalStateException("Agent stopped without a verified terminal result.")));
                }
            }
        });
        } catch (RuntimeException e) {
            state = SessionState.IDLE;
            workerThread = null;
            waitingForApproval = false;
            runActive.set(false);
            projectRun.set(false);
            UiPoster.post(() -> callbacks.onError(new IllegalStateException(
                    "Agent task could not be scheduled: " + e.getClass().getSimpleName(), e)));
        }
    }

    /**
     * Produces a small UI-safe summary from the executor result. Never display raw read-file
     * contents or arbitrary structured payloads in the execution card.
     */
    private static String summarizeToolResult(String toolName, String content, boolean success) {
        String prefix = success ? "EXECUTOR OK" : "EXECUTOR ERROR";
        if (content == null || content.trim().isEmpty()) return prefix + ": executor returned no result details.";
        String name = toolName == null ? "" : toolName.toLowerCase(java.util.Locale.ROOT);
        if (name.contains("read") || name.contains("search") || name.contains("list") || name.contains("info")) {
            try {
                JSONObject json = new JSONObject(content);
                String status = json.optString("status", success ? "SUCCESS" : "ERROR");
                String path = json.optString("absolutePath", json.optString("path", json.optString("root", "")));
                String message = json.optString("message", "");
                String bytes = json.has("bytes") ? "bytes=" + json.optLong("bytes") : "";
                String truncated = json.optBoolean("truncated", false) ? "results truncated/partial" : "";
                StringBuilder summary = new StringBuilder(prefix).append(": ").append(status);
                if (!path.isEmpty()) summary.append(" • ").append(path);
                if (!message.isEmpty()) summary.append(" • ").append(message);
                if (!bytes.isEmpty()) summary.append(" • ").append(bytes);
                if (!truncated.isEmpty()) summary.append(" • ").append(truncated);
                return truncateSummary(summary.toString());
            } catch (Exception ignored) {
                return prefix + ": bounded read/search output returned to the model; content intentionally omitted.";
            }
        }
        try {
            JSONObject json = new JSONObject(content);
            String status = json.optString("status", success ? "SUCCESS" : "ERROR");
            String path = json.optString("absolutePath", json.optString("path", ""));
            String message = json.optString("message", "");
            StringBuilder summary = new StringBuilder(prefix).append(": ").append(status);
            if (!path.isEmpty()) summary.append(" • ").append(path);
            if (!message.isEmpty()) summary.append(" • ").append(message);
            if (json.has("candidates")) summary.append(" • candidates=").append(json.optJSONArray("candidates").length());
            return truncateSummary(summary.toString());
        } catch (Exception ignored) {
            String singleLine = content.replaceAll("[\\p{Cntrl}]+", " ").trim();
            return truncateSummary(prefix + ": " + singleLine);
        }
    }

    private static String truncateSummary(String summary) {
        if (summary == null) return "";
        return summary.length() > 260 ? summary.substring(0, 257) + "…" : summary;
    }

    private String validateActiveProjectArgs(org.json.JSONObject args) {
        if (args != null && args.has("sc_id") && !args.isNull("sc_id")) {
            String requested = args.optString("sc_id", "");
            if (context.sc_id == null || !context.sc_id.equals(requested)) {
                return "The requested project ID does not match the project bound to this task. No operation was executed.";
            }
        }
        if (context.sc_id == null || context.sc_id.trim().isEmpty()) {
            return "No active Sketchware project is bound to this agent task.";
        }
        com.besome.sketch.design.DesignActivity activity = context.activity.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return "The project editor that started this task is no longer active. No new operation was started.";
        }
        String currentlyOpenProject = com.besome.sketch.design.DesignActivity.sc_id;
        if (currentlyOpenProject == null || !context.sc_id.equals(currentlyOpenProject)) {
            return "The active project changed after this task started. This task remains bound to its original project and will not continue writing.";
        }
        return null;
    }

    private static String buildApprovalPrompt(ToolCall call, Tool tool, Tool.ToolContext context) {
        StringBuilder message = new StringBuilder();
        message.append("TAJ Agent requests approval for a potentially destructive operation.\n")
                .append("Tool: ").append(sanitizeForDialog(call.name, 80)).append('\n');
        String summary = tool.approvalSummary(call.arguments, context);
        if (summary != null && !summary.trim().isEmpty()) {
            message.append("Scope: ").append(sanitizeForDialog(summary, 240)).append('\n');
        }
        org.json.JSONObject args = call.arguments;
        String[] relevant = {"path", "src", "dst", "folder", "name", "fromFolder",
                "fromName", "toFolder", "toName", "op", "id"};
        if (args != null) {
            for (String key : relevant) {
                if (args.has(key) && !args.isNull(key)) {
                    message.append(key).append(": ")
                            .append(sanitizeForDialog(args.optString(key, String.valueOf(args.opt(key))), 160))
                            .append('\n');
                }
            }
        }
        message.append("No changes will be attempted unless you approve this specific call.");
        return message.toString();
    }

    private static String sanitizeForDialog(String value, int maxLength) {
        if (value == null) return "";
        String sanitized = value.replaceAll("[\\\\p{Cntrl}]", " ").trim();
        if (sanitized.length() > maxLength) sanitized = sanitized.substring(0, maxLength) + "…";
        return sanitized;
    }

    private AIResponse fetchBlocking(AIRequest request, StreamCallbacks outerCallbacks, long taskDeadline) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AIResponse> responseRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        activeHandle = provider.stream(request, new StreamCallbacks() {
            @Override
            public void onReasoningToken(String token) {
                if (!isCancelled) outerCallbacks.onReasoningToken(token);
            }

            @Override
            public void onToken(String token) {
                if (!isCancelled) outerCallbacks.onToken(token);
            }

            @Override
            public void onToolCall(ToolCall call) {
                if (!isCancelled) outerCallbacks.onToolCall(call);
            }

            @Override
            public void onRateLimitWait(long waitMs, int attempt, int maxAttempts) {
                if (!isCancelled) outerCallbacks.onRateLimitWait(waitMs, attempt, maxAttempts);
            }

            @Override
            public void onComplete(AIResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                errorRef.set(new Exception(error));
                latch.countDown();
            }
        });

        try {
            while (latch.getCount() > 0) {
                if (isCancelled) {
                    throw new java.util.concurrent.CancellationException("Agent task cancelled.");
                }
                long remaining = taskDeadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw new java.util.concurrent.TimeoutException("Agent task exceeded its five-minute execution budget.");
                }
                latch.await(Math.min(250L, remaining), TimeUnit.MILLISECONDS);
            }
        } finally {
            activeHandle = null;
        }

        if (errorRef.get() != null) {
            throw errorRef.get();
        }

        return responseRef.get();
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
