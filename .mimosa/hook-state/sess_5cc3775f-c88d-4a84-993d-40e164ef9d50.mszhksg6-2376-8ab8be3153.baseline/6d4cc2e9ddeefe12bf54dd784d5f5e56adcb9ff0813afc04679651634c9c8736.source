package pro.sketchware.ai.core;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Protocol-neutral description of one agent tool. The registry converts each
 * spec into the OpenAI "function", Anthropic "input_schema" or Gemini
 * "function_declarations" wire format on demand.
 */
public final class ToolSpec {

    public final String name;
    public final String description;
    public final JSONObject jsonSchema;

    public ToolSpec(String name, String description, JSONObject jsonSchema) {
        this.name = name;
        this.description = description;
        this.jsonSchema = jsonSchema == null ? emptyObjectSchema() : jsonSchema;
    }

    public static ToolSpec noArgs(String name, String description) {
        return new ToolSpec(name, description, emptyObjectSchema());
    }

    private static JSONObject emptyObjectSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            schema.put("properties", new JSONObject());
        } catch (JSONException ignored) {
            // Cannot happen for static literals.
        }
        return schema;
    }

    /** OpenAI-compatible format: {"type":"function","function":{...}}. */
    public JSONObject toOpenAITool() {
        JSONObject function = new JSONObject();
        JSONObject wrapper = new JSONObject();
        try {
            function.put("name", name);
            function.put("description", description);
            function.put("parameters", jsonSchema);
            wrapper.put("type", "function");
            wrapper.put("function", function);
        } catch (JSONException ignored) {
            // Cannot happen for these static structures.
        }
        return wrapper;
    }

    /** Anthropic format: {"name","description","input_schema"}. */
    public JSONObject toAnthropicTool() {
        JSONObject tool = new JSONObject();
        try {
            tool.put("name", name);
            tool.put("description", description);
            tool.put("input_schema", jsonSchema);
        } catch (JSONException ignored) {
            // Cannot happen for these static structures.
        }
        return tool;
    }

    /** Gemini format: {"name","description","parameters"} as used in function_declarations. */
    public JSONObject toGeminiDeclaration() {
        JSONObject declaration = new JSONObject();
        try {
            declaration.put("name", name);
            declaration.put("description", description);
            declaration.put("parameters", jsonSchema);
        } catch (JSONException ignored) {
            // Cannot happen for these static structures.
        }
        return declaration;
    }
}
