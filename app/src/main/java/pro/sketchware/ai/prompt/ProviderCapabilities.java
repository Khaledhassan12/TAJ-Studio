package pro.sketchware.ai.prompt;

import pro.sketchware.ai.core.CapabilityProfile;

/**
 * [WHAT] Simplified record of provider differences for prompt adaptation.
 */
public class ProviderCapabilities {
    public final boolean supportsSystemRole;
    public final CapabilityProfile.SystemPromptStyle systemPlacement;
    public final boolean supportsNativeTools;
    public final int contextSize;

    public ProviderCapabilities(boolean supportsSystemRole, CapabilityProfile.SystemPromptStyle systemPlacement, boolean supportsNativeTools, int contextSize) {
        this.supportsSystemRole = supportsSystemRole;
        this.systemPlacement = systemPlacement;
        this.supportsNativeTools = supportsNativeTools;
        this.contextSize = contextSize;
    }
}
