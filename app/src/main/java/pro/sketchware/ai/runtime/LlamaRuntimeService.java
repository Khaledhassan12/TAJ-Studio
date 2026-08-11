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
import pro.sketchware.ai.core.AiResponse;

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
    // P2-CS2: embedding RPC (independent of chat handle).
    public static final int MSG_EMBED = 5;
    public static final int MSG_EMBED_UNLOAD = 6;

    // Change 3: Reply constants (Bundle-only) - EXACT AGORA PORT
    public static final int MSG_TEXT = 1;
    public static final int MSG_THOUGHT = 2;
    public static final int MSG_USAGE = 3;
    public static final int MSG_ERROR = 4;
    public static final int MSG_DONE = 5;
    // P2-CS2: embedding reply (Bundle carries float[] ONLY — RISK-13).
    public static final int MSG_EMBED_RESULT = 6;

    /** Idle-unload delay for the embedding handle to protect memory (RISK-19). */
    private static final long EMBED_IDLE_UNLOAD_MS = 60_000L;

    private final LlamaRuntime runtime = new LlamaRuntime();
    private Messenger messenger;
    private HandlerThread workerThread;
    private Handler workerHandler;

    private String loadedModelPath = null;
    private final java.util.concurrent.locks.ReentrantLock samplingLock = new java.util.concurrent.locks.ReentrantLock();
    private final Runnable embedIdleUnload = () -> {
        Log.d(TAG, "Embedding idle-unload after " + EMBED_IDLE_UNLOAD_MS + "ms");
        runtime.unloadEmbed();
    };

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
        workerHandler.removeCallbacks(embedIdleUnload);
        runtime.unloadEmbed();
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
                loadedModelPath = null;
                return true;
            case MSG_EMBED:
                handleEmbed(msg);
                return true;
            case MSG_EMBED_UNLOAD:
                workerHandler.removeCallbacks(embedIdleUnload);
                runtime.unloadEmbed();
                return true;
        }
        return false;
    }

    /**
     * P2-CS2: embed RPC. Bundle in: path, texts (String[]), nCtx, nThreads.
     * Bundle out: MSG_EMBED_RESULT with flattened float[] "vectors" + "dim" +
     * "count" (primitive-safe, RISK-13), or MSG_ERROR with typed codes
     * (EMBED_LOAD_FAILED / EMBED_OOM / EMBED_NOT_EMBEDDING_MODEL).
     */
    private void handleEmbed(Message msg) {
        Bundle data = msg.getData();
        String path = data.getString("path");
        String[] texts = data.getStringArray("texts");
        int nCtx = data.getInt("nCtx", 512);
        int nThreads = data.getInt("nThreads", 4);
        Messenger replyTo = msg.replyTo;

        if (path == null || texts == null || texts.length == 0) {
            sendReply(replyTo, MSG_ERROR, "EMBED_LOAD_FAILED: missing path or texts", 0);
            return;
        }

        // Reset the idle-unload timer on every request (RISK-19).
        workerHandler.removeCallbacks(embedIdleUnload);

        try {
            float[][] result = new float[texts.length][];
            for (int i = 0; i < texts.length; i++) {
                result[i] = runtime.embed(path, texts[i] != null ? texts[i] : "", nCtx, nThreads);
            }

            int dim = result[0].length;
            float[] flat = new float[result.length * dim];
            for (int i = 0; i < result.length; i++) {
                System.arraycopy(result[i], 0, flat, i * dim, dim);
            }

            Message reply = Message.obtain();
            reply.what = MSG_EMBED_RESULT;
            Bundle out = new Bundle();
            out.putFloatArray("vectors", flat);
            out.putInt("dim", dim);
            out.putInt("count", result.length);
            reply.setData(out);
            try {
                if (replyTo != null) replyTo.send(reply);
            } catch (RemoteException e) {
                Log.w(TAG, "embed reply target gone");
            }
        } catch (OutOfMemoryError oom) {
            runtime.unloadEmbed();
            sendReply(replyTo, MSG_ERROR, "EMBED_OOM: " + oom.getMessage(), 0);
        } catch (Exception e) {
            sendReply(replyTo, MSG_ERROR, e.getMessage() != null ? e.getMessage() : "EMBED_LOAD_FAILED: unknown", 0);
        } finally {
            workerHandler.postDelayed(embedIdleUnload, EMBED_IDLE_UNLOAD_MS);
        }
    }

    private void handleLoad(Message msg) {
        Bundle data = msg.getData();
        String path = data.getString("path");
        int nCtx = data.getInt("nCtx", 2048);
        int nThreads = data.getInt("nThreads", 4);
        String mmprojPath = data.getString("mmprojPath");
        Messenger replyTo = msg.replyTo;

        try {
            if (path != null && path.equals(loadedModelPath) && runtime.isLoaded()) {
                Log.d(TAG, "Model already loaded, resetting context instead of reloading");
                runtime.reset();
            } else {
                runtime.loadModel(new File(path), nCtx, nThreads, mmprojPath);
                loadedModelPath = path;
            }

            // Sync mmproj if needed (Change 2)
            if (mmprojPath != null) {
                if (!runtime.hasMmproj()) runtime.loadMmproj(mmprojPath);
            } else {
                if (runtime.hasMmproj()) runtime.unloadMmproj();
            }

            sendReply(replyTo, MSG_DONE, null, 0);
        } catch (Exception e) {
            loadedModelPath = null;
            sendReply(replyTo, MSG_ERROR, e.getMessage(), 0);
        }
    }

    private void handleComplete(Message msg) {
        Bundle data = msg.getData();
        String prompt = data.getString("prompt");
        String[] roles = data.getStringArray("roles");
        String[] contents = data.getStringArray("contents");
        float temp = data.getFloat("temperature", 0.7f);
        float topP = data.getFloat("topP", 0.9f);
        int maxTokens = data.getInt("maxTokens", 4096);
        Messenger replyTo = msg.replyTo;

        try {
            String finalPrompt = prompt;
            if (roles != null && contents != null) {
                finalPrompt = runtime.applyTemplate(roles, contents, true);
                if (finalPrompt == null) {
                    finalPrompt = buildChatMlFallback(roles, contents);
                }
            }

            samplingLock.lockInterruptibly();
            try {
                ThinkingParser parser = new ThinkingParser(new ThinkingParser.Listener() {
                    @Override public void onText(String text) { sendReply(replyTo, MSG_TEXT, text, 0); }
                    @Override public void onThought(String thought, String titleOrNull) { sendReply(replyTo, MSG_THOUGHT, thought, 0); }
                });

                TokenSanitizer sanitizer = new TokenSanitizer(new TokenSanitizer.Listener() {
                    @Override public void onToken(String token) { parser.feed(token); }
                    @Override public void onStopSignalled() { runtime.cancel(); }
                });

                final int[] tokensCount = {0};
                runtime.complete(finalPrompt, temp, topP, maxTokens, token -> {
                    tokensCount[0]++;
                    sanitizer.feed(token);
                    return true;
                });
                sanitizer.flush();
                parser.flush();
                
                sendReply(replyTo, MSG_DONE, null, tokensCount[0]);
            } finally {
                samplingLock.unlock();
            }
        } catch (Exception e) {
            sendReply(replyTo, MSG_ERROR, e.getMessage(), 0);
        }
    }

    private String buildChatMlFallback(String[] roles, String[] contents) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < roles.length; i++) {
            sb.append("<|im_start|>").append(roles[i]).append("\n")
              .append(contents[i]).append("<|im_end|>\n");
        }
        sb.append("<|im_start|>assistant\n");
        return sb.toString();
    }

    private void sendReply(Messenger client, int what, String text, int value) {
        if (client == null) return;
        Message m = Message.obtain();
        m.what = what;
        Bundle b = new Bundle();
        if (text != null) b.putString("text", text);
        b.putInt("value", value);
        m.setData(b);
        try {
            client.send(m);
        } catch (RemoteException e) {
            Log.w(TAG, "reply target gone");
        }
    }
}
