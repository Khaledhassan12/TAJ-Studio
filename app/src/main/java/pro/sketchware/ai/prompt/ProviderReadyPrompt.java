package pro.sketchware.ai.prompt;

import pro.sketchware.ai.core.AiMessage;

public class ProviderReadyPrompt {
    public String systemString;
    public AiMessage systemMessage;

    public ProviderReadyPrompt(String systemString, AiMessage systemMessage) {
        this.systemString = systemString;
        this.systemMessage = systemMessage;
    }
}
