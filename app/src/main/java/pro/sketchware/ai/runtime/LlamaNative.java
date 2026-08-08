package pro.sketchware.ai.runtime;

import android.util.Log;

/**
 * [WHAT] JNI bridge for llama.cpp.
 * [WHY] Low-level access to the native LLM engine.
 * [HOW] Exposes native methods for model lifecycle and inference.
 */
public class LlamaNative {

    static {
        try {
            System.loadLibrary("llama-jni");
        } catch (UnsatisfiedLinkError e) {
            Log.e("LlamaNative", "Failed to load llama-jni library", e);
        }
    }

    public static native long nativeLoad(String path, int nCtx, int nThreads);

    public static native void nativeComplete(long handle, String prompt, TokenCallback callback);

    public static native void nativeCancel(long handle);

    public static native void nativeFree(long handle);

    public interface TokenCallback {
        /**
         * @param token The generated token string.
         * @return true to continue, false to abort.
         */
        boolean onToken(String token);
    }
}
