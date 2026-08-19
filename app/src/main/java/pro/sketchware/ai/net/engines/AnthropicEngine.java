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
 * Engine for the native Anthropic Messages API (and any Anthropic-compatible
 * gateway with a custom base URL). POSTs to "{base}/v1/messages" (base URL
 * carries no version path for Anthropic). Auth is exactly
 * "x-api-key: &lt;key&gt;" + "anthropic-version: 2023-06-01"; a Bearer header is
 * never sent. Bodies stay minimal: model, max_tokens (required), system
 * (top-level, extracted from system messages), messages (user/assistant only,
 * tool results as tool_result blocks), stream:true, temperature?, tools? with
 * input_schema. SSE events handled: message_start, content_block_start,
 * content_block_delta (text_delta + input_json_delta accumulation),
 * content_block_stop, message_delta, error.
 */
public final class AnthropicEngine extends HttpAI {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    public AnthropicEngine(android.content.Context context, ProviderProfile profile, String baseUrl, String apiKey) {
        super(context, profile, baseUrl, apiKey);
    }

    @Override
    protected Request buildRequest(AIRequest request) throws AIException {
        try {
            JSONObject body = new JSONObject();
            AIConfigStore store = AIConfigStore.getInstance(context);
            body.put("model", store.safeModel());
            int maxTokens = request.maxTokens > 0 ? request.maxTokens : 1024;
            if (request.thinkingEnabled) {
                JSONObject thinking = new JSONObject();
                thinking.put("type", "enabled");
                thinking.put("budget_tokens", 4096);
                body.put("thinking", thinking);
                if (maxTokens < 8192) {
                    maxTokens = 8192;
                }
            }
            body.put("max_tokens", maxTokens);
            body.put("stream", true);
            if (request.temperature >= 0f) {
                body.put("temperature", request.temperature);
            }
            if (request.systemPrompt != null && !request.systemPrompt.isEmpty()) {
                body.put("system", request.systemPrompt);
            }
            body.put("messages", buildMessages(request));

            if (request.hasTools()) {
                JSONArray tools = new JSONArray();
                for (ToolSpec spec : request.tools) {
                    tools.put(spec.toAnthropicTool());
                }
                body.put("tools", tools);
            }

            Request.Builder builder = new Request.Builder()
                    .url(buildUrl("/v1/messages"))
                    .post(jsonBody(body.toString()))
                    .header("x-api-key", store.safeKey())
                    .header("anthropic-version", "2023-06-01");
            for (Map.Entry<String, String> header : profile.extraHeaders.entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
            return builder.build();
        } catch (JSONException e) {
            throw new AIException(AIException.Type.INVALID_JSON, "Failed to build the request payload.", e);
        }
    }

    /**
     * Converts the neutral conversation into Anthropic content blocks, merging
     * consecutive same-role turns (required: consecutive tool results are all
     * "user" turns and the API rejects repeated roles).
     */
    private static JSONArray buildMessages(AIRequest request) throws JSONException {
        JSONArray result = new JSONArray();
        JSONObject current = null;
        String currentRole = null;

        for (AIMessage message : request.messages) {
            if (AIMessage.ROLE_SYSTEM.equals(message.role)) {
                continue; // Carried by the top-level "system" field instead.
            }

            boolean toolResult = AIMessage.ROLE_TOOL.equals(message.role);
            String role = toolResult || AIMessage.ROLE_USER.equals(message.role) ? "user" : "assistant";

            JSONArray blocks = new JSONArray();
            if (AIMessage.ROLE_ASSISTANT.equals(message.role) && message.hasToolCalls()) {
                if (!message.text.isEmpty()) {
                    blocks.put(new JSONObject().put("type", "text").put("text", message.text));
                }
                for (ToolCall call : message.toolCalls) {
                    blocks.put(new JSONObject()
                            .put("type", "tool_use")
                            .put("id", call.id)
                            .put("name", call.name)
                            .put("input", call.arguments));
                }
            } else if (toolResult) {
                blocks.put(new JSONObject()
                        .put("type", "tool_result")
                        .put("tool_use_id", message.toolCallId)
                        .put("content", message.text));
            } else {
                blocks.put(new JSONObject().put("type", "text").put("text", message.text));
            }

            if (role.equals(currentRole) && current != null) {
                JSONArray content = current.getJSONArray("content");
                for (int i = 0; i < blocks.length(); i++) {
                    content.put(blocks.get(i));
                }
            } else {
                current = new JSONObject().put("role", role).put("content", blocks);
                result.put(current);
                currentRole = role;
            }
        }
        return result;
    }

    private static final class AnthropicState implements StreamState {
        final StringBuilder text = new StringBuilder();
        final Map<Integer, ToolUseFragment> blocks = new HashMap<>();
        final List<ToolCall> toolCalls = new ArrayList<>();
        String finishReason = "";
        int promptTokens = 0;
        int completionTokens = 0;
    }

    private static final class ToolUseFragment {
        String id = "";
        String name = "";
        final StringBuilder input = new StringBuilder();
        boolean thinking = false;
    }

    @Override
    protected StreamState newState() {
        return new AnthropicState();
    }
    @Override
    protected void handleEvent(StreamState state, String eventName, String data, Tracked tracked)
            throws AIException {
        AnthropicState anthropic = (AnthropicState) state;
        JSONObject payload;
        try {
            payload = new JSONObject(data);
        } catch (JSONException e) {
            return; // Ignore malformed chatter.
        }

        String type = eventName != null ? eventName : payload.optString("type", "");
        switch (type) {
            case "message_start": {
                JSONObject message = payload.optJSONObject("message");
                if (message != null) {
                    JSONObject usage = message.optJSONObject("usage");
                    if (usage != null) {
                        anthropic.promptTokens = usage.optInt("input_tokens", 0);
                    }
                }
                break;
            }
            case "content_block_start": {
                int index = payload.optInt("index", 0);
                JSONObject block = payload.optJSONObject("content_block");
                if (block != null) {
                    String blockType = block.optString("type");
                    if ("tool_use".equals(blockType)) {
                        ToolUseFragment fragment = new ToolUseFragment();
                        fragment.id = block.optString("id", "");
                        fragment.name = block.optString("name", "");
                        anthropic.blocks.put(index, fragment);
                    } else if ("thinking".equals(blockType)) {
                        ToolUseFragment fragment = new ToolUseFragment();
                        fragment.thinking = true;
                        anthropic.blocks.put(index, fragment);
                    }
                }
                break;
            }
            case "content_block_delta": {
                int index = payload.optInt("index", 0);
                JSONObject delta = payload.optJSONObject("delta");
                if (delta == null) {
                    break;
                }
                String deltaType = delta.optString("type", "");
                if ("text_delta".equals(deltaType)) {
                    String token = delta.optString("text", "");
                    if (!token.isEmpty()) {
                        anthropic.text.append(token);
                        tracked.onToken(token);
                    }
                } else if ("thinking_delta".equals(deltaType)) {
                    String thinking = delta.optString("thinking", "");
                    if (!thinking.isEmpty()) {
                        tracked.onReasoningToken(thinking);
                    }
                } else if ("input_json_delta".equals(deltaType)) {
                    ToolUseFragment fragment = anthropic.blocks.get(index);
                    if (fragment != null) {
                        fragment.input.append(delta.optString("partial_json", ""));
                    }
                }
                break;
            }
            case "content_block_stop": {
                int index = payload.optInt("index", 0);
                ToolUseFragment fragment = anthropic.blocks.remove(index);
                if (fragment != null) {
                    ToolCall call = ToolCall.of(fragment.id, fragment.name, fragment.input.toString());
                    anthropic.toolCalls.add(call);
                    tracked.onToolCall(call);
                }
                break;
            }
            case "message_delta": {
                JSONObject delta = payload.optJSONObject("delta");
                if (delta != null) {
                    String stopReason = delta.optString("stop_reason", null);
                    if (stopReason != null && !stopReason.isEmpty()) {
                        anthropic.finishReason = "tool_use".equals(stopReason) ? "tool_calls" : stopReason;
                    }
                }
                JSONObject usage = payload.optJSONObject("usage");
                if (usage != null) {
                    anthropic.completionTokens = usage.optInt("output_tokens", anthropic.completionTokens);
                }
                break;
            }
            case "error": {
                JSONObject error = payload.optJSONObject("error");
                String message = error == null ? "Unknown provider error." : error.optString("message", "Unknown provider error.");
                throw new AIException(AIException.Type.HTTP, "The provider reported an error: " + message);
            }
            default:
                // message_stop and unknown events need no action.
                break;
        }
    }

    @Override
    protected void parseNonStreaming(StreamState state, String rawBody, Tracked tracked) throws AIException {
        AnthropicState anthropic = (AnthropicState) state;
        try {
            JSONObject body = new JSONObject(rawBody);
            JSONObject usage = body.optJSONObject("usage");
            if (usage != null) {
                anthropic.promptTokens = usage.optInt("input_tokens", 0);
                anthropic.completionTokens = usage.optInt("output_tokens", 0);
            }
            String stopReason = body.optString("stop_reason", "");
            anthropic.finishReason = "tool_use".equals(stopReason) ? "tool_calls" : stopReason;

            JSONArray content = body.optJSONArray("content");
            if (content == null) {
                return;
            }
            for (int i = 0; i < content.length(); i++) {
                JSONObject block = content.optJSONObject(i);
                if (block == null) {
                    continue;
                }
                if ("text".equals(block.optString("type"))) {
                    String text = block.optString("text", "");
                    if (!text.isEmpty()) {
                        anthropic.text.append(text);
                        tracked.onToken(text);
                    }
                } else if ("thinking".equals(block.optString("type"))) {
                    String thinking = block.optString("thinking", "");
                    if (!thinking.isEmpty()) {
                        tracked.onReasoningToken(thinking);
                    }
                } else if ("tool_use".equals(block.optString("type"))) {
                    anthropic.toolCalls.add(new ToolCall(
                            block.optString("id", ""),
                            block.optString("name", ""),
                            block.optJSONObject("input")));
                }
            }
        } catch (JSONException e) {
            throw new AIException(AIException.Type.INVALID_JSON, "The provider returned malformed JSON.", e);
        }
    }

    @Override
    protected void finishStream(StreamState state, Tracked tracked) {
        AnthropicState anthropic = (AnthropicState) state;
        tracked.complete(new AIResponse(anthropic.text.toString(), anthropic.toolCalls,
                anthropic.finishReason, anthropic.promptTokens, anthropic.completionTokens));
    }
}
