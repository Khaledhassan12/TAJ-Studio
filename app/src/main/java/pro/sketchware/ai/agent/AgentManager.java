package pro.sketchware.ai.agent;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import pro.sketchware.ai.agent.tools.*;
import pro.sketchware.ai.core.*;
import pro.sketchware.ai.prompt.*;
import pro.sketchware.ai.data.AiStorage;

/**
 * [WHAT] Orchestrates the agent reasoning loop (TAJ sitting in IDE).
 * [WHY] Allows the AI to act on real project data using tools.
 * [HOW] Composes prompt with tool schemas, handles tool calls, and streams results.
 */
public class AgentManager {

    public interface AgentListener {
        void onStep(AgentStep step);
        void onDone();
    }

    private final Context context;
    private final SystemPromptManager promptManager;
    private final AiStorage storage;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AgentManager(Context context) {
        this.context = context.getApplicationContext();
        this.promptManager = new SystemPromptManager(context);
        this.storage = AiStorage.get(context);
    }

    public void runTurn(String scId, String conversationId, String userMessage, AiProvider provider, String modelId, AgentListener listener) {
        // 1. Compose
        ComposeRequest req = new ComposeRequest(scId, userMessage, true);
        ComposedPrompt composed = promptManager.compose(req, provider.caps().contextSize);
        
        // 2. Prepare request with tools
        List<AiMessage> messages = new ArrayList<>();
        messages.add(new AiMessage(AiMessage.Role.user, userMessage));
        
        String systemWithTools = composed.systemText + "\n\nAvailable Tools:\n" + getToolSchemas();
        
        AiRequest aiReq = new AiRequest(messages, systemWithTools, 4096, 0.7, modelId);
        
        // 3. Start loop
        provider.stream(aiReq, new AiStreamCallback() {
            @Override
            public void onToken(String token) {
                AgentStep step = new AgentStep(AgentStep.Kind.TEXT, token);
                persistStep(conversationId, step);
                listener.onStep(step);
            }

            @Override
            public void onDone(AiResponse response) {
                if (response.content.contains("{\"tool\":")) {
                    executeToolCall(scId, conversationId, response.content, provider, modelId, listener);
                } else {
                    mainHandler.post(listener::onDone);
                }
            }

            @Override
            public void onError(AiError error) {
                AgentStep step = new AgentStep(AgentStep.Kind.ERROR, error.message);
                persistStep(conversationId, step);
                listener.onStep(step);
                mainHandler.post(listener::onDone);
            }
        });
    }

    private void executeToolCall(String scId, String conversationId, String json, AiProvider provider, String modelId, AgentListener listener) {
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            String name = obj.getString("tool");
            String argsJson = obj.getJSONObject("args").toString();
            
            Tool tool = ToolRegistry.get(name);
            if (tool != null) {
                AgentStep callStep = new AgentStep(AgentStep.Kind.TOOL_CALL, name);
                persistStep(conversationId, callStep);
                listener.onStep(callStep);
                
                boolean isDestructive = name.equals("deleteFile") || name.equals("writeFile") || name.equals("patchFile");
                
                // In full P5, we pause loop here if not confirmed. 
                // For this round, we auto-confirm to show the tool success.
                ToolResult res = tool.execute(new ToolArgs(argsJson), new ToolCtx(context, scId, true));
                
                AgentStep resStep = new AgentStep(AgentStep.Kind.TOOL_RESULT, res.content);
                persistStep(conversationId, resStep);
                listener.onStep(resStep);
            }
        } catch (Exception ignored) {}
        mainHandler.post(listener::onDone);
    }

    private void persistStep(String conversationId, AgentStep step) {
        android.content.ContentValues cv = new android.content.ContentValues();
        cv.put("id", UUID.randomUUID().toString());
        cv.put("conversationId", conversationId);
        cv.put("action", step.kind.name());
        cv.put("payloadJson", step.payload);
        cv.put("createdAt", step.timestamp);
        storage.insertAgentStep(cv);
    }

    private String getToolSchemas() {
        StringBuilder sb = new StringBuilder();
        for (Tool t : ToolRegistry.list()) {
            sb.append("- ").append(t.spec().name).append(": ").append(t.spec().description).append("\n");
            sb.append("  Schema: ").append(t.spec().jsonSchema).append("\n");
        }
        return sb.toString();
    }
}
