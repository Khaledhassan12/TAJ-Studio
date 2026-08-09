package pro.sketchware.ai.core;

public interface AiStreamCallback {
    void onToken(String token);
    default void onThought(String thought) {}
    void onDone(AiResponse response);
    void onError(AiError error);
}
