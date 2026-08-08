package pro.sketchware.ai.download;

import android.content.ContentValues;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.data.Paths;
import pro.sketchware.ai.hf.CancelFlag;
import pro.sketchware.ai.hf.HfClient;
import pro.sketchware.ai.validate.GgufInfo;
import pro.sketchware.ai.validate.GgufValidator;

/**
 * [WHAT] Orchestrator for downloading and validating AI models.
 * [WHY] Ensures models are atomic and verified before listed (RISK-2).
 * [HOW] Uses a single-thread executor for background work.
 */
public class ModelDownloader {

    public interface DownloadListener {
        void onProgress(String modelId, long bytes, Long total);
        void onStateChange(String modelId, String state, String error);
    }

    private final HfClient hfClient;
    private final AiStorage storage;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CancelFlag cancelFlag = new CancelFlag();

    public ModelDownloader(Context context) {
        this.hfClient = new HfClient(context);
        this.storage = AiStorage.get(context);
    }

    public void download(String repoId, String fileName, String modelId, DownloadListener listener) {
        executor.execute(() -> {
            postState(listener, modelId, "DOWNLOADING", null);
            File part = Paths.tempDownloadFile(modelId);
            File dest = Paths.modelFile(modelId);

            try {
                String url = hfClient.resolveDownloadUrl(repoId, fileName);
                hfClient.downloadToFile(url, part, (bytes, total) -> {
                    mainHandler.post(() -> listener.onProgress(modelId, bytes, total));
                    updateProgressInStorage(modelId, bytes, total);
                }, cancelFlag);

                postState(listener, modelId, "VALIDATING", null);
                GgufInfo info = GgufValidator.validate(part);
                if (info.valid) {
                    if (part.renameTo(dest)) {
                        finalizeSuccess(modelId, fileName, dest.getAbsolutePath());
                        postState(listener, modelId, "DONE", null);
                    } else {
                        throw new Exception("Failed to rename .part file");
                    }
                } else {
                    throw new Exception("Invalid GGUF: " + info.error);
                }
            } catch (Exception e) {
                part.delete();
                postState(listener, modelId, "FAILED", e.getMessage());
                updateErrorInStorage(modelId, e.getMessage());
            }
        });
    }

    public void cancel() {
        cancelFlag.cancel();
    }

    private void postState(DownloadListener l, String id, String state, String error) {
        mainHandler.post(() -> l.onStateChange(id, state, error));
    }

    private void updateProgressInStorage(String id, long bytes, Long total) {
        ContentValues cv = new ContentValues();
        cv.put("bytesDownloaded", bytes);
        if (total != null) cv.put("totalBytes", total);
        cv.put("updatedAt", System.currentTimeMillis());
        storage.updateDownload(id, cv);
    }

    private void updateErrorInStorage(String id, String error) {
        ContentValues cv = new ContentValues();
        cv.put("state", "FAILED");
        cv.put("errorMsg", error);
        cv.put("updatedAt", System.currentTimeMillis());
        storage.updateDownload(id, cv);
    }

    private void finalizeSuccess(String id, String name, String path) {
        ContentValues cv = new ContentValues();
        cv.put("id", id);
        cv.put("kind", "LOCAL");
        cv.put("provider", "huggingface");
        cv.put("name", name);
        cv.put("filePath", path);
        cv.put("installedAt", System.currentTimeMillis());
        storage.insertModel(cv);

        ContentValues dv = new ContentValues();
        dv.put("state", "DONE");
        dv.put("updatedAt", System.currentTimeMillis());
        storage.updateDownload(id, dv);
    }
}
