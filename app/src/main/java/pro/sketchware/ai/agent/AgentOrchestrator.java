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

import pro.sketchware.ai.live.UiPoster;
import pro.sketchware.ai.core.AIMessage;
import pro.sketchware.ai.core.AIProvider;
import pro.sketchware.ai.core.AIRequest;
import pro.sketchware.ai.core.AIResponse;
import pro.sketchware.ai.core.StreamCallbacks;
import pro.sketchware.ai.core.ToolCall;
import pro.sketchware.ai.core.ToolSpec;

public final class AgentOrchestrator {

    public static final String DEFAULT_SYSTEM_PROMPT = "You are operating on the project currently open in TAJ Studio. " +
            "Use the supplied tools to inspect and change that project; do not merely describe edits when a tool can perform them. " +
            "Never supply a different sc_id: the runtime binds every project operation to the currently open project. " +
            "Use relative paths such as 'res/layout/main.xml', 'values/colors.xml', 'assets/logo.png', and 'java/...'. " +
            "Tools auto-resolve supported aliases; if a tool returns ERROR with candidates, retry using an exact candidate. " +
            "For adding widgets to layouts prefer insert_widget with parentId. " +
            "For every requested change: inspect the target, execute the appropriate tool, read back or otherwise verify the persisted result, and report the actual result. " +
            "The block_create and block_edit tools update the shared custom-block manager catalog, not project-private files; disclose that scope. " +
            "Never claim an operation succeeded unless the tool returned a verified success. Explain permission denials and failed verification plainly.";

    private final AIProvider provider;
    private final ToolRegistry registry;
    private final Tool.ToolContext context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean isCancelled = false;
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
        state = SessionState.IDLE;
        if (activeHandle != null) activeHandle.cancel();
    }

    public void skipWait() {
        if (activeHandle != null) activeHandle.skipWait();
    }

    public void run(String systemPrompt, List<AIMessage> messages, String model, float temperature, StreamCallbacks callbacks) {
        isCancelled = false;
        state = SessionState.MODEL_STREAMING;
        executor.execute(() -> {
            AIResponse lastResponse = null;
            boolean terminalCallbackSent = false;
            try {
                List<AIMessage> conversation = new ArrayList<>(messages);
                int iterations = 0;
                final int MAX_ITERATIONS = 10;

                while (iterations < MAX_ITERATIONS && !isCancelled) {
                    iterations++;

                    List<pro.sketchware.ai.core.ToolSpec> toolSpecs = new ArrayList<>();
                    for (Tool.ToolSpec spec : registry.specs()) {
                        toolSpecs.add(new pro.sketchware.ai.core.ToolSpec(spec.name, spec.description, spec.parameters));
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
                        AIResponse response = fetchBlocking(request, callbacks);
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

                            UiPoster.post(() -> callbacks.onToolStart(call.name));

                            boolean success = false;
                            try {
                                if (!call.argumentsValid) {
                                    conversation.add(AIMessage.toolResult(call.id,
                                            "ERROR: the provider returned malformed JSON for this tool's arguments. No operation was executed. Request a corrected tool call."));
                                } else {
                                    Tool tool = registry.get(call.name);
                                    Tool.ToolResult result;
                                    if (tool != null) {
                                    boolean destructive = requiresManualConfirmation(call.name);
                                    if (destructive && !context.confirmDestructiveOperation(call.name, call.arguments)) {
                                        result = new Tool.ToolResult(
                                                "Operation not executed: destructive operations are disabled or confirmation was denied/dismissed.",
                                                true);
                                    } else {
                                        if (destructive) context.beginConfirmedOperation();
                                        try {
                                            result = tool.run(call.arguments, context);
                                        } finally {
                                            if (destructive) context.endConfirmedOperation();
                                        }
                                    }
                                    conversation.add(AIMessage.toolResult(call.id, result.content));
                                    success = !result.error;
                                    } else {
                                        result = new Tool.ToolResult("Tool not found: " + call.name, true);
                                        conversation.add(AIMessage.toolResult(call.id, result.content));
                                    }
                                }
                            } catch (Exception e) {
                                conversation.add(AIMessage.toolResult(call.id, "Error executing tool: " + e.getMessage()));
                            } finally {
                                final boolean ok = success;
                                UiPoster.post(() -> callbacks.onToolEnd(call.name, ok));
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
                if (iterations >= MAX_ITERATIONS && lastResponse != null && !lastResponse.toolCalls.isEmpty()) {
                    terminalCallbackSent = true;
                    UiPoster.post(() -> callbacks.onError(
                            new Exception("Agent reached the 10-round tool limit before producing a final answer. Review the tool results and send a follow-up request.")));
                    return;
                }

                if (lastResponse != null) {
                    final AIResponse finalResp = lastResponse;
                    terminalCallbackSent = true;
                    UiPoster.post(() -> callbacks.onComplete(finalResp));
                }
            } finally {
                state = SessionState.IDLE;
                if (!terminalCallbackSent && !isCancelled && lastResponse == null) {
                    UiPoster.post(callbacks::onCancel);
                }
            }
        });
    }

    /**
     * Deletion and relocation can irreversibly lose user project data. Until the
     * UI has a real per-call approval handshake, fail closed for these operations.
     */
    private static boolean requiresManualConfirmation(String toolName) {
        if (toolName == null) return false;
        String name = toolName.toLowerCase(java.util.Locale.ROOT);
        return name.contains("delete")
                || name.contains("remove")
                || name.contains("move")
                || name.contains("copy")
                || "block_create".equals(name)
                || "block_edit".equals(name);
    }

    private AIResponse fetchBlocking(AIRequest request, StreamCallbacks outerCallbacks) throws Exception {
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
            if (!latch.await(120, TimeUnit.SECONDS)) {
                throw new Exception("AI Request timed out");
            }
        } finally {
            activeHandle = null;
        }

        if (errorRef.get() != null) {
            throw errorRef.get();
        }
        AIResponse response = responseRef.get();
        if (response == null) {
            throw new Exception("Provider completed without an AI response payload.");
        }
        return response;
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
