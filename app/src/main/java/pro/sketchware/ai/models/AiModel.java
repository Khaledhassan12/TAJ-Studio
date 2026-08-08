package pro.sketchware.ai.models;

public class AiModel {
    public enum Kind { LOCAL, CLOUD }

    public String id;
    public Kind kind;
    public String provider;
    public String name;
    public String arch;
    public String quant;
    public long contextLength;
    public String filePath;
    public long sizeBytes;
    public long installedAt;
    public long lastUsedAt;
    public boolean isActive;

    public AiModel() {}

    public AiModel(String id, Kind kind, String provider, String name, String arch, String quant, long contextLength, String filePath, long sizeBytes, long installedAt, long lastUsedAt, boolean isActive) {
        this.id = id;
        this.kind = kind;
        this.provider = provider;
        this.name = name;
        this.arch = arch;
        this.quant = quant;
        this.contextLength = contextLength;
        this.filePath = filePath;
        this.sizeBytes = sizeBytes;
        this.installedAt = installedAt;
        this.lastUsedAt = lastUsedAt;
        this.isActive = isActive;
    }
}
