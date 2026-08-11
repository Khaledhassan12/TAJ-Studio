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
        void onDone(AiResponse usage);
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
        runTurn(scId, conversationId, userMessage, null, provider, modelId, listener);
    }

    public void runTurn(String scId, String conversationId, String userMessage, List<String> imagePaths, AiProvider provider, String modelId, AgentListener listener) {
        // 0. Transcribe images if needed (P2-IT)
        new pro.sketchware.ai.transcription.ImageTranscriptionEngine(context).maybeTranscribe(imagePaths, modelId, new pro.sketchware.ai.transcription.ImageTranscriptionEngine.Callback() {
            @Override
            public void onResult(String transcription) {
                String finalUserMessage = userMessage;
                if (transcription != null && !transcription.isEmpty()) {
                    finalUserMessage = "[Image Transcription]\n" + transcription + "\n\n[User Message]\n" + userMessage;
                }
                continueRunTurn(scId, conversationId, finalUserMessage, imagePaths, provider, modelId, listener);
            }

            @Override
            public void onError(String error) {
                // Log and continue without transcription
                continueRunTurn(scId, conversationId, userMessage, imagePaths, provider, modelId, listener);
            }
        });
    }

    private void continueRunTurn(String scId, String conversationId, String userMessage, List<String> imagePaths, AiProvider provider, String modelId, AgentListener listener) {
        // P2-IG/WS/MCP/AU: re-check conditional tool registration before composing schemas.
        ToolRegistry.syncImageGen(context);
        ToolRegistry.syncWebSearch(context);
        ToolRegistry.syncMcp(context);
        ToolRegistry.syncAutomation(context);

        // 0. Resolve Template (P1-H)
        pro.sketchware.ai.prompts.PromptTemplate template = pro.sketchware.ai.prompts.PromptTemplateStore.get(context).getActiveTemplate();
        pro.sketchware.ai.prompts.PromptVariables.ResolveCtx ctx = new pro.sketchware.ai.prompts.PromptVariables.ResolveCtx(System.currentTimeMillis(), modelId);
        
        String resolvedSystem = pro.sketchware.ai.prompts.PromptVariables.resolveList(template.system, ctx, storage);
        String resolvedPrefix = pro.sketchware.ai.prompts.PromptVariables.resolveList(template.prefix, ctx, storage);
        String resolvedSuffix = pro.sketchware.ai.prompts.PromptVariables.resolveList(template.suffix, ctx, storage);

        // 1. Compose
        ComposeRequest req = new ComposeRequest(scId, userMessage, true);
        req.templateSystemText = resolvedSystem;
        req.userPrefixText = resolvedPrefix;
        req.userSuffixText = resolvedSuffix;
        req.toolSchemas = getToolSchemas(provider);

        ComposedPrompt composed = promptManager.compose(req, provider.caps().contextSize);
        
        // 2. Prepare request
        List<AiMessage> messages = new ArrayList<>();
        // Load history from DB (Trimming per P1-M)
        int ctxWindow = pro.sketchware.ai.generation.GenerationDefaults.get(context).getContextWindow();
        
        try (android.database.Cursor c = storage.listMessages(conversationId)) {
            if (c != null) {
                int startPos = Math.max(0, c.getCount() - ctxWindow);
                if (c.moveToPosition(startPos - 1)) { // Move to just before the desired window
                    while (c.moveToNext()) {
                        String roleStr = c.getString(c.getColumnIndexOrThrow("role"));
                        String content = c.getString(c.getColumnIndexOrThrow("content"));
                        messages.add(new AiMessage(AiMessage.Role.valueOf(roleStr), content));
                    }
                }
            }
        } catch (Exception ignored) {}
        
        // Wrap current message in the list
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiMessage m = messages.get(i);
            if (m.role == AiMessage.Role.user && m.content.equals(userMessage)) {
                m.content = resolvedPrefix + m.content + resolvedSuffix;
                break; 
            }
        }
        
        // P1-D: pull real per-model settings from LocalModelConfig (no hardcoded sampling)
        pro.sketchware.ai.models.LocalModelConfig cfg = null;
        boolean isLocal = "local-llama".equals(provider.id());
        
        try (android.database.Cursor c = storage.findModel(modelId)) {
            if (c.moveToFirst()) {
                String metadata = c.getString(c.getColumnIndexOrThrow("metadataJson"));
                cfg = pro.sketchware.ai.models.LocalModelConfig.fromJson(metadata);
            }
        }
        
        // Resolution order (P1-M): Local model config WINS; Generation defaults fill nulls.
        pro.sketchware.ai.generation.GenerationDefaults gd = pro.sketchware.ai.generation.GenerationDefaults.get(context);
        
        Float temp = null;
        Float topP = null;
        Integer maxTokens = null;
        int contextSize = 2048;
        String mmproj = "";
        
        if (cfg != null) {
            temp = cfg.temperature;
            topP = cfg.topP;
            maxTokens = cfg.maxTokens;
            contextSize = cfg.contextSize;
            mmproj = cfg.mmprojPath;
        } else if (!isLocal) {
            // Cloud: Use defaults from GenerationDefaults if set
            temp = gd.getTemperature();
            topP = gd.getTopP();
            maxTokens = gd.getMaxTokens();
        }
        
        AiRequest aiReq = new AiRequest(messages, composed.systemText, maxTokens, temp, modelId);
        aiReq.topP = topP != null ? topP : 0.9f;
        aiReq.contextSize = contextSize;
        aiReq.mmprojPath = mmproj;
        aiReq.imagePaths = imagePaths;
        
        // 3. Start loop
        provider.stream(aiReq, new AiStreamCallback() {
            @Override
            public void onToken(String token) {
                AgentStep step = new AgentStep(AgentStep.Kind.TEXT, token);
                persistStep(conversationId, step);
                listener.onStep(step);
            }

            @Override
            public void onThought(String thought) {
                AgentStep step = new AgentStep(AgentStep.Kind.THOUGHT, thought);
                persistStep(conversationId, step);
                listener.onStep(step);
            }

            @Override
            public void onDone(AiResponse response) {
                if (response.content.contains("{\"tool\":")) {
                    executeToolCall(scId, conversationId, response, provider, modelId, listener);
                } else {
                    mainHandler.post(() -> listener.onDone(response));
                }
            }

            @Override
            public void onError(AiError error) {
                AgentStep step = new AgentStep(AgentStep.Kind.ERROR, error.message);
                persistStep(conversationId, step);
                listener.onStep(step);
                mainHandler.post(() -> listener.onDone(null));
            }
        });
    }

    private void executeToolCall(String scId, String conversationId, AiResponse response, AiProvider provider, String modelId, AgentListener listener) {
        String json = response.content;
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            String name = obj.getString("tool");
            String argsJson = obj.getJSONObject("args").toString();
            
            Tool tool = ToolRegistry.get(name);
            if (tool != null) {
                AgentStep callStep = new AgentStep(AgentStep.Kind.TOOL_CALL, name);
                persistStep(conversationId, callStep);
                listener.onStep(callStep);
                
                ToolResult res = tool.execute(new ToolArgs(argsJson), new ToolCtx(context, scId, conversationId, true));
                
                AgentStep resStep = new AgentStep(AgentStep.Kind.TOOL_RESULT, res.content);
                persistStep(conversationId, resStep);
                listener.onStep(resStep);
            }
        } catch (Exception ignored) {}
        mainHandler.post(() -> listener.onDone(response));
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

    private String getToolSchemas(AiProvider provider) {
        // P2-MCP (D5/RISK-5): providers without tool-calling omit MCP tools — honestly.
        boolean supportsTools = provider != null && provider.caps().supportsNativeTools;
        StringBuilder sb = new StringBuilder();
        for (Tool t : ToolRegistry.list()) {
            if (t.spec().name.startsWith(ToolRegistry.MCP_TOOL_PREFIX) && !supportsTools) continue;
            sb.append("- ").append(t.spec().name).append(": ").append(t.spec().description).append("\n");
            sb.append("  Schema: ").append(t.spec().jsonSchema).append("\n");
        }
        return sb.toString();
    }
}
