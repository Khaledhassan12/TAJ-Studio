package pro.sketchware.ai.search;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.data.SecureKeyStore;

/**
 * [WHAT] SSOT for Conversation Search settings.
 * [WHY] P2-CS: Wraps AiStorage kv for typed access (R5/R16); single writer per field.
 * [HOW] Singleton; strictly derived from persisted storage.
 */
public class ConversationSearchSettings {

    public static final String METHOD_KEYWORD = "keyword";
    public static final String METHOD_SEMANTIC = "semantic";

    private static ConversationSearchSettings instance;
    private final AiStorage storage;
    private final Context context;

    private ConversationSearchSettings(Context context) {
        this.context = context.getApplicationContext();
        this.storage = AiStorage.get(this.context);
    }

    public static synchronized ConversationSearchSettings get(Context context) {
        if (instance == null) {
            instance = new ConversationSearchSettings(context);
        }
        return instance;
    }

    // --- Access ---

    public boolean isAccessEnabled() {
        String val = storage.kvGet("cs_access");
        return val != null && Boolean.parseBoolean(val);
    }

    public void setAccessEnabled(boolean enabled) {
        storage.kvPut("cs_access", String.valueOf(enabled));
    }

    // --- Caching ---

    public boolean isAutoCacheEnabled() {
        String val = storage.kvGet("cs_auto_cache");
        return val != null && Boolean.parseBoolean(val);
    }

    public void setAutoCacheEnabled(boolean enabled) {
        storage.kvPut("cs_auto_cache", String.valueOf(enabled));
    }

    // --- Search Methods ---

    public String getModelSearchMethod() {
        String val = storage.kvGet("cs_model_method");
        return val != null ? val : METHOD_KEYWORD;
    }

    public void setModelSearchMethod(String method) {
        storage.kvPut("cs_model_method", method);
    }

    public String getManualSearchMethod() {
        String val = storage.kvGet("cs_manual_method");
        return val != null ? val : METHOD_KEYWORD;
    }

    public void setManualSearchMethod(String method) {
        storage.kvPut("cs_manual_method", method);
    }

    // --- Advanced ---

    public int getContextPerHit() {
        String val = storage.kvGet("cs_context_per_hit");
        try {
            return val != null ? Integer.parseInt(val) : 8;
        } catch (NumberFormatException e) {
            return 8;
        }
    }

    public void setContextPerHit(int count) {
        storage.kvPut("cs_context_per_hit", String.valueOf(Math.max(1, Math.min(20, count))));
    }

    public int getMaxResults() {
        String val = storage.kvGet("cs_max_results");
        try {
            return val != null ? Integer.parseInt(val) : 10;
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    public void setMaxResults(int count) {
        storage.kvPut("cs_max_results", String.valueOf(Math.max(1, Math.min(20, count))));
    }

    public float getSimilarityThreshold() {
        String val = storage.kvGet("cs_similarity");
        try {
            return val != null ? Float.parseFloat(val) : 0.50f;
        } catch (NumberFormatException e) {
            return 0.50f;
        }
    }

    public void setSimilarityThreshold(float threshold) {
        storage.kvPut("cs_similarity", String.valueOf(Math.max(0f, Math.min(1f, threshold))));
    }

    // --- Embedding Models ---

    public static class EmbeddingModel {
        public String id;
        public String type; // "remote" | "local"
        public String provider;
        public String modelName;
        public String baseUrl;
        public String name;
        public int batchSize;
        public String localPath;

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("type", type);
            obj.put("provider", provider);
            obj.put("modelName", modelName);
            obj.put("baseUrl", baseUrl);
            obj.put("name", name);
            obj.put("batchSize", batchSize);
            obj.put("localPath", localPath);
            return obj;
        }

        public static EmbeddingModel fromJson(JSONObject obj) throws JSONException {
            EmbeddingModel m = new EmbeddingModel();
            m.id = obj.getString("id");
            m.type = obj.getString("type");
            m.provider = obj.optString("provider");
            m.modelName = obj.optString("modelName");
            m.baseUrl = obj.optString("baseUrl");
            m.name = obj.getString("name");
            m.batchSize = obj.optInt("batchSize", 8);
            m.localPath = obj.optString("localPath");
            return m;
        }
    }

    public List<EmbeddingModel> getEmbeddingModels() {
        List<EmbeddingModel> list = new ArrayList<>();
        String json = storage.kvGet("cs_embedding_models");
        if (json == null || json.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(EmbeddingModel.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignored) {}
        return list;
    }

    public void setEmbeddingModels(List<EmbeddingModel> models) {
        JSONArray arr = new JSONArray();
        try {
            for (EmbeddingModel m : models) {
                arr.put(m.toJson());
            }
            storage.kvPut("cs_embedding_models", arr.toString());
        } catch (JSONException ignored) {}
    }

    public boolean hasEmbeddingModel() {
        return !getEmbeddingModels().isEmpty();
    }

    public void addEmbeddingModel(EmbeddingModel model, String apiKey) {
        List<EmbeddingModel> models = getEmbeddingModels();
        models.add(model);
        setEmbeddingModels(models);
        if (apiKey != null && !apiKey.isEmpty()) {
            SecureKeyStore.get(context).putKey("emb:" + model.id, apiKey);
        }
    }

    /**
     * P2-CS2 cascade delete (single writer, R5): removes the config, its
     * SecureKeyStore key, ALL stored index vectors for the model, and — for
     * local models — the copied .gguf file itself.
     */
    public void removeEmbeddingModel(String id) {
        EmbeddingModel target = null;
        List<EmbeddingModel> models = getEmbeddingModels();
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).id.equals(id)) {
                target = models.remove(i);
                break;
            }
        }
        setEmbeddingModels(models);
        SecureKeyStore.get(context).removeKey("emb:" + id);

        // Remove the model's index vectors.
        int removed = AiStorage.get(context).deleteEmbeddingsByModelRef(id);
        android.util.Log.i("ConversationSearch", "Model removed: " + id + " (vectors deleted: " + removed + ")");

        // Local models: delete the copied GGUF file.
        if (target != null && "local".equals(target.type) && target.localPath != null) {
            java.io.File file = new java.io.File(target.localPath);
            if (file.exists()) {
                boolean deleted = file.delete();
                android.util.Log.i("ConversationSearch", "Local model file deleted: " + deleted + " (" + target.localPath + ")");
            }
        }
    }

    public String getRemoteKey(String id) {
        return SecureKeyStore.get(context).getKey("emb:" + id);
    }
}
