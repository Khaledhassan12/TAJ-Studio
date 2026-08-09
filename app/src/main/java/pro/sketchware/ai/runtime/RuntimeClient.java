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

    private Callback activeCallback;

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

    private boolean handleReply(Message msg) {
        if (activeCallback == null) return false;
        Bundle b = msg.getData();
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
