package pro.sketchware.ai.core;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One function/tool call requested by the model. {@link #arguments} is always
 * a valid JSONObject (empty when the model sent none or malformed JSON).
 */
public final class ToolCall {

    public final String id;
    public final String name;
    public final JSONObject arguments;

    public ToolCall(String id, String name, JSONObject arguments) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.arguments = arguments == null ? new JSONObject() : arguments;
    }

    /**
     * Parses raw argument JSON defensively; malformed JSON yields an empty
     * object instead of crashing the agent loop.
     */
    public static ToolCall of(String id, String name, String rawArguments) {
        JSONObject parsed = null;
        if (rawArguments != null && !rawArguments.isEmpty()) {
            try {
                parsed = new JSONObject(rawArguments);
            } catch (JSONException ignored) {
                // Malformed tool arguments must never crash the loop.
            }
        }
        return new ToolCall(id, name, parsed);
    }

    public String argString(String key, String defaultValue) {
        String value = arguments.optString(key, null);
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    public boolean argBoolean(String key, boolean defaultValue) {
        return arguments.has(key) ? arguments.optBoolean(key, defaultValue) : defaultValue;
    }

    /** Serializes this call back to JSON (used when persisting chat history). */
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("name", name);
            json.put("arguments", arguments);
        } catch (JSONException ignored) {
            // Cannot happen for String/JSONObject values.
        }
        return json;
    }

    /** Restores a call previously written by {@link #toJson()}. */
    public static ToolCall fromJson(JSONObject json) {
        return new ToolCall(
                json.optString("id"),
                json.optString("name"),
                json.optJSONObject("arguments"));
    }
}
