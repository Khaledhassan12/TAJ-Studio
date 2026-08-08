package pro.sketchware.ai.agent;

/**
 * [WHAT] A single step in the agent's reasoning loop.
 */
public class AgentStep {
    public enum Kind { THINK, TOOL_CALL, TOOL_RESULT, TEXT, ERROR }

    public final Kind kind;
    public final String payload;
    public final long timestamp;

    public AgentStep(Kind kind, String payload) {
        this.kind = kind;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }
}
