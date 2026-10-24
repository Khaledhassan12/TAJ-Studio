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
import pro.sketchware.ai.agent.tools.DomainToolHelper;
import pro.sketchware.ai.core.AIMessage;
import pro.sketchware.ai.core.AIProvider;
import pro.sketchware.ai.core.AIRequest;
import pro.sketchware.ai.core.AIResponse;
import pro.sketchware.ai.core.StreamCallbacks;
import pro.sketchware.ai.core.ToolCall;
import pro.sketchware.ai.core.ToolSpec;

public final class AgentOrchestrator {

    /** Shared hard deadline; UI watchdogs use the same value as the executor. */
    public static final long MAX_TASK_DURATION_MS = TimeUnit.MINUTES.toMillis(8);
    private static final int MAX_MODEL_ITERATIONS = 20;

    /** Stable identity and operating policy shared by every agent request/provider. */
    public static final String DEFAULT_SYSTEM_PROMPT = AgentSystemPrompt.DEFAULT;

    private final AIProvider provider;
    private final ToolRegistry registry;
    private final Tool.ToolContext context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile AtomicBoolean activeCancellation = new AtomicBoolean(false);
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
        activeCancellation.set(true);
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
        final AtomicBoolean taskCancellation = new AtomicBoolean(false);
        activeCancellation = taskCancellation;
        context.setCancellationCheck(taskCancellation::get);
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
                final int MAX_ITERATIONS = MAX_MODEL_ITERATIONS;
                final long taskDeadline = System.currentTimeMillis() + MAX_TASK_DURATION_MS;

                while (iterations < MAX_ITERATIONS && !taskCancellation.get()
                        && System.currentTimeMillis() < taskDeadline) {
                    iterations++;

                    List<pro.sketchware.ai.core.ToolSpec> toolSpecs = new ArrayList<>();
                    // Re-route on every model turn using the accumulated user task context.
                    // The registry still validates and executes calls against the full catalog.
                    for (Tool t : ToolRouter.select(registry, conversation)) {
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
                        AIResponse response = fetchBlocking(request, callbacks, taskDeadline, taskCancellation);
                        lastResponse = response;

                        if (taskCancellation.get()) break;

                        if (response.toolCalls.isEmpty()) {
                            break;
                        } else {
                            conversation.add(AIMessage.assistantWithToolCalls(response.text, response.toolCalls));
                        }

                        state = SessionState.TOOL_RUNNING;
                        for (ToolCall call : response.toolCalls) {
                            if (taskCancellation.get()) break;

                            // StreamCallbacks is a background-thread contract; UI consumers post once themselves.
                            callbacks.onToolStart(call.name);

                            boolean success = false;
                            String toolSummary = "Tool execution did not produce a result.";
                            try {
                                Tool tool = registry.get(call.name);
                                Tool.ToolResult result;
                                if (tool != null) {
                                    String validationError = call.malformedArguments
                                            ? "The model/provider returned malformed tool arguments; the operation was not executed."
                                            : ToolArgumentValidator.validate(registry.spec(call.name), call.arguments);
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
                                            approved = context.requestConfirmation(buildApprovalPrompt(call, tool, context),
                                    () -> taskCancellation.get());
                                        } finally {
                                            waitingForApproval = false;
                                        }
                                        if (!approved || taskCancellation.get()) {
                                            result = Tool.ToolResult.structured(call.name, "DENIED", null,
                                                    "The user denied or did not complete the required approval. No tool operation was started.",
                                                    null, true);
                                        } else {
                                            String projectChanged = validateActiveProjectArgs(call.arguments);
                                            if (projectChanged != null) {
                                                result = Tool.ToolResult.structured(call.name, "PROJECT_CHANGED", null,
                                                        projectChanged + " The approved operation was not started.", null, true);
                                            } else if (taskCancellation.get()) {
                                                result = Tool.ToolResult.structured(call.name, "CANCELLED", null,
                                                        "The task was cancelled before the approved operation started. No tool operation was executed.", null, true);
                                            } else {
                                                context.beginApprovedMutation();
                                                try {
                                                    result = taskCancellation.get()
                                                            ? Tool.ToolResult.structured(call.name, "CANCELLED", null,
                                                                    "The task was cancelled before the operation started.", null, true)
                                                            : tool.run(call.arguments, context);
                                                } finally {
                                                    context.endApprovedMutation();
                                                }
                                            }
                                        }
                                    } else if (taskCancellation.get()) {
                                        result = Tool.ToolResult.structured(call.name, "CANCELLED", null,
                                                "The task was cancelled before the operation started. No tool operation was executed.", null, true);
                                    } else {
                                        // Recheck cancellation at the execution boundary so a
                                        // cancellation received after validation does not start a new tool.
                                        result = taskCancellation.get()
                                                ? Tool.ToolResult.structured(call.name, "CANCELLED", null,
                                                        "The task was cancelled before the operation started.", null, true)
                                                : tool.run(call.arguments, context);
                                    }
                                    // Reconcile filesystem operations that can add, move, or remove layout XML.
                                    // The specialized layout writers already perform checked model sync; this
                                    // per-tool safety net covers generic copy/move/delete paths before the model
                                    // receives the next tool result and before another action begins.
                                    if (!result.error && touchesLayoutXml(call.name, call.arguments, context)) {
                                        DomainToolHelper.refreshEditor(context);
                                    }
                                    conversation.add(AIMessage.toolResult(call.id, boundToolResult(result.content)));
                                    success = !result.error;
                                    toolSummary = summarizeToolResult(call.name, result.content, success);
                                } else {
                                    result = new Tool.ToolResult("Tool not found: " + call.name, true);
                                    conversation.add(AIMessage.toolResult(call.id, boundToolResult(result.content)));
                                    toolSummary = "ERROR: No executor is registered for this tool.";
                                }
                            } catch (Exception e) {
                                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                                if (reason.length() > 240) reason = reason.substring(0, 240) + "…";
                                toolSummary = "ERROR: " + reason;
                                conversation.add(AIMessage.toolResult(call.id, boundToolResult("Error executing tool: " + reason)));
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
                        if (taskCancellation.get()) {
                            UiPoster.post(callbacks::onCancel);
                        } else {
                            Throwable reportError = e;
                            if (!taskToolFailures.isEmpty()) {
                                reportError = new IllegalStateException(
                                        "The agent task failed after one or more tool failures (" 
                                                + String.join(", ", new java.util.LinkedHashSet<>(taskToolFailures))
                                                + "). Earlier operations may have changed project files; inspect the actual project state before retrying.",
                                        e);
                            }
                            Throwable deliverError = reportError;
                            UiPoster.post(() -> callbacks.onError(deliverError));
                        }
                        return;
                    }
                }

