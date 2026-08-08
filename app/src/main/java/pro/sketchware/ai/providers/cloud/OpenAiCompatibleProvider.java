package pro.sketchware.ai.providers.cloud;

import pro.sketchware.ai.core.CapabilityProfile;

/**
 * [WHAT] OpenAI-Compatible provider (Groq, Together, self-hosted, etc.).
 * [WHY] Allows using many alternative cloud AI engines with the same protocol.
 * [HOW] Inherits from OpenAiProvider but allows setting a custom baseUrl and id/name.
 */
public class OpenAiCompatibleProvider extends OpenAiProvider {

    private final String id;
    private final String name;

    public OpenAiCompatibleProvider(String id, String name, String apiKey, String baseUrl) {
        super(id, name, apiKey, baseUrl);
        this.id = id;
        this.name = name;
    }

    @Override public String id() { return id; }
    @Override public String name() { return name; }

    @Override
    public CapabilityProfile caps() {
        // Conservative capabilities for third-party providers.
        return new CapabilityProfile(true, false, 8192, CapabilityProfile.SystemPromptStyle.MESSAGE);
    }
}
