package pro.sketchware.ai.core;

/**
 * A callable AI backend. The three protocol engines
 * ({@code OpenAICompatibleEngine}, {@code AnthropicEngine}, {@code GeminiEngine})
 * implement this interface; the registry hands out the matching engine for the
 * active {@link ProviderProfile}.
 */
public interface AIProvider {

    String id();

    String displayName();

    Protocol protocol();

    /**
     * Starts a streaming chat completion. Callbacks fire on a background
     * thread. The returned handle can cancel the in-flight call.
     */
    Handle stream(AIRequest request, StreamCallbacks callbacks);

    /** Cancellable handle for an in-flight stream. */
    interface Handle {
        void cancel();

        boolean isCancelled();
    }
}
