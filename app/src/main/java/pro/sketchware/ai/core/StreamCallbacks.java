package pro.sketchware.ai.core;

/**
 * Streaming callbacks invoked by an engine as tokens arrive. All callbacks are
 * delivered on a background thread; UI layers must hop to the main thread
 * themselves. Engines guarantee exactly one terminal callback: either
 * {@link #onComplete(AIResponse)} or {@link #onError(Throwable)}.
 */
public interface StreamCallbacks {

    /** Called for every decoded text token/chunk. Never null, may be multi-char. */
    void onToken(String token);

    /**
     * Called for every reasoning token/chunk. Unlike onToken, this text
     * describes the model's internal thoughts and is not part of the final answer.
     */
    default void onReasoningToken(String token) {
    }

    /** Called when the model has finished assembling a full tool call. */
    default void onToolCall(ToolCall toolCall) {
    }

    /** Terminal success. {@code response.text} already includes all streamed tokens. */
    void onComplete(AIResponse response);

    /** Terminal failure. Always an {@link pro.sketchware.ai.net.AIException}. */
    void onError(Throwable error);
}
