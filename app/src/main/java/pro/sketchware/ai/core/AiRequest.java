package pro.sketchware.ai.core;

import java.util.List;

public class AiRequest {
    public List<AiMessage> messages;
    public String systemPrompt;
    public Integer maxTokens;
    public Float temperature;
    public Float topP;
    public Integer contextSize;
    public String mmprojPath;
    public String modelId;
    public List<String> imagePaths;

    public AiRequest(List<AiMessage> messages, String systemPrompt, Integer maxTokens, Float temperature, String modelId) {
        this.messages = messages;
        this.systemPrompt = systemPrompt;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.topP = 0.9f;
        this.contextSize = 2048;
        this.mmprojPath = null;
        this.modelId = modelId;
    }
}
