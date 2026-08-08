package pro.sketchware.ai.agent.tools;

/**
 * [WHAT] Specification of a tool for AI model awareness.
 */
public class ToolSpec {
    public final String name;
    public final String description;
    public final String jsonSchema;

    public ToolSpec(String name, String description, String jsonSchema) {
        this.name = name;
        this.description = description;
        this.jsonSchema = jsonSchema;
    }
}
