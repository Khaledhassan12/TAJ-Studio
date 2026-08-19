package pro.sketchware.ai.net.engines;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * Engine for every OpenAI-compatible endpoint. The base URL supplied by the
 * provider already includes any version path; this engine appends ONLY
 * "/chat/completions". Auth is assembled by {@link HttpAI#applyHeaders}:
 * "Authorization: Bearer &lt;key&gt;" (exactly one space), "api-key" header for
 * Azure-style gateways, or no Authorization at all for keyless local servers.
 * Bodies are minimal: {model, messages, stream, temperature?, max_tokens?,
 * tools?}. SSE parsing reads "data:" frames until "data: [DONE]".
 */
public final class OpenAICompatibleEngine extends HttpAI {

    public OpenAICompatibleEngine(android.content.Context context, ProviderProfile profile, String baseUrl, String apiKey) {
        super(context, profile, baseUrl, apiKey);
    }

    @Override
    protected Request buildRequest(AIRequest request) throws AIException {
        try {
            JSONObject body = new JSONObject();
            AIConfigStore store = AIConfigStore.getInstance(context);
            body.put("model", store.safeModel());
            body.put("stream", true);
            if (request.temperature >= 0f) {
                body.put("temperature", request.temperature);
            }
            if (request.maxTokens > 0) {
                body.put("max_tokens", request.maxTokens);
            }

            JSONArray messages = new JSONArray();
            if (request.systemPrompt != null && !request.systemPrompt.isEmpty()) {
                messages.put(messageJson(AIMessage.system(request.systemPrompt)));
            }
            for (AIMessage message : request.messages) {
                messages.put(messageJson(message));
            }
            body.put("messages", messages);

            if (request.hasTools()) {
                JSONArray tools = new JSONArray();
                for (ToolSpec spec : request.tools) {
                    tools.put(spec.toOpenAITool());
                }
                body.put("tools", tools);
            }

            Request.Builder builder = new Request.Builder()
                    .url(buildUrl("/chat/completions"))
                    .post(jsonBody(body.toString()));
            
            if (!store.safeKey().isEmpty()) {
                builder.header("Authorization", "Bearer " + store.safeKey());
                if (baseUrl.contains("openrouter.ai")) {
                    builder.header("HTTP-Referer", "https://taj.studio");
                    builder.header("X-Title", "TAJ Studio");
                }
            }
            return builder.build();
        } catch (JSONException e) {
            throw new AIException(AIException.Type.INVALID_JSON, "Failed to build the request payload.", e);
        }
    }

    private static JSONObject messageJson(AIMessage message) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("role", message.role);
        if (AIMessage.ROLE_ASSISTANT.equals(message.role) && message.hasToolCalls()) {
            if (!message.text.isEmpty()) {
                json.put("content", message.text);
            } else {
                json.put("content", JSONObject.NULL);
            }
            JSONArray calls = new JSONArray();
            for (ToolCall call : message.toolCalls) {
                JSONObject callJson = new JSONObject();
                callJson.put("id", call.id);
                callJson.put("type", "function");
                JSONObject function = new JSONObject();
                function.put("name", call.name);
                function.put("arguments", call.arguments.toString());
                callJson.put("function", function);
                calls.put(callJson);
            }
            json.put("tool_calls", calls);
        } else if (AIMessage.ROLE_TOOL.equals(message.role)) {
            json.put("content", message.text);
            json.put("tool_call_id", message.toolCallId);
        } else {
            json.put("content", message.text);
        }
        return json;
    }

    private static final class OpenAIState implements StreamState {
        final StringBuilder text = new StringBuilder();
        final Map<Integer, ToolCallFragment> fragments = new HashMap<>();
        final List<ToolCall> toolCalls = new ArrayList<>();
        String finishReason = "";
        int promptTokens = 0;
        int completionTokens = 0;
    }

    private static final class ToolCallFragment {
        String id = "";
        String name = "";
        final StringBuilder arguments = new StringBuilder();
    }

    @Override
    protected StreamState newState() {
        return new OpenAIState();
    }

    @Override
    protected void handleEvent(StreamState state, String eventName, String data, Tracked tracked)
            throws AIException {
        OpenAIState openAi = (OpenAIState) state;
        JSONObject chunk;
        try {
            chunk = new JSONObject(data);
        } catch (JSONException e) {
            // Non-JSON chatter (proxies, pings) is ignored rather than fatal.
            return;
        }

        JSONObject usage = chunk.optJSONObject("usage");
        if (usage != null) {
            openAi.promptTokens = usage.optInt("prompt_tokens", openAi.promptTokens);
            openAi.completionTokens = usage.optInt("completion_tokens", openAi.completionTokens);
        }

        JSONArray choices = chunk.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            return;
        }
        JSONObject choice = choices.optJSONObject(0);
        if (choice == null) {
            return;
        }

        String finishReason = choice.optString("finish_reason", null);
        if (finishReason != null && !"null".equals(finishReason)) {
            openAi.finishReason = finishReason;
        }

        JSONObject delta = choice.optJSONObject("delta");
        if (delta == null) {
            return;
        }

        String token = delta.optString("content", null);
        if (token != null && !token.isEmpty()) {
            openAi.text.append(token);
            tracked.onToken(token);
        }

        JSONArray deltaCalls = delta.optJSONArray("tool_calls");
        if (deltaCalls != null) {
            for (int i = 0; i < deltaCalls.length(); i++) {
                JSONObject deltaCall = deltaCalls.optJSONObject(i);
                if (deltaCall == null) {
                    continue;
                }
                int index = deltaCall.optInt("index", 0);
                ToolCallFragment fragment = openAi.fragments.get(index);
                if (fragment == null) {
                    fragment = new ToolCallFragment();
                    openAi.fragments.put(index, fragment);
                }
                if (deltaCall.has("id") && !deltaCall.isNull("id")) {
                    fragment.id = deltaCall.optString("id", fragment.id);
                }
                JSONObject function = deltaCall.optJSONObject("function");
                if (function != null) {
                    if (function.has("name") && !function.isNull("name")) {
                        fragment.name = function.optString("name", fragment.name);
                    }
                    fragment.arguments.append(function.optString("arguments", ""));
                }
            }
        }
    }
    @Override
    protected void parseNonStreaming(StreamState state, String rawBody, Tracked tracked) throws AIException {
        OpenAIState openAi = (OpenAIState) state;
        try {
            JSONObject body = new JSONObject(rawBody);
            JSONObject usage = body.optJSONObject("usage");
            if (usage != null) {
                openAi.promptTokens = usage.optInt("prompt_tokens", 0);
                openAi.completionTokens = usage.optInt("completion_tokens", 0);
            }
            JSONArray choices = body.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new AIException(AIException.Type.INVALID_JSON, "The provider returned no choices.");
            }
            JSONObject choice = choices.optJSONObject(0);
            openAi.finishReason = choice.optString("finish_reason", "");
            JSONObject message = choice.optJSONObject("message");
            if (message == null) {
                return;
            }
            String text = message.optString("content", "");
            if (!text.isEmpty()) {
                openAi.text.append(text);
                tracked.onToken(text);
            }
            JSONArray calls = message.optJSONArray("tool_calls");
            if (calls != null) {
                for (int i = 0; i < calls.length(); i++) {
                    JSONObject call = calls.optJSONObject(i);
                    if (call == null) {
                        continue;
                    }
                    JSONObject function = call.optJSONObject("function");
                    openAi.toolCalls.add(ToolCall.of(
                            call.optString("id", "call_" + i),
                            function == null ? "" : function.optString("name", ""),
                            function == null ? "" : function.optString("arguments", "")));
                }
            }
        } catch (JSONException e) {
            throw new AIException(AIException.Type.INVALID_JSON, "The provider returned malformed JSON.", e);
        }
    }

    @Override
    protected void finishStream(StreamState state, Tracked tracked) {
        OpenAIState openAi = (OpenAIState) state;
        List<Integer> indexes = new ArrayList<>(openAi.fragments.keySet());
        java.util.Collections.sort(indexes);
        for (Integer index : indexes) {
            ToolCallFragment fragment = openAi.fragments.get(index);
            ToolCall call = ToolCall.of(fragment.id, fragment.name, fragment.arguments.toString());
            openAi.toolCalls.add(call);
            tracked.onToolCall(call);
        }
        tracked.complete(new AIResponse(openAi.text.toString(), openAi.toolCalls,
                openAi.finishReason, openAi.promptTokens, openAi.completionTokens));
    }
}
