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

import pro.sketchware.ai.core.AIMessage;
import pro.sketchware.ai.core.AIProvider;
import pro.sketchware.ai.core.AIRequest;
import pro.sketchware.ai.core.AIResponse;
import pro.sketchware.ai.core.StreamCallbacks;
import pro.sketchware.ai.core.ToolCall;
import pro.sketchware.ai.core.ToolSpec;

public final class AgentOrchestrator {

    private final AIProvider provider;
    private final ToolRegistry registry;
    private final Tool.ToolContext context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AgentOrchestrator(AIProvider provider, ToolRegistry registry, Tool.ToolContext context) {
        this.provider = provider;
        this.registry = registry;
        this.context = context;
    }

    public void run(String systemPrompt, List<AIMessage> messages, String model, float temperature, StreamCallbacks callbacks) {
        executor.execute(() -> {
            List<AIMessage> conversation = new ArrayList<>(messages);
            int iterations = 0;
            final int MAX_ITERATIONS = 10;

            while (iterations < MAX_ITERATIONS) {
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
                    if (response.toolCalls.isEmpty()) {
                        conversation.add(AIMessage.assistant(response.text));
                        break;
                    } else {
                        conversation.add(AIMessage.assistantWithToolCalls(response.toolCalls));
                    }

                    for (ToolCall call : response.toolCalls) {
                        Tool tool = registry.get(call.name);
                        if (tool != null) {
                            try {
                                Tool.ToolResult result = tool.run(call.arguments, context);
                                conversation.add(AIMessage.toolResult(call.id, result.content));
                            } catch (Exception e) {
                                conversation.add(AIMessage.toolResult(call.id, "Error executing tool: " + e.getMessage()));
                            }
                        } else {
                            conversation.add(AIMessage.toolResult(call.id, "Tool not found: " + call.name));
                        }
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> callbacks.onError(e));
                    return;
                }
            }
        });
    }

    private AIResponse fetchBlocking(AIRequest request, StreamCallbacks outerCallbacks) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AIResponse> responseRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        provider.stream(request, new StreamCallbacks() {
            @Override
            public void onToken(String token) {
                outerCallbacks.onToken(token);
            }

            @Override
            public void onToolCall(ToolCall call) {
                outerCallbacks.onToolCall(call);
            }

            @Override
            public void onComplete(AIResponse response) {
                responseRef.set(response);
                outerCallbacks.onComplete(response);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                errorRef.set(new Exception(error));
                outerCallbacks.onError(error);
                latch.countDown();
            }
        });

        if (!latch.await(60, TimeUnit.SECONDS)) {
            throw new Exception("AI Request timed out");
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
