package pro.sketchware.ai.prompt;

public class ComposeRequest {
    public boolean agentMode;
    public String scId;
    public String userMessage;

    public ComposeRequest(String scId, String userMessage, boolean agentMode) {
        this.scId = scId;
        this.userMessage = userMessage;
        this.agentMode = agentMode;
    }
}
