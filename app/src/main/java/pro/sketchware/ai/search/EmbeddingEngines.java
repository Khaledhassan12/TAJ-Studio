package pro.sketchware.ai.search;

import android.content.Context;

/**
 * [WHAT] Engine factory + shared vector math for Conversation Search (P2-CS2).
 * [WHY] Indexing pipeline and semantic search select the engine by
 * config.type (single decision point, R5); cosine + threshold unchanged.
 */
public class EmbeddingEngines {

    private EmbeddingEngines() {}

    /** Selects the engine by model type: remote (HTTP) or local (RPC). */
    public static EmbeddingEngine forModel(Context context, ConversationSearchSettings.EmbeddingModel model) {
        if ("local".equals(model.type)) {
            return new LocalEmbeddingEngine(context, model);
        }
        String key = ConversationSearchSettings.get(context).getRemoteKey(model.id);
        return new RemoteEmbeddingEngine(model, key);
    }

    /** Maps typed runtime error codes to honest, user-readable text (R7). */
    public static String honestError(String code) {
        if (code == null) return "Embedding failed";
        if (code.startsWith("EMBED_NOT_EMBEDDING_MODEL")) {
            return "This GGUF is not an embedding model";
        }
        if (code.startsWith("EMBED_OOM")) {
            return "Out of memory while creating the embedding — try a smaller model";
        }
        if (code.startsWith("EMBED_LOAD_FAILED")) {
            return "Embedding model failed to load (unreadable or invalid GGUF file)";
        }
        return code;
    }

    /** Full cosine similarity (works for both normalized and raw vectors). */
    public static float cosine(float[] a, float[] b) {
        if (a == null || b == null) return 0f;
        int n = Math.min(a.length, b.length);
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < n; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0f;
        return (float) (dot / (Math.sqrt(na) * Math.sqrt(nb)));
    }
}
