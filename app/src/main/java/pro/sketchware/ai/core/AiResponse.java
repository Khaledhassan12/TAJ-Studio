package pro.sketchware.ai.core;

/**
 * [WHAT] Unified response model for AI completions.
 * [WHY] Standardizes output across different providers and tracks token usage.
 * [HOW] Holds content, finish reason, and optional token usage fields (Change 7).
 */
public class AiResponse {
    public String content;
    public String finishReason;
    public int promptTokens;
    public int completionTokens;
    
    // Change 10: Optional usage
    public Integer totalTokens;
    public Integer inputTokens;
    public Integer outputTokens;
    public Integer reasoningTokens;

    public AiResponse(String content, String finishReason, int promptTokens, int completionTokens) {
        this.content = content;
        this.finishReason = finishReason;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = promptTokens + completionTokens;
        this.inputTokens = promptTokens;
        this.outputTokens = completionTokens;
    }

    public AiResponse(String content) {
        this.content = content;
    }
}
