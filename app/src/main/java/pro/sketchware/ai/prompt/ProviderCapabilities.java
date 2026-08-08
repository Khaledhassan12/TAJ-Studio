package pro.sketchware.ai.prompt;

import pro.sketchware.ai.core.CapabilityProfile;

public class ProviderCapabilities {
    public boolean supportsSystemRole;
    public CapabilityProfile.SystemPromptStyle systemPlacement;
    public boolean supportsNativeTools;
    public int contextSize;

    public ProviderCapabilities(boolean supportsSystemRole, CapabilityProfile.SystemPromptStyle systemPlacement, boolean supportsNativeTools, int contextSize) {
        this.supportsSystemRole = supportsSystemRole;
        this.systemPlacement = systemPlacement;
        this.supportsNativeTools = supportsNativeTools;
        this.contextSize = contextSize;
    }
}
