package pro.sketchware.ai.transcription;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;
import pro.sketchware.ai.data.AiStorage;

/**
 * [WHAT] SSOT for Image Transcription settings.
 * [WHY] P2-IT: Wraps AiStorage kv for typed access (R5/R16).
 * [HOW] Singleton; strictly derived from persisted storage.
 */
public class TranscriptionSettings {

    private static TranscriptionSettings instance;
    private final AiStorage storage;
    private final Gson gson = new Gson();

    private TranscriptionSettings(Context context) {
        this.storage = AiStorage.get(context);
    }

    public static synchronized TranscriptionSettings get(Context context) {
        if (instance == null) {
            instance = new TranscriptionSettings(context.getApplicationContext());
        }
        return instance;
    }

    public boolean isEnabled() {
        return storage.isTranscriptionEnabled();
    }

    public void setEnabled(boolean enabled) {
        storage.setTranscriptionEnabled(enabled);
    }

    public String getModel() {
        return storage.getTranscriptionModel();
    }

    public void setModel(String modelId) {
        storage.setTranscriptionModel(modelId);
    }

    public List<String> getEnabledModels() {
        String json = storage.getTranscriptionEnabledModelsJson();
        List<String> list = gson.fromJson(json, new TypeToken<List<String>>(){}.getType());
        return list != null ? list : new ArrayList<>();
    }

    public void setEnabledModels(List<String> models) {
        storage.setTranscriptionEnabledModelsJson(gson.toJson(models));
    }

    public String getPrompt() {
        return storage.getTranscriptionPrompt();
    }

    public void setPrompt(String prompt) {
        storage.setTranscriptionPrompt(prompt);
    }

    public int getBatchSize() {
        return storage.getTranscriptionBatchSize();
    }

    public void setBatchSize(int size) {
        storage.setTranscriptionBatchSize(size);
    }
}
