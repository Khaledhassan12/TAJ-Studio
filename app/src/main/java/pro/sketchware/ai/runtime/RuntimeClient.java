package pro.sketchware.ai.runtime;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import pro.sketchware.ai.core.AiResponse;

/**
 * [WHAT] Client for LlamaRuntimeService.
 * [WHY] Simplifies binding and IPC for the main process.
 * [HOW] Manages ServiceConnection and provides async methods with callbacks.
 */
public class RuntimeClient {

    private static final String TAG = "RuntimeClient";
    private final Context context;
    private Messenger serviceMessenger;
    private boolean isBound = false;
    private final Messenger replyMessenger;
    private String loadedModelPath = null;

    public interface Callback {
        void onToken(String token);
        default void onThought(String thought) {}
        void onDone(AiResponse usage);
        void onError(String error);
    }

    /** P2-CS2: embedding RPC callback (vectors arrive as float[][], main thread). */
    public interface EmbedCallback {
        void onVectors(float[][] vectors);
        void onError(String error);
    }

    private Callback activeCallback;
    private EmbedCallback activeEmbedCallback;
    private final Object boundLock = new Object();

    public RuntimeClient(Context context) {
        this.context = context.getApplicationContext();
        this.replyMessenger = new Messenger(new Handler(Looper.getMainLooper(), this::handleReply));
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            serviceMessenger = new Messenger(service);
            isBound = true;
            Log.d(TAG, "Service connected");
            synchronized (boundLock) {
                boundLock.notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceMessenger = null;
            isBound = false;
            loadedModelPath = null;
            Log.d(TAG, "Service disconnected");
        }
    };

    public void bind() {
        if (!isBound) {
            Intent intent = new Intent(context, LlamaRuntimeService.class);
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        }
    }

    public void unbind() {
        if (isBound) {
            context.unbindService(connection);
            isBound = false;
        }
    }

