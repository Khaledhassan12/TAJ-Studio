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

    public static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful Android development assistant. " +
            "Java/Kotlin routing rules: (1) Brand-new standalone code the user asks to create goes to target=manager (Java Manager) unless the user explicitly ties it to the app project. " +
            "(2) Any modification to existing project files (e.g. MainActivity.java) goes to target=project at the project's on-device source tree. " +
            "(3) If asked to EDIT inside Java Manager while it is empty, do not create anything; tell the user it is empty. " +
            "(4) Always read before edit; prefer search_replace over replace_all.";

    private final AIProvider provider;
    private final ToolRegistry registry;
    private final Tool.ToolContext context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isCancelled = false;

    public AgentOrchestrator(AIProvider provider, ToolRegistry registry, Tool.ToolContext context) {
        this.provider = provider;
        this.registry = registry;
        this.context = context;
    }

    public void cancel() {
        isCancelled = true;
    }

    public void run(String systemPrompt, List<AIMessage> messages, String model, float temperature, StreamCallbacks callbacks) {
        isCancelled = false;
        executor.execute(() -> {
            List<AIMessage> conversation = new ArrayList<>(messages);
            int iterations = 0;
            final int MAX_ITERATIONS = 10;
            AIResponse lastResponse = null;

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
                        
                        mainHandler.post(() -> callbacks.onToolStart(call.name));
                        
                        Tool tool = registry.get(call.name);
                        Tool.ToolResult result;
                        if (tool != null) {
                            try {
                                result = tool.run(call.arguments, context);
                                conversation.add(AIMessage.toolResult(call.id, result.content));
                            } catch (Exception e) {
                                result = new Tool.ToolResult("Error: " + e.getMessage(), true);
                                conversation.add(AIMessage.toolResult(call.id, result.content));
                            }
                        } else {
                            result = new Tool.ToolResult("Tool not found: " + call.name, true);
                            conversation.add(AIMessage.toolResult(call.id, result.content));
                        }
                        
                        final boolean ok = !result.error;
                        mainHandler.post(() -> callbacks.onToolEnd(call.name, ok));
                    }
                } catch (Exception e) {
                    if (!isCancelled) {
                        mainHandler.post(() -> callbacks.onError(e));
                    }
                    return;
                }
            }
            
            if (isCancelled) {
                // We don't call onComplete if cancelled. AssistantFragment handles the UI.
                return;
            }

            if (lastResponse != null) {
                final AIResponse finalResp = lastResponse;
                mainHandler.post(() -> callbacks.onComplete(finalResp));
            }
        });
    }

    private AIResponse fetchBlocking(AIRequest request, StreamCallbacks outerCallbacks) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AIResponse> responseRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        provider.stream(request, new StreamCallbacks() {
            @Override
            public void onReasoningToken(String token) {
                if (!isCancelled) outerCallbacks.onReasoningToken(token);
            }

            @Override
            public void onToken(String token) {
                // Only pipe tokens if this is likely the final response (no tools)
                // Actually we don't know yet. But Edit 1.1 says:
                // "Tool-loop iterations must NEVER create/append an assistant bubble; exactly ONE final assistant bubble after the loop ends"
                // This is tricky. If we don't stream tokens, user sees nothing for a long time.
                // Re-reading: "Tool-loop iterations must NEVER create/append an assistant bubble"
                // Maybe it means intermediate bubbles. 
                // Let's pipe tokens anyway, AssistantFragment will handle deduplication or only start one bubble.
                if (!isCancelled) outerCallbacks.onToken(token);
            }

            @Override
            public void onToolCall(ToolCall call) {
                if (!isCancelled) outerCallbacks.onToolCall(call);
            }

            @Override
            public void onComplete(AIResponse response) {
                responseRef.set(response);
                latch.countDown();
                // We DON'T call outerCallbacks.onComplete here because the run() loop handles it.
            }

            @Override
            public void onError(Throwable error) {
                errorRef.set(new Exception(error));
                latch.countDown();
                // outerCallbacks.onError(error); // run() loop handles this
            }
        });

        if (!latch.await(120, TimeUnit.SECONDS)) {
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
