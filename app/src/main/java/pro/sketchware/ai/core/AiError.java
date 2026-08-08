package pro.sketchware.ai.core;

public class AiError {
    public enum Type { Network, Auth, RateLimit, Timeout, Provider, Native, Unknown }

    public Type type;
    public String message;
    public int code;

    public AiError(Type type, String message) {
        this.type = type;
        this.message = message;
    }

    public AiError(Type type, String message, int code) {
        this.type = type;
        this.message = message;
        this.code = code;
    }
}
