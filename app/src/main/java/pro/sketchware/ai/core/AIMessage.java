package pro.sketchware.ai.core;

import java.util.ArrayList;
import java.util.List;

/**
 * One message in a chat conversation. Roles follow the OpenAI convention:
 * "system", "user", "assistant" and "tool". An assistant message may carry
 * {@link #toolCalls}; a tool message carries {@link #toolCallId} plus the
 * serialized tool result in {@link #text}.
 */
public final class AIMessage {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";

    public final String role;
    public final String text;
    public final List<ToolCall> toolCalls;
    public final String toolCallId;

    private AIMessage(String role, String text, List<ToolCall> toolCalls, String toolCallId) {
        this.role = role;
        this.text = text == null ? "" : text;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
    }

    public static AIMessage system(String text) {
        return new AIMessage(ROLE_SYSTEM, text, null, null);
    }

    public static AIMessage user(String text) {
        return new AIMessage(ROLE_USER, text, null, null);
    }

    public static AIMessage assistant(String text) {
        return new AIMessage(ROLE_ASSISTANT, text, null, null);
    }

    public static AIMessage assistantWithToolCalls(List<ToolCall> toolCalls) {
        return new AIMessage(ROLE_ASSISTANT, "", new ArrayList<>(toolCalls), null);
    }

    public static AIMessage toolResult(String toolCallId, String content) {
        return new AIMessage(ROLE_TOOL, content, null, toolCallId);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
