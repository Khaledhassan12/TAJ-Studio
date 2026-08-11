package pro.sketchware.ai.search;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import pro.sketchware.ai.runtime.RuntimeClient;

/**
 * [WHAT] Local GGUF embedding engine (P2-CS2, D10-amended).
 * [WHY] Routes embed requests through :ai_runtime via the Messenger RPC
 * (float[] payloads only — RISK-13); on-demand load + 60s idle unload live
 * in the service (RISK-19).
 * [HOW] Blocking wrapper around RuntimeClient.embed for background-thread
 * callers (indexing pipeline, semantic search, import test gate).
 */
public class LocalEmbeddingEngine implements EmbeddingEngine {

    private static final long BIND_TIMEOUT_MS = 8_000L;
    private static final long EMBED_TIMEOUT_MS = 120_000L;

    private static RuntimeClient sharedClient;

    private final RuntimeClient client;
    private final ConversationSearchSettings.EmbeddingModel model;

    public LocalEmbeddingEngine(Context context, ConversationSearchSettings.EmbeddingModel model) {
        this.client = client(context);
        this.model = model;
    }

    /** Shared bound client so repeated embeds reuse one service connection. */
    private static synchronized RuntimeClient client(Context context) {
        if (sharedClient == null) {
            sharedClient = new RuntimeClient(context.getApplicationContext());
        }
        return sharedClient;
    }

    @Override
    public List<float[]> embed(List<String> texts) throws Exception {
        if (model.localPath == null || model.localPath.isEmpty()) {
            throw new Exception("Local embedding model '" + model.name + "' has no file path");
        }

        if (!client.ensureBound(BIND_TIMEOUT_MS)) {
            throw new Exception("EMBED_LOAD_FAILED: could not connect to the local AI runtime");
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final float[][][] holder = new float[1][][];
        final String[] error = new String[1];

        client.embed(model.localPath, texts.toArray(new String[0]), new RuntimeClient.EmbedCallback() {
            @Override
            public void onVectors(float[][] vectors) {
                holder[0] = vectors;
                latch.countDown();
            }

            @Override
            public void onError(String err) {
                error[0] = err;
                latch.countDown();
            }
        });

        if (!latch.await(EMBED_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw new Exception("Local embedding timed out after " + (EMBED_TIMEOUT_MS / 1000) + "s");
        }
        if (error[0] != null) {
            throw new Exception(EmbeddingEngines.honestError(error[0]));
        }

        return new ArrayList<>(Arrays.asList(holder[0]));
    }
}
