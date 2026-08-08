package pro.sketchware.ai.runtime;

import android.util.Log;
import java.io.File;

/**
 * [WHAT] High-level wrapper for LlamaNative.
 * [WHY] Manages model handle lifecycle and ensures thread-safe inference.
 * [HOW] Holds the native handle and provides clean load/complete/unload methods.
 */
public class LlamaRuntime {

    private static final String TAG = "LlamaRuntime";
    private long handle = 0;

    public synchronized void loadModel(File file, int nCtx, int nThreads) throws Exception {
        if (handle != 0) unload();
        
        Log.d(TAG, "Loading model: " + file.getAbsolutePath());
        handle = LlamaNative.nativeLoad(file.getAbsolutePath(), nCtx, nThreads);
        
        if (handle == 0) {
            throw new Exception("Failed to load native model handle");
        }
    }

    public synchronized void complete(String prompt, LlamaNative.TokenCallback callback) throws Exception {
        if (handle == 0) throw new Exception("Model not loaded");
        LlamaNative.nativeComplete(handle, prompt, callback);
    }

    public synchronized void cancel() {
        if (handle != 0) {
            LlamaNative.nativeCancel(handle);
        }
    }

    public synchronized void unload() {
        if (handle != 0) {
            Log.d(TAG, "Unloading model handle: " + handle);
            LlamaNative.nativeFree(handle);
            handle = 0;
        }
    }

    public boolean isLoaded() {
        return handle != 0;
    }
}
