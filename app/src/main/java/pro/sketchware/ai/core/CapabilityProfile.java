package pro.sketchware.ai.core;

public class CapabilityProfile {
    public enum SystemPromptStyle { MESSAGE, TOP_LEVEL_FIELD }

    public boolean supportsStreaming;
    public boolean supportsNativeTools;
    public int contextSize;
    public SystemPromptStyle systemPromptStyle;

    public CapabilityProfile(boolean supportsStreaming, boolean supportsNativeTools, int contextSize, SystemPromptStyle systemPromptStyle) {
        this.supportsStreaming = supportsStreaming;
        this.supportsNativeTools = supportsNativeTools;
        this.contextSize = contextSize;
        this.systemPromptStyle = systemPromptStyle;
    }
}
