package pro.sketchware.ai.models;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import pro.sketchware.ai.bus.AiEventHub;
import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.data.Paths;
import pro.sketchware.ai.hf.CancelFlag;
import pro.sketchware.ai.hf.HfClient;
import pro.sketchware.ai.hf.HfModelSummary;
import pro.sketchware.ai.validate.GgufValidator;
import pro.sketchware.ai.validate.ValidationResult;

/**
 * [WHAT] Orchestrator for AI model lifecycle.
 * [WHY] Handles heavy operations (download, validate, I/O) off-thread and publishes via hub.
 * [HOW] Single-thread executor for consistency; integrates HfClient and GgufValidator.
 */
public class ModelManager {

    private final Context context;
    private final HfClient hfClient;
    private final AiStorage storage;
    private final AiEventHub hub;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static ModelManager instance;

    public static synchronized ModelManager get(Context context) {
        if (instance == null) instance = new ModelManager(context.getApplicationContext());
        return instance;
    }

    private ModelManager(Context context) {
        this.context = context;
        this.hfClient = new HfClient(context);
        this.storage = AiStorage.get(context);
        this.hub = AiEventHub.get();
    }

    public void search(String query) {
        hub.publish(AiEventHub.Event.SEARCHING, null);
        executor.execute(() -> {
            try {
                List<HfModelSummary> results = hfClient.searchModels(query, 20);
                hub.publish(AiEventHub.Event.SEARCH_DONE, results);
            } catch (IOException e) {
                hub.publish(AiEventHub.Event.ERROR, "Search failed: " + e.getMessage());
            }
        });
    }

    public List<AiModel> listLocal() {
        List<AiModel> models = new ArrayList<>();
        try (Cursor cursor = storage.listModels()) {
            while (cursor.moveToNext()) {
                AiModel m = new AiModel();
                m.id = cursor.getString(cursor.getColumnIndexOrThrow("id"));
                m.kind = AiModel.Kind.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("kind")));
                m.provider = cursor.getString(cursor.getColumnIndexOrThrow("provider"));
                m.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                m.filePath = cursor.getString(cursor.getColumnIndexOrThrow("filePath"));
                m.sizeBytes = new File(m.filePath).length();
                m.installedAt = cursor.getLong(cursor.getColumnIndexOrThrow("installedAt"));
                m.isActive = "true".equals(storage.kvGet("active_model_id_" + m.id)); // Simplified active check
                models.add(m);
            }
        }
        return models;
    }

    public void download(String repoId, String fileName) {
        executor.execute(() -> {
            String modelId = repoId.replace("/", "__") + "__" + fileName;
            File temp = Paths.tempDownloadFile(modelId);
            File dest = Paths.modelFile(modelId);

            try {
                String url = hfClient.resolveDownloadUrl(repoId, fileName);
                hfClient.downloadToFile(url, temp, (bytes, total) -> {
                    hub.publish(AiEventHub.Event.DOWNLOAD_PROGRESS, new long[]{bytes, total});
                }, new CancelFlag());

                ValidationResult vr = GgufValidator.validate(temp);
                if (vr.success) {
                    if (temp.renameTo(dest)) {
                        ContentValues cv = new ContentValues();
                        cv.put("id", modelId);
                        cv.put("kind", AiModel.Kind.LOCAL.name());
                        cv.put("provider", "huggingface");
                        cv.put("name", fileName);
                        cv.put("filePath", dest.getAbsolutePath());
                        cv.put("installedAt", System.currentTimeMillis());
                        storage.insertModel(cv);
                        hub.publish(AiEventHub.Event.DOWNLOAD_SUCCESS, modelId);
                        hub.publish(AiEventHub.Event.MODELS_CHANGED, null);
                    } else {
                        hub.publish(AiEventHub.Event.DOWNLOAD_FAILED, "Failed to finalize file");
                    }
                } else {
                    temp.delete();
                    hub.publish(AiEventHub.Event.DOWNLOAD_FAILED, "Invalid GGUF: " + vr.reason);
                }
            } catch (IOException e) {
                temp.delete();
                hub.publish(AiEventHub.Event.DOWNLOAD_FAILED, e.getMessage());
            }
        });
    }

    public void delete(String modelId) {
        executor.execute(() -> {
            try (Cursor c = storage.findModel(modelId)) {
                if (c.moveToFirst()) {
                    String path = c.getString(c.getColumnIndexOrThrow("filePath"));
                    new File(path).delete();
                    // storage.deleteModel(modelId); // Need to add to AiStorage
                    hub.publish(AiEventHub.Event.MODELS_CHANGED, null);
                }
            }
        });
    }
}
