package pro.sketchware.ai.models;

/**
 * [WHAT] Configuration for an AI provider.
 * [WHY] Allows multiple instances of the same provider type (e.g. multiple OpenAI-Compatible).
 * [HOW] Data class with type, name, and optional base URL. Keys are stored separately in SecureKeyStore.
 */
public class ProviderConfig {
    public String id;
    public String type; // openai, anthropic, gemini, compatible, local
    public String name;
    public String baseUrl;

    public ProviderConfig() {}

    public ProviderConfig(String id, String type, String name, String baseUrl) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.baseUrl = baseUrl;
    }
}
