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

import pro.sketchware.ai.ui.UiPoster;
import pro.sketchware.ai.core.AIMessage;
import pro.sketchware.ai.core.AIProvider;
import pro.sketchware.ai.core.AIRequest;
import pro.sketchware.ai.core.AIResponse;
import pro.sketchware.ai.core.StreamCallbacks;
import pro.sketchware.ai.core.ToolCall;
import pro.sketchware.ai.core.ToolSpec;

public final class AgentOrchestrator {

    public static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful Android development assistant. " +
            "Java/Kotlin routing rules: (1) Brand-new standalone code the user asks to create goes to target=manager (Java Manager) unless the user explicitly ties it to the app project. " +
            "(2) Any modification to existing project files (e.g. MainActivity.java) goes to target=project at the project's on-device source tree. " +
            "(3) If asked to EDIT inside Java Manager while it is empty, do not create anything; tell the user it is empty. " +
            "(4) Always read before edit; prefer search_replace over replace_all. " +
            "(5) NEVER claim a change was made unless the tool result contains 'verified'. If a tool returns ERROR, tell the user what failed and propose a fix. " +
            "NEVER claim a change was made unless the tool result contains 'verified'. If ERROR, tell the user what failed.";

    private final AIProvider provider;
    private final ToolRegistry registry;
    private final Tool.ToolContext context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean isCancelled = false;
    private volatile AIProvider.Handle activeHandle;

    public AgentOrchestrator(AIProvider provider, ToolRegistry registry, Tool.ToolContext context) {
        this.provider = provider;
        this.registry = registry;
        this.context = context;
    }

    public void cancel() {
        isCancelled = true;
        if (activeHandle != null) activeHandle.cancel();
    }

    public void skipWait() {
        if (activeHandle != null) activeHandle.skipWait();
    }

    public void run(String systemPrompt, List<AIMessage> messages, String model, float temperature, StreamCallbacks callbacks) {
        isCancelled = false;
        executor.execute(() -> {
            AIResponse lastResponse = null;
            try {
                List<AIMessage> conversation = new ArrayList<>(messages);
                int iterations = 0;
                final int MAX_ITERATIONS = 10;

                while (iterations < MAX_ITERATIONS && !isCancelled) {
                    iterations++;

                    List<pro.sketchware.ai.core.ToolSpec> toolSpecs = new ArrayList<>();
                    for (Tool t : registry.all()) {
                        toolSpecs.add(new pro.sketchware.ai.core.ToolSpec(t.spec().name, t.spec().description, t.spec().parameters));
                    }

                    AIRequest request = new AIRequest.Builder()
                            .model(model)
                            .systemPrompt(systemPrompt)
                            .messages(conversation)
                            .temperature(temperature)
                            .tools(toolSpecs)
                            .build();

                    try {
                        AIResponse response = fetchBlocking(request, callbacks);
                        lastResponse = response;

                        if (isCancelled) break;

                        if (response.toolCalls.isEmpty()) {
                            // Loop terminates when no tools are requested
                            break;
                        } else {
                            conversation.add(AIMessage.assistantWithToolCalls(response.toolCalls));
                        }

                        for (ToolCall call : response.toolCalls) {
                            if (isCancelled) break;

                            UiPoster.post(() -> callbacks.onToolStart(call.name));

                            boolean success = false;
                            try {
                                Tool tool = registry.get(call.name);
                                Tool.ToolResult result;
                                if (tool != null) {
                                    result = tool.run(call.arguments, context);
                                    conversation.add(AIMessage.toolResult(call.id, result.content));
                                    success = !result.error;
                                } else {
                                    result = new Tool.ToolResult("Tool not found: " + call.name, true);
                                    conversation.add(AIMessage.toolResult(call.id, result.content));
                                }
                            } catch (Exception e) {
                                conversation.add(AIMessage.toolResult(call.id, "Error executing tool: " + e.getMessage()));
                            } finally {
                                final boolean ok = success;
                                UiPoster.post(() -> callbacks.onToolEnd(call.name, ok));
                            }
                        }
                    } catch (Exception e) {
                        if (!isCancelled) {
                            UiPoster.post(() -> callbacks.onError(e));
                        }
                        return;
                    }
                }

                if (isCancelled) {
                    UiPoster.post(callbacks::onCancel);
                    return;
                }

                if (lastResponse != null) {
                    final AIResponse finalResp = lastResponse;
                    UiPoster.post(() -> callbacks.onComplete(finalResp));
                }
            } finally {
                if (!isCancelled && lastResponse == null) {
                    UiPoster.post(callbacks::onCancel);
                }
            }
        });
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

        return responseRef.get();
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
