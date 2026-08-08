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

    public interface Callback {
        void onToken(String token);
        void onDone();
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

    public void loadModel(String path, Callback cb) {
        if (!isBound) {
            cb.onError("Service not bound");
            return;
        }
        this.activeCallback = cb;
        Message msg = Message.obtain(null, LlamaRuntimeService.MSG_LOAD);
        Bundle data = new Bundle();
        data.putString("path", path);
        msg.setData(data);
        msg.replyTo = replyMessenger;
        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            cb.onError(e.getMessage());
        }
    }

    public void complete(String prompt, Callback cb) {
        if (!isBound) {
            cb.onError("Service not bound");
            return;
        }
        this.activeCallback = cb;
        Message msg = Message.obtain(null, LlamaRuntimeService.MSG_COMPLETE);
        Bundle data = new Bundle();
        data.putString("prompt", prompt);
        msg.setData(data);
        msg.replyTo = replyMessenger;
        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            cb.onError(e.getMessage());
        }
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
        switch (msg.what) {
            case LlamaRuntimeService.MSG_TOKEN:
                activeCallback.onToken((String) msg.obj);
                return true;
            case LlamaRuntimeService.MSG_DONE:
                activeCallback.onDone();
                return true;
            case LlamaRuntimeService.MSG_ERROR:
                activeCallback.onError((String) msg.obj);
                return true;
        }
        return false;
    }
}