    public void loadModel(String path, int nCtx, String mmprojPath, Callback cb) {
        if (!isBound) {
            cb.onError("Service not bound");
            return;
        }
        this.activeCallback = cb;
        this.loadedModelPath = path;
        Message msg = Message.obtain(null, LlamaRuntimeService.MSG_LOAD);
        Bundle data = new Bundle();
        data.putString("path", path);
        data.putInt("nCtx", nCtx);
        if (mmprojPath != null) data.putString("mmprojPath", mmprojPath);
        msg.setData(data);
        msg.replyTo = replyMessenger;
        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            cb.onError(e.getMessage());
        }
    }

    public void loadModel(String path, int nCtx, Callback cb) {
        loadModel(path, nCtx, null, cb);
    }

    public void complete(String prompt, float temp, float topP, int maxTokens, Callback cb) {
        if (!isBound) {
            cb.onError("Service not bound");
            return;
        }
        this.activeCallback = cb;
        Message msg = Message.obtain(null, LlamaRuntimeService.MSG_COMPLETE);
        Bundle data = new Bundle();
        data.putString("prompt", prompt);
        data.putFloat("temperature", temp);
        data.putFloat("topP", topP);
        data.putInt("maxTokens", maxTokens);
        msg.setData(data);
        msg.replyTo = replyMessenger;
        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            cb.onError(e.getMessage());
        }
    }

    public void complete(String[] roles, String[] contents, float temp, float topP, int maxTokens, Callback cb) {
        if (!isBound) {
            cb.onError("Service not bound");
            return;
        }
        this.activeCallback = cb;
        Message msg = Message.obtain(null, LlamaRuntimeService.MSG_COMPLETE);
        Bundle data = new Bundle();
        data.putStringArray("roles", roles);
        data.putStringArray("contents", contents);
        data.putFloat("temperature", temp);
        data.putFloat("topP", topP);
        data.putInt("maxTokens", maxTokens);
        msg.setData(data);
        msg.replyTo = replyMessenger;
        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            cb.onError(e.getMessage());
        }
    }

    public void ensureModelAndComplete(String modelPath, int nCtx, String mmprojPath, String prompt, 
                                     float temp, float topP, int maxTokens, Callback cb) {
        if (modelPath.equals(loadedModelPath)) {
            complete(prompt, temp, topP, maxTokens, cb);
        } else {
            loadModel(modelPath, nCtx, mmprojPath, new Callback() {
                @Override public void onToken(String token) {}
                @Override public void onDone(AiResponse usage) { complete(prompt, temp, topP, maxTokens, cb); }
                @Override public void onError(String error) { cb.onError(error); }
            });
        }
    }

    public void ensureModelAndComplete(String modelPath, int nCtx, String mmprojPath, String[] roles, String[] contents,
                                     float temp, float topP, int maxTokens, Callback cb) {
        if (modelPath.equals(loadedModelPath)) {
            complete(roles, contents, temp, topP, maxTokens, cb);
        } else {
            loadModel(modelPath, nCtx, mmprojPath, new Callback() {
                @Override public void onToken(String token) {}
                @Override public void onDone(AiResponse usage) { complete(roles, contents, temp, topP, maxTokens, cb); }
                @Override public void onError(String error) { cb.onError(error); }
            });
        }
    }

    public void ensureModelAndComplete(String modelPath, int nCtx, String prompt, 
                                     float temp, float topP, int maxTokens, Callback cb) {
        ensureModelAndComplete(modelPath, nCtx, null, prompt, temp, topP, maxTokens, cb);
    }

    public void cancel() {
        if (isBound) {
            try {
                serviceMessenger.send(Message.obtain(null, LlamaRuntimeService.MSG_CANCEL));
            } catch (RemoteException ignored) {}
        }
    }

    // --- Embedding RPC (P2-CS2) ---

    /**
     * Blocks (off-main-thread callers only) until the service is bound.
     *
     * @return true if bound within the timeout.
     */
    public boolean ensureBound(long timeoutMs) {
        if (isBound) return true;
        bind();
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (boundLock) {
            while (!isBound) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) return false;
                try {
                    boundLock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Embeds {@code texts} with the GGUF model at {@code modelPath} via
     * :ai_runtime. On-demand load + idle unload are handled by the service.
     */
    public void embed(String modelPath, String[] texts, EmbedCallback cb) {
        if (!isBound) {
            cb.onError("EMBED_LOAD_FAILED: runtime service not bound");
            return;
        }
        this.activeEmbedCallback = cb;
        Message msg = Message.obtain(null, LlamaRuntimeService.MSG_EMBED);
        Bundle data = new Bundle();
        data.putString("path", modelPath);
        data.putStringArray("texts", texts);
        msg.setData(data);
        msg.replyTo = replyMessenger;
        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            cb.onError("EMBED_LOAD_FAILED: " + e.getMessage());
        }
    }

    /** Explicitly releases the embedding handle (idle unload also covers this). */
    public void unloadEmbed() {
        if (isBound) {
            try {
                serviceMessenger.send(Message.obtain(null, LlamaRuntimeService.MSG_EMBED_UNLOAD));
            } catch (RemoteException ignored) {}
        }
    }

    private boolean handleReply(Message msg) {
        Bundle b = msg.getData();
        // P2-CS2: embedding replies are routed independently of the chat
        // callback so the two flows can never clobber each other.
        if (msg.what == LlamaRuntimeService.MSG_EMBED_RESULT) {
            if (activeEmbedCallback != null) {
                float[] flat = b.getFloatArray("vectors");
                int dim = b.getInt("dim");
                int count = b.getInt("count");
                if (flat == null || dim <= 0 || count <= 0) {
                    activeEmbedCallback.onError("EMBED_OOM: empty embedding reply");
                } else {
                    float[][] vectors = new float[count][dim];
                    for (int i = 0; i < count; i++) {
                        System.arraycopy(flat, i * dim, vectors[i], 0, dim);
                    }
                    activeEmbedCallback.onVectors(vectors);
                }
            }
            return true;
        }
        if (msg.what == LlamaRuntimeService.MSG_ERROR) {
            String err = b.getString("text");
            if (err != null && err.startsWith("EMBED_") && activeEmbedCallback != null) {
                activeEmbedCallback.onError(err);
                return true;
            }
        }
        if (activeCallback == null) return false;
        switch (msg.what) {
            case LlamaRuntimeService.MSG_TEXT:
                activeCallback.onToken(b.getString("text"));
                return true;
            case LlamaRuntimeService.MSG_THOUGHT:
                activeCallback.onThought(b.getString("text"));
                return true;
            case LlamaRuntimeService.MSG_DONE:
                AiResponse usage = new AiResponse("", "stop", 0, b.getInt("value"));
                activeCallback.onDone(usage);
                return true;
            case LlamaRuntimeService.MSG_ERROR:
                String err = b.getString("text");
                if (err != null && err.startsWith("LOCAL_CONTEXT_EXCEEDED:")) {
                    activeCallback.onError(mapContextError(err));
                } else {
                    activeCallback.onError(err);
                }
                return true;
        }
        return false;
    }

    private String mapContextError(String raw) {
        try {
            String[] parts = raw.split(":");
            if (parts.length >= 3) {
                return "Local context exceeded (Prompt: " + parts[1] + ", Model limit: " + parts[2] + " tokens). Try reducing history or increasing context size.";
            }
        } catch (Exception ignored) {}
        return "Local context limit exceeded. Please shorten your prompt or history.";
    }
}
