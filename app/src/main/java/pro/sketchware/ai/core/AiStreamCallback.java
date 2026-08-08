package pro.sketchware.ai.core;

public interface AiStreamCallback {
    void onToken(String token);
    void onDone(AiResponse response);
    void onError(AiError error);
}
