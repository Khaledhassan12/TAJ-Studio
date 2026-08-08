package pro.sketchware.ai.hf;

import java.util.List;

/**
 * [WHAT] Summary of a HuggingFace model.
 * [WHY] Used for display in search results.
 */
public class HfModelSummary {
    public final String id;
    public final int downloads;
    public final int likes;
    public final List<String> tags;
    public final String pipelineTag;

    public HfModelSummary(String id, int downloads, int likes, List<String> tags, String pipelineTag) {
        this.id = id;
        this.downloads = downloads;
        this.likes = likes;
        this.tags = tags;
        this.pipelineTag = pipelineTag;
    }
}
