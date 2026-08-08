package pro.sketchware.ai.runtime;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import java.io.File;

/**
 * [WHAT] Bound service running in isolated process :ai_runtime.
 * [WHY] Contains native crashes and separates LLM memory from main app.
 * [HOW] Uses Messenger for IPC. Handles LOAD, COMPLETE, CANCEL, UNLOAD.
 */
public class LlamaRuntimeService extends Service {

    private static final String TAG = "LlamaRuntimeService";

    public static final int MSG_LOAD = 1;
    public static final int MSG_COMPLETE = 2;
    public static final int MSG_CANCEL = 3;
    public static final int MSG_UNLOAD = 4;

    public static final int MSG_TOKEN = 10;
    public static final int MSG_DONE = 11;
    public static final int MSG_ERROR = 12;

    private final LlamaRuntime runtime = new LlamaRuntime();
    private Messenger messenger;
    private HandlerThread workerThread;
    private Handler workerHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        workerThread = new HandlerThread("LlamaWorker");
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper(), this::handleWorkerMessage);
        messenger = new Messenger(workerHandler);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    @Override
    public void onDestroy() {
        runtime.unload();
        workerThread.quitSafely();
        super.onDestroy();
    }

    private boolean handleWorkerMessage(Message msg) {
        switch (msg.what) {
            case MSG_LOAD:
                handleLoad(msg);
                return true;
            case MSG_COMPLETE:
                handleComplete(msg);
                return true;
            case MSG_CANCEL:
                runtime.cancel();
                return true;
            case MSG_UNLOAD:
                runtime.unload();
                return true;
        }
        return false;
    }

    private void handleLoad(Message msg) {
        Bundle data = msg.getData();
        String path = data.getString("path");
        int nCtx = data.getInt("nCtx", 2048);
        int nThreads = data.getInt("nThreads", 4);
        Messenger replyTo = msg.replyTo;

        try {
            runtime.loadModel(new File(path), nCtx, nThreads);
            sendReply(replyTo, MSG_DONE, null);
        } catch (Exception e) {
            sendReply(replyTo, MSG_ERROR, e.getMessage());
        }
    }

    private void handleComplete(Message msg) {
        Bundle data = msg.getData();
        String prompt = data.getString("prompt");
        Messenger replyTo = msg.replyTo;

        try {
            runtime.complete(prompt, token -> {
                sendReply(replyTo, MSG_TOKEN, token);
                return true; // Continue
            });
            sendReply(replyTo, MSG_DONE, null);
        } catch (Exception e) {
            sendReply(replyTo, MSG_ERROR, e.getMessage());
        }
    }

    private void sendReply(Messenger replyTo, int what, String obj) {
        if (replyTo == null) return;
        try {
            Message reply = Message.obtain(null, what);
            reply.obj = obj;
            replyTo.send(reply);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to send reply", e);
        }
    }
}
