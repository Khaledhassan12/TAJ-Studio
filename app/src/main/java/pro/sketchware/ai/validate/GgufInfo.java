package pro.sketchware.ai.validate;

/**
 * [WHAT] Results of a GGUF validation.
 */
public class GgufInfo {
    public final boolean valid;
    public final String arch;
    public final String name;
    public final String quantHint;
    public final long contextLength;
    public final long sizeBytes;
    public final String error;

    public GgufInfo(boolean valid, String arch, String name, String quantHint, long contextLength, long sizeBytes, String error) {
        this.valid = valid;
        this.arch = arch;
        this.name = name;
        this.quantHint = quantHint;
        this.contextLength = contextLength;
        this.sizeBytes = sizeBytes;
        this.error = error;
    }

    public static GgufInfo invalid(String reason) {
        return new GgufInfo(false, null, null, null, 0, 0, reason);
    }
}
