package pro.sketchware.ai.core;

public class AiResponse {
    public String content;
    public String finishReason;
    public int promptTokens;
    public int completionTokens;

    public AiResponse(String content, String finishReason, int promptTokens, int completionTokens) {
        this.content = content;
        this.finishReason = finishReason;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }
}
