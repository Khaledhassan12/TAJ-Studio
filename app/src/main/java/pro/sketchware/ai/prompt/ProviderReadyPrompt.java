package pro.sketchware.ai.prompt;

import pro.sketchware.ai.core.AiMessage;

/**
 * [WHAT] A prompt adapted for a specific provider's API.
 */
public class ProviderReadyPrompt {
    public final String systemString;
    public final AiMessage systemMessage;

    public ProviderReadyPrompt(String systemString, AiMessage systemMessage) {
        this.systemString = systemString;
        this.systemMessage = systemMessage;
    }
}
