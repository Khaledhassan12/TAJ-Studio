package pro.sketchware.ai.hf;

public interface ProgressCallback {
    void onProgress(long bytes, long total);
}
