package pro.sketchware.ai.runtime;

/**
 * [WHAT] Sealed-like event model for AI streams.
 * [WHY] Standardizes message passing between isolated service and UI (Agora port).
 * [HOW] Abstract base with static specialized implementations.
 */
public abstract class StreamEvent {
    private StreamEvent() {}

    public static class TextChunk extends StreamEvent {
        public final String text;
        public TextChunk(String text) { this.text = text; }
    }

    public static class ThoughtChunk extends StreamEvent {
        public final String thought;
        public final String titleOrNull;
        public ThoughtChunk(String thought, String titleOrNull) {
            this.thought = thought;
            this.titleOrNull = titleOrNull;
        }
    }

    public static class UsageUpdate extends StreamEvent {
        public final int totalTokens;
        public final Integer outputTokens;
        public final Integer reasoningTokens;
        public UsageUpdate(int totalTokens, Integer outputTokens, Integer reasoningTokens) {
            this.totalTokens = totalTokens;
            this.outputTokens = outputTokens;
            this.reasoningTokens = reasoningTokens;
        }
    }

    public static class Error extends StreamEvent {
        public final String message;
        public Error(String message) { this.message = message; }
    }
}
