package pro.sketchware.ai.prompt;

/**
 * [WHAT] Request to compose a system prompt.
 */
public class ComposeRequest {
    public final String scId;
    public final String userMessage;
    public final boolean agentMode;
    
    public String templateSystemText;
    public String userPrefixText;
    public String userSuffixText;
    public String toolSchemas;

    public ComposeRequest(String scId, String userMessage, boolean agentMode) {
        this.scId = scId;
        this.userMessage = userMessage;
        this.agentMode = agentMode;
    }
}
