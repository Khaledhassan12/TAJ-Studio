package pro.sketchware.ai.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Final result of a completed chat call: accumulated text, any requested tool
 * calls, the finish reason and (optionally) token usage.
 */
public final class AIResponse {

    public final String text;
    public final List<ToolCall> toolCalls;
    public final String finishReason;
    public final int promptTokens;
    public final int completionTokens;

    public AIResponse(String text, List<ToolCall> toolCalls, String finishReason,
                      int promptTokens, int completionTokens) {
        this.text = text == null ? "" : text;
        this.toolCalls = toolCalls == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(toolCalls));
        this.finishReason = finishReason == null ? "" : finishReason;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public boolean hasText() {
        return !text.isEmpty();
    }
}
