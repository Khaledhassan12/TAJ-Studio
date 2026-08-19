package pro.sketchware.ai.net.engines;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Request;
import pro.sketchware.ai.config.AIConfigStore;
import pro.sketchware.ai.core.AIMessage;
import pro.sketchware.ai.core.AIRequest;
import pro.sketchware.ai.core.AIResponse;
import pro.sketchware.ai.core.ProviderProfile;
import pro.sketchware.ai.core.ToolCall;
import pro.sketchware.ai.core.ToolSpec;
import pro.sketchware.ai.net.AIException;
import pro.sketchware.ai.net.HttpAI;

/**
 * Engine for Google Gemini (AI Studio) and Vertex-style compatible gateways.
 * Base URL has no version path; this engine appends ONLY
 * "/v1beta/models/{model}:streamGenerateContent?alt=sse" (streaming) and reads
 * SSE chunks as candidates[0].content.parts[].text plus functionCall parts.
 * Auth: "x-goog-api-key". Body: system_instruction.parts, contents with
 * role user/model, tools.function_declarations, optional generationConfig.
 */
public final class GeminiEngine extends HttpAI {

    public GeminiEngine(android.content.Context context, ProviderProfile profile, String baseUrl, String apiKey) {
        super(context, profile, baseUrl, apiKey);
    }

