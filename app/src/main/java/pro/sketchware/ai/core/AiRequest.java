package pro.sketchware.ai.core;

import java.util.List;

public class AiRequest {
    public List<AiMessage> messages;
    public String systemPrompt;
    public int maxTokens;
    public double temperature;
    public String modelId;

    public AiRequest(List<AiMessage> messages, String systemPrompt, int maxTokens, double temperature, String modelId) {
        this.messages = messages;
        this.systemPrompt = systemPrompt;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.modelId = modelId;
    }
}
