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

    // P2-CS2 (D10-amended): embedding handle is INDEPENDENT of the chat
    // handle — own RW-lock, own lifecycle (on-demand load, idle unload).
    private final ReentrantReadWriteLock embedLock = new ReentrantReadWriteLock();
    private volatile long embedHandle = 0;
    private volatile String embedModelPath = null;

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

    // --- Embedding surface (P2-CS2) ---

    /**
     * Loads (or reuses) the embedding model for {@code path}. Never touches
     * the chat handle.
     */
    public void loadEmbedModel(String path, int nCtx, int nThreads) throws Exception {
        embedLock.writeLock().lock();
        try {
            if (embedHandle != 0 && path.equals(embedModelPath)) return;
            unloadEmbedInternal();
            Log.d(TAG, "Loading embedding model: " + path);
            embedHandle = LlamaNative.nativeEmbedInit(path, nCtx, nThreads);
            if (embedHandle == 0) {
                throw new Exception("EMBED_LOAD_FAILED: " + path);
            }
            embedModelPath = path;
        } finally {
            embedLock.writeLock().unlock();
        }
    }

    /**
     * @return L2-normalized embedding of {@code text}.
     * @throws Exception with typed codes: EMBED_LOAD_FAILED / EMBED_OOM.
     */
    public float[] embed(String path, String text, int nCtx, int nThreads) throws Exception {
        loadEmbedModel(path, nCtx, nThreads);
        embedLock.readLock().lock();
        try {
            if (embedHandle == 0) throw new Exception("EMBED_LOAD_FAILED: no embedding handle");
            float[] vector = LlamaNative.nativeEmbed(embedHandle, text);
            if (vector == null) throw new Exception("EMBED_OOM: embedding produced no vector");
            return vector;
        } finally {
            embedLock.readLock().unlock();
        }
    }

    /** Frees the embedding handle (idle unload); chat handle untouched. */
    public void unloadEmbed() {
        embedLock.writeLock().lock();
        try {
            unloadEmbedInternal();
        } finally {
            embedLock.writeLock().unlock();
        }
    }

    private void unloadEmbedInternal() {
        if (embedHandle != 0) {
            Log.d(TAG, "Unloading embedding handle: " + embedHandle);
            LlamaNative.nativeEmbedFree(embedHandle);
            embedHandle = 0;
            embedModelPath = null;
        }
    }

    public boolean isEmbedLoaded() {
        return embedHandle != 0;
    }
}