    @Override
    protected Request buildRequest(AIRequest request) throws AIException {
        try {
            JSONObject body = new JSONObject();
            AIConfigStore store = AIConfigStore.getInstance(context);
            String model = store.safeModel();

            if (request.systemPrompt != null && !request.systemPrompt.isEmpty()) {
                JSONObject instruction = new JSONObject();
                JSONArray instructionParts = new JSONArray();
                instructionParts.put(new JSONObject().put("text", request.systemPrompt));
                instruction.put("parts", instructionParts);
                body.put("system_instruction", instruction);
            }

            body.put("contents", buildContents(request));

            JSONObject generationConfig = new JSONObject();
            if (request.temperature >= 0f) {
                generationConfig.put("temperature", request.temperature);
            }
            if (request.maxTokens > 0) {
                generationConfig.put("maxOutputTokens", request.maxTokens);
            }
            if (request.thinkingEnabled) {
                JSONObject thinkingConfig = new JSONObject();
                thinkingConfig.put("includeThoughts", true);
                generationConfig.put("thinkingConfig", thinkingConfig);
            }
            if (generationConfig.length() > 0) {
                body.put("generationConfig", generationConfig);
            }

            if (request.hasTools()) {
                JSONArray declarations = new JSONArray();
                for (ToolSpec spec : request.tools) {
                    declarations.put(spec.toGeminiDeclaration());
                }
                JSONObject toolsWrapper = new JSONObject();
                toolsWrapper.put("function_declarations", declarations);
                JSONArray tools = new JSONArray();
                tools.put(toolsWrapper);
                body.put("tools", tools);
            }

            String url = buildUrl("/v1beta/models/" + encodeModel(model) + ":streamGenerateContent?alt=sse");
            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .post(jsonBody(body.toString()));
            if (!store.safeKey().isEmpty()) {
                builder.header("x-goog-api-key", store.safeKey());
            }
            for (java.util.Map.Entry<String, String> header : profile.extraHeaders.entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
            return builder.build();
        } catch (JSONException e) {
            throw new AIException(AIException.Type.INVALID_JSON, "Failed to build the request payload.", e);
        }
    }

    /**
     * URL-encodes the model id and tolerates pasted "models/..." prefixes so
     * the server-accepted bare id ("gemini-2.5-pro") and the picker id are
     * always interchangeable.
     */
    private static String encodeModel(String model) throws AIException {
        String clean = model;
        if (clean != null && clean.startsWith("models/")) {
            clean = clean.substring("models/".length());
        }
        if (clean == null || clean.isEmpty()) {
            throw new AIException(AIException.Type.MODEL_NOT_FOUND, "No model name configured for this provider.");
        }
        try {
            return URLEncoder.encode(clean, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return clean;
        }
    }

    /** Maps roles ("assistant" -&gt; "model"), folds tool results in, merges consecutive turns. */
    private static JSONArray buildContents(AIRequest request) throws JSONException {
        JSONArray contents = new JSONArray();
        JSONObject current = null;
        String currentRole = null;

        for (AIMessage message : request.messages) {
            if (AIMessage.ROLE_SYSTEM.equals(message.role)) {
                continue; // Carried by system_instruction instead.
            }

            String role = AIMessage.ROLE_ASSISTANT.equals(message.role) ? "model" : "user";
            JSONArray parts = new JSONArray();

            if (AIMessage.ROLE_ASSISTANT.equals(message.role)) {
                if (!message.text.isEmpty()) {
                    parts.put(new JSONObject().put("text", message.text));
                }
                if (message.hasToolCalls()) {
                    for (ToolCall call : message.toolCalls) {
                        JSONObject functionCall = new JSONObject();
                        functionCall.put("name", call.name);
                        functionCall.put("args", call.arguments);
                        parts.put(new JSONObject().put("functionCall", functionCall));
                    }
                }
                if (parts.length() == 0) {
                    parts.put(new JSONObject().put("text", " "));
                }
            } else if (AIMessage.ROLE_TOOL.equals(message.role)) {
                JSONObject functionResponse = new JSONObject();
                functionResponse.put("name", nameForToolCallId(request, message.toolCallId));
                JSONObject response = new JSONObject();
                response.put("result", message.text);
                functionResponse.put("response", response);
                parts.put(new JSONObject().put("functionResponse", functionResponse));
            } else {
                parts.put(new JSONObject().put("text", message.text));
            }

            if (role.equals(currentRole) && current != null) {
                JSONArray existing = current.getJSONArray("parts");
                for (int i = 0; i < parts.length(); i++) {
                    existing.put(parts.get(i));
                }
            } else {
                current = new JSONObject().put("role", role).put("parts", parts);
                contents.put(current);
                currentRole = role;
            }
        }
        return contents;
    }

    /**
     * Gemini pairs functionResponse with the declaration name, not a call id.
     * Finds the tool name that produced the given synthetic call id.
     */
    private static String nameForToolCallId(AIRequest request, String toolCallId) {
        for (AIMessage message : request.messages) {
            if (message.hasToolCalls()) {
                for (ToolCall call : message.toolCalls) {
                    if (call.id.equals(toolCallId)) {
                        return call.name;
                    }
                }
            }
        }
        return toolCallId == null ? "" : toolCallId;
    }

    private static final class GeminiState implements StreamState {
        final StringBuilder text = new StringBuilder();
        final List<ToolCall> toolCalls = new ArrayList<>();
        String finishReason = "";
        int promptTokens = 0;
        int completionTokens = 0;
        int syntheticIdCounter = 0;
    }

    @Override
    protected StreamState newState() {
        return new GeminiState();
    }
    @Override
    protected void handleEvent(StreamState state, String eventName, String data, Tracked tracked)
            throws AIException {
        parseChunk((GeminiState) state, data, tracked);
    }

    @Override
    protected void parseNonStreaming(StreamState state, String rawBody, Tracked tracked) throws AIException {
        parseChunk((GeminiState) state, rawBody, tracked);
    }

    private void parseChunk(GeminiState gemini, String raw, Tracked tracked) throws AIException {
        JSONObject chunk;
        try {
            chunk = new JSONObject(raw);
        } catch (JSONException e) {
            throw new AIException(AIException.Type.INVALID_JSON, "The provider returned malformed JSON.", e);
        }

        JSONObject promptFeedback = chunk.optJSONObject("promptFeedback");
        if (promptFeedback != null && promptFeedback.has("blockReason")) {
            throw new AIException(AIException.Type.HTTP,
                    "The provider blocked this request: " + promptFeedback.optString("blockReason"));
        }

        JSONObject usage = chunk.optJSONObject("usageMetadata");
        if (usage != null) {
            gemini.promptTokens = usage.optInt("promptTokenCount", gemini.promptTokens);
            gemini.completionTokens = usage.optInt("candidatesTokenCount", gemini.completionTokens);
        }

        JSONArray candidates = chunk.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            return;
        }
        JSONObject candidate = candidates.optJSONObject(0);
        if (candidate == null) {
            return;
        }

        String finishReason = candidate.optString("finishReason", null);
        if (finishReason != null && !finishReason.isEmpty()) {
            gemini.finishReason = finishReason;
        }

        JSONObject content = candidate.optJSONObject("content");
        if (content == null) {
            return;
        }
        JSONArray parts = content.optJSONArray("parts");
        if (parts == null) {
            return;
        }
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part == null) {
                continue;
            }
            String token = part.optString("text", null);
            if (token != null && !token.isEmpty()) {
                if (part.optBoolean("thought", false)) {
                    tracked.onReasoningToken(token);
                } else {
                    gemini.text.append(token);
                    tracked.onToken(token);
                }
            }
            JSONObject functionCall = part.optJSONObject("functionCall");
            if (functionCall != null) {
                String name = functionCall.optString("name", "");
                String id = "gemini_call_" + (++gemini.syntheticIdCounter);
                ToolCall call = new ToolCall(id, name, functionCall.optJSONObject("args"));
                gemini.toolCalls.add(call);
                tracked.onToolCall(call);
            }
        }
    }

    @Override
    protected void finishStream(StreamState state, Tracked tracked) {
        GeminiState gemini = (GeminiState) state;
        tracked.complete(new AIResponse(gemini.text.toString(), gemini.toolCalls,
                gemini.finishReason, gemini.promptTokens, gemini.completionTokens));
    }
}
