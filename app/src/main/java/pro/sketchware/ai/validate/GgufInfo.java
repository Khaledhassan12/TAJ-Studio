package pro.sketchware.ai.validate;

public class GgufInfo {
    public boolean valid;
    public String architecture;
    public String name;
    public String quantVersion;
    public String tokenizerModel;
    public long contextLength;
    public long fileSizeBytes;

    public GgufInfo(boolean valid, String architecture, String name, String quantVersion, String tokenizerModel, long contextLength, long fileSizeBytes) {
        this.valid = valid;
        this.architecture = architecture;
        this.name = name;
        this.quantVersion = quantVersion;
        this.tokenizerModel = tokenizerModel;
        this.contextLength = contextLength;
        this.fileSizeBytes = fileSizeBytes;
    }
}
