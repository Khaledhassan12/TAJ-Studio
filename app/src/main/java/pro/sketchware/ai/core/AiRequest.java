package pro.sketchware.ai.core;

import java.util.List;

public class AiRequest {
    public List<AiMessage> messages;
    public String systemPrompt;
    public int maxTokens;
    public double temperature;
    public double topP;
    public int contextSize;
    public String mmprojPath;
    public String modelId;

    public AiRequest(List<AiMessage> messages, String systemPrompt, int maxTokens, double temperature, String modelId) {
        this.messages = messages;
        this.systemPrompt = systemPrompt;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.topP = 0.9;
        this.contextSize = 2048;
        this.mmprojPath = null;
        this.modelId = modelId;
    }
}
