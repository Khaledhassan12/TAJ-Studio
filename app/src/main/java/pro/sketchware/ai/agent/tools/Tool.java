package pro.sketchware.ai.agent.tools;

/**
 * [WHAT] Interface for an executable agent tool.
 */
public interface Tool {
    ToolSpec spec();
    ToolResult execute(ToolArgs args, ToolCtx ctx);
}
