package pro.sketchware.ai.validate;

public class ValidationResult {
    public boolean success;
    public String reason;
    public GgufInfo info;

    public ValidationResult(boolean success, String reason, GgufInfo info) {
        this.success = success;
        this.reason = reason;
        this.info = info;
    }
}
