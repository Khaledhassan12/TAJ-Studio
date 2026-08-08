package pro.sketchware.ai.prompt;

import pro.sketchware.ai.core.AiMessage;
import pro.sketchware.ai.core.CapabilityProfile;

public class PromptComposer {

    public static ProviderReadyPrompt adapt(ComposedPrompt composed, ProviderCapabilities caps) {
        if (caps.systemPlacement == CapabilityProfile.SystemPromptStyle.TOP_LEVEL_FIELD) {
            return new ProviderReadyPrompt(composed.systemText, null);
        } else {
            return new ProviderReadyPrompt(null, new AiMessage(AiMessage.Role.system, composed.systemText));
        }
    }
}
