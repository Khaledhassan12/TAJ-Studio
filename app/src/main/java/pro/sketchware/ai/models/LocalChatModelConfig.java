package pro.sketchware.ai.models;

import com.google.gson.Gson;
import java.util.UUID;

/**
 * [WHAT] Configuration for a local GGUF chat model (Agora port).
 * [WHY] Standardizes parameters and paths for local inference.
 * [HOW] Immutable POJO used for JSON persistence in AiStorage models.metadataJson.
 */
public class LocalChatModelConfig {
    public final String id;
    public final String modelId;
    public final String alias;
    public final String localFilePath;
    public final String mmprojPath;
    public final int nCtx;
    public final float temperature;
    public final float topP;
    public final int maxTokens;

    public LocalChatModelConfig(String id, String modelId, String alias, String localFilePath, 
                               String mmprojPath, int nCtx, float temperature, float topP, int maxTokens) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.modelId = modelId;
        this.alias = alias;
        this.localFilePath = localFilePath;
        this.mmprojPath = mmprojPath != null ? mmprojPath : "";
        this.nCtx = nCtx > 0 ? nCtx : 2048;
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens > 0 ? maxTokens : 4096;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public static LocalChatModelConfig fromJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return new Gson().fromJson(json, LocalChatModelConfig.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static LocalChatModelConfig defaults(String modelId, String alias, String localFilePath) {
        return new LocalChatModelConfig(null, modelId, alias, localFilePath, "", 2048, 0.7f, 0.9f, 4096);
    }
}
