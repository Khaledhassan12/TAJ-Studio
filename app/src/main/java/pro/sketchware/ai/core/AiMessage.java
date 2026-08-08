package pro.sketchware.ai.core;

import java.util.List;

public class AiMessage {
    public enum Role { system, user, assistant, tool }

    public Role role;
    public String content;
    public String toolCallId;
    public String toolCallsJson;

    public AiMessage(Role role, String content) {
        this.role = role;
        this.content = content;
    }

    public AiMessage(Role role, String content, String toolCallId, String toolCallsJson) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
        this.toolCallsJson = toolCallsJson;
    }
}
