package pro.sketchware.ai.runtime;

import android.util.Log;
import java.io.File;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * [WHAT] High-level wrapper for LlamaNative with RW-lock handle discipline.
 * [WHY] Ensures thread-safe native handle access and prevents use-after-free (Change 1).
 * [HOW] Holds a @Volatile handle guarded by ReentrantReadWriteLock.
 */
public class LlamaRuntime {

    private static final String TAG = "LlamaRuntime";
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile long handle = 0;

    public void loadModel(File file, int nCtx, int nThreads) throws Exception {
        loadModel(file, nCtx, nThreads, null);
    }

    public void loadModel(File file, int nCtx, int nThreads, String mmprojPath) throws Exception {
        lock.writeLock().lock();
        try {
            if (handle != 0) freeInternal();
            Log.d(TAG, "Loading model: " + file.getAbsolutePath());
            handle = LlamaNative.nativeLoad(file.getAbsolutePath(), nCtx, nThreads, mmprojPath);
            if (handle == 0) throw new Exception("Failed to load native model handle");
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void complete(String prompt, float temperature, float topP, int maxTokens,
                        LlamaNative.TokenCallback callback) throws Exception {
        lock.readLock().lock();
        try {
            if (handle == 0) throw new Exception("Model not loaded");
            LlamaNative.nativeComplete(handle, prompt, temperature, topP, maxTokens, callback);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void cancel() {
        lock.readLock().lock();
        try {
            if (handle != 0) LlamaNative.nativeCancel(handle);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void reset() {
        lock.readLock().lock();
        try {
            if (handle != 0) LlamaNative.nativeReset(handle);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void close() {
        cancel();
        lock.writeLock().lock();
        try {
            freeInternal();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void unload() {
        close();
    }

    public boolean loadMmproj(String path) {
        lock.readLock().lock();
        try {
            return handle != 0 && LlamaNative.nativeLoadMmproj(handle, path);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void unloadMmproj() {
        lock.readLock().lock();
        try {
            if (handle != 0) LlamaNative.nativeUnloadMmproj(handle);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean hasMmproj() {
        lock.readLock().lock();
        try {
            return handle != 0 && LlamaNative.nativeHasMmproj(handle);
        } finally {
            lock.readLock().unlock();
        }
    }

    public String applyTemplate(String[] roles, String[] contents, boolean addAssistant) {
        lock.readLock().lock();
        try {
            if (handle == 0) return null;
            return LlamaNative.nativeApplyTemplate(handle, roles, contents, addAssistant);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void freeInternal() {
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