                state = SessionState.IDLE;
                if (taskCancellation.get()) {
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
                    // Final editor rebind is the task-level safety net: successful resource tools
                    // synchronize their domain models, and the editor refresh runs after all tool
                    // calls have completed so the user sees the final project state, not an interim one.
                    DomainToolHelper.refreshEditor(context);
                    UiPoster.post(() -> callbacks.onComplete(deliver));
                }
            } finally {
                state = SessionState.IDLE;
                workerThread = null;
                waitingForApproval = false;
                context.setCancellationCheck(null);
                runActive.set(false);
                projectRun.set(false);
                if (!terminalCallbackSent && !taskCancellation.get() && lastResponse == null) {
                    UiPoster.post(() -> callbacks.onError(
                            new IllegalStateException("Agent stopped without a verified terminal result.")));
                }
            }
        });
        } catch (RuntimeException e) {
            state = SessionState.IDLE;
            workerThread = null;
            waitingForApproval = false;
            context.setCancellationCheck(null);
            runActive.set(false);
            projectRun.set(false);
            UiPoster.post(() -> callbacks.onError(new IllegalStateException(
                    "Agent task could not be scheduled: " + e.getClass().getSimpleName(), e)));
        }
    }


    /**
     * Detect filesystem operations that may change layout XML without going through
     * the specialized layout tools. Paths are resolved through the active project's
     * resolver; user-supplied absolute paths are never trusted as layout evidence.
     */
    private static boolean touchesLayoutXml(String toolName, JSONObject args, Tool.ToolContext ctx) {
        if (toolName == null || args == null || ctx == null || ctx.sc_id == null) return false;
        try {
            String layoutRoot = new java.io.File(pro.sketchware.ai.agent.ProjectPaths.layoutDir(ctx.sc_id))
                    .getCanonicalPath();
            java.util.List<String> candidates = new java.util.ArrayList<>();
            if ("res_delete".equals(toolName)) {
                if ("layout".equals(args.optString("folder", ""))) {
                    candidates.add("res/" + args.optString("folder") + "/" + args.optString("name"));
                }
            } else if ("res_move_copy".equals(toolName)) {
                if ("layout".equals(args.optString("fromFolder", ""))) {
                    candidates.add("res/layout/" + args.optString("fromName"));
                }
                if ("layout".equals(args.optString("toFolder", ""))) {
                    candidates.add("res/layout/" + args.optString("toName"));
                }
            } else if ("fs_delete".equals(toolName)) {
                candidates.add(args.optString("path", ""));
            } else if ("fs_copy".equals(toolName) || "fs_move".equals(toolName)) {
                candidates.add(args.optString("src", ""));
                candidates.add(args.optString("dst", ""));
            } else {
                return false;
            }
            for (String candidate : candidates) {
                if (candidate == null || candidate.trim().isEmpty()) continue;
                java.io.File resolved = pro.sketchware.ai.agent.PathResolver.resolve(ctx.sc_id, candidate);
                String canonical = resolved.getCanonicalPath();
                if (resolved.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".xml")
                        && canonical.startsWith(layoutRoot + java.io.File.separator)) return true;
            }
        } catch (Exception ignored) {
            // The executor remains responsible for reporting path errors. A failed
            // detection must never broaden filesystem access or change the target project.
        }
        return false;
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
                try {
                    org.json.JSONArray array = new org.json.JSONArray(content);
                    return truncateSummary(prefix + ": " + array.length() + " item(s) returned; details remain available to the model.");
                } catch (Exception notArray) {
                    String safeText = content.replaceAll("[\\p{Cntrl}]+", " ").trim();
                    if (!success) return truncateSummary("EXECUTOR ERROR: " + safeText);
                    return truncateSummary("EXECUTOR OK: text result returned (" + content.length() + " chars); full content intentionally kept out of the compact card.");
                }
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

    /**
     * Protects mobile memory without imposing the former 16 KiB cap on every tool.
     * This ceiling is intentionally multi-megabyte for long-context providers; it is
     * independent from the provider's token window and the overall HTTP payload ceiling.
     */
    private static final int MAX_TOOL_RESULT_CHARS = 8 * 1024 * 1024;
    private static final int TOOL_RESULT_PREVIEW_CHARS = 6 * 1024 * 1024;

    private static String boundToolResult(String content) {
        if (content == null) return "";
        if (content.length() <= MAX_TOOL_RESULT_CHARS) return content;
        org.json.JSONObject bounded = new org.json.JSONObject();
        try {
            bounded.put("status", "TRUNCATED");
            bounded.put("message", "Tool output exceeded TAJ AI's 8 Mi-character per-result memory safety ceiling. This preview is incomplete; request a narrower path, range, or search.");
            bounded.put("originalChars", content.length());
            bounded.put("preview", content.substring(0, TOOL_RESULT_PREVIEW_CHARS));
            bounded.put("truncated", true);
            return bounded.toString();
        } catch (org.json.JSONException impossible) {
            return "{\"status\":\"TRUNCATED\",\"truncated\":true}";
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

    private AIResponse fetchBlocking(AIRequest request, StreamCallbacks outerCallbacks, long taskDeadline,
            AtomicBoolean taskCancellation) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AIResponse> responseRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        AIProvider.Handle requestHandle = provider.stream(request, new StreamCallbacks() {
            @Override
            public void onReasoningToken(String token) {
                if (!taskCancellation.get()) outerCallbacks.onReasoningToken(token);
            }

            @Override
            public void onToken(String token) {
                if (!taskCancellation.get()) outerCallbacks.onToken(token);
            }

            @Override
            public void onToolCall(ToolCall call) {
                if (!taskCancellation.get()) outerCallbacks.onToolCall(call);
            }

            @Override
            public void onRateLimitWait(long waitMs, int attempt, int maxAttempts) {
                if (!taskCancellation.get()) outerCallbacks.onRateLimitWait(waitMs, attempt, maxAttempts);
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
        activeHandle = requestHandle;

        try {
            while (latch.getCount() > 0) {
                if (taskCancellation.get()) {
                    throw new java.util.concurrent.CancellationException("Agent task cancelled.");
                }
                long remaining = taskDeadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw new java.util.concurrent.TimeoutException("Agent task exceeded its eight-minute execution budget.");
                }
                latch.await(Math.min(250L, remaining), TimeUnit.MILLISECONDS);
            }
        } finally {
            // A deadline/cancellation can exit before the provider emits a terminal
            // callback. Always cancel that underlying HTTP call to avoid orphan streams.
            if (latch.getCount() > 0 && requestHandle != null) {
                requestHandle.cancel();
            }
            if (activeHandle == requestHandle) {
                activeHandle = null;
            }
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
