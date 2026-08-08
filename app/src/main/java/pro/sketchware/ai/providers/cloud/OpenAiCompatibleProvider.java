package pro.sketchware.ai.providers.cloud;

import pro.sketchware.ai.core.CapabilityProfile;

public class OpenAiCompatibleProvider extends OpenAiProvider {

    private final String id;
    private final String name;

    public OpenAiCompatibleProvider(String id, String name, String apiKey, String baseUrl) {
        super(apiKey, baseUrl);
        this.id = id;
        this.name = name;
    }

    @Override public String id() { return id; }
    @Override public String name() { return name; }

    @Override
    public CapabilityProfile caps() {
        return new CapabilityProfile(true, false, 8192, CapabilityProfile.SystemPromptStyle.MESSAGE);
    }
}
