package pro.sketchware.ai.hf;

/**
 * [WHAT] Callback for reporting download progress.
 */
public interface ProgressCallback {
    void onProgress(long bytes, Long total);
}
