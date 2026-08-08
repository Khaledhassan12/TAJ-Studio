package pro.sketchware.ai.agent.tools;

/**
 * [WHAT] Result of a tool execution.
 */
public class ToolResult {
    public final boolean ok;
    public final String content;

    public ToolResult(boolean ok, String content) {
        this.ok = ok;
        this.content = content;
    }

    public static ToolResult success(String content) {
        return new ToolResult(true, content);
    }

    public static ToolResult error(String message) {
        return new ToolResult(false, message);
    }
}
