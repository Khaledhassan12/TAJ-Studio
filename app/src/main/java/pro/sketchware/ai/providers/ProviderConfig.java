package pro.sketchware.ai.providers;

import pro.sketchware.ai.data.SecureKeyStore;

/**
 * [WHAT] Configuration for an AI provider.
 * [WHY] Allows multiple instances of the same provider type (e.g. multiple OpenAI-Compatible).
 * [HOW] Data class with type, name, and optional base URL. Keys are stored separately in SecureKeyStore.
 */
public class ProviderConfig {
    public static final String TYPE_CLOUD = "cloud";
    public static final String TYPE_LOCAL = "local";

    public String id;
    public String displayName;
    public String type; // cloud, local
    public String providerType; // openai, anthropic, gemini, compatible, llama-cpp
    public String wireProtocol; // google-wire, openai-wire, anthropic-wire
    public String baseUrl;
    public boolean enabled;
    public boolean worksWithoutKey;
    public long lastTestedAt;
    public String lastTestResult; // success, auth_error, network_error, unknown

    // Derived fields, not persisted in JSON
    public transient boolean hasKey;
    public transient int keyCount;

    public ProviderConfig() {}

    public ProviderConfig(String id, String displayName, String type, String wireProtocol, String baseUrl, boolean enabled) {
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.wireProtocol = wireProtocol;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
    }
}
