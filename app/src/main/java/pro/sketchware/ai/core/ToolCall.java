package pro.sketchware.ai.core;

import org.json.JSONException;
import org.json.JSONObject;

/** One provider-neutral tool call with explicit malformed-argument tracking. */
public final class ToolCall {
    public final String id;
    public final String name;
    public final JSONObject arguments;
    /** True only when a provider supplied non-empty argument text that was not valid JSON object syntax. */
    public final boolean malformedArguments;

    public ToolCall(String id, String name, JSONObject arguments) {
        this(id, name, arguments, false);
    }

    private ToolCall(String id, String name, JSONObject arguments, boolean malformedArguments) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.arguments = arguments == null ? new JSONObject() : arguments;
        this.malformedArguments = malformedArguments;
    }

    /** Parses raw argument JSON without crashing; malformed payloads remain explicitly invalid. */
    public static ToolCall of(String id, String name, String rawArguments) {
        if (rawArguments == null || rawArguments.trim().isEmpty()) {
            return new ToolCall(id, name, new JSONObject(), false);
        }
        try {
            return new ToolCall(id, name, new JSONObject(rawArguments), false);
        } catch (JSONException | RuntimeException ignored) {
            return new ToolCall(id, name, new JSONObject(), true);
        }
    }

    public String argString(String key, String defaultValue) {
        String value = arguments.optString(key, null);
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    public boolean argBoolean(String key, boolean defaultValue) {
        return arguments.has(key) ? arguments.optBoolean(key, defaultValue) : defaultValue;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("name", name);
            json.put("arguments", arguments);
            if (malformedArguments) json.put("malformedArguments", true);
        } catch (JSONException ignored) {
            // Cannot happen for String/JSONObject values.
        }
        return json;
    }

    public static ToolCall fromJson(JSONObject json) {
        return new ToolCall(
                json.optString("id"),
                json.optString("name"),
                json.optJSONObject("arguments"),
                json.optBoolean("malformedArguments", false));
    }
}
