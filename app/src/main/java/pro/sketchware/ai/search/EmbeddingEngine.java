package pro.sketchware.ai.search;

import java.util.List;

/**
 * [WHAT] Abstraction over embedding providers (P2-CS2).
 * [WHY] The indexing pipeline and semantic search must select an engine by
 * config.type without caring whether vectors come from an HTTP endpoint or
 * from the local GGUF runtime in :ai_runtime.
 * [HOW] Implementations throw Exception with honest, typed messages.
 */
public interface EmbeddingEngine {

    /**
     * @param texts non-empty list of texts to embed.
     * @return one L2-usable vector per input text, in input order.
     * @throws Exception with an honest, user-surfaceable message.
     */
    List<float[]> embed(List<String> texts) throws Exception;
}
