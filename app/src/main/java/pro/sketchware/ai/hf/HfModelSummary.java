package pro.sketchware.ai.hf;

import java.util.List;

public class HfModelSummary {
    public String id;
    public int downloads;
    public int likes;
    public List<String> tags;

    public HfModelSummary(String id, int downloads, int likes, List<String> tags) {
        this.id = id;
        this.downloads = downloads;
        this.likes = likes;
        this.tags = tags;
    }
}
