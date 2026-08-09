package pro.sketchware.ai.models;

import com.google.gson.Gson;

/**
 * [WHAT] Configuration for a local GGUF model.
 * [WHY] Allows per-model sampling and context settings (P1-D).
 * [HOW] Persisted as JSON in the database.
 */
public class LocalModelConfig {
    public String modelId;
    public String alias;
    public int contextSize = 2048;
    public float temperature = 0.7f;
    public float topP = 0.9f;
    public int maxTokens = 4096;
    public String mmprojPath;

    public String toJson() {
        return new Gson().toJson(this);
    }

    public static LocalModelConfig fromJson(String json) {
        if (json == null || json.isEmpty()) return new LocalModelConfig();
        try {
            return new Gson().fromJson(json, LocalModelConfig.class);
        } catch (Exception e) {
            return new LocalModelConfig();
        }
    }
}
