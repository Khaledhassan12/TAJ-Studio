package pro.sketchware.ai.hf;

/**
 * [WHAT] Metadata for a file in a HuggingFace repository.
 * [WHY] Used to identify GGUF files for download.
 */
public class HfFile {
    public final String path;
    public final long size;

    public HfFile(String path, long size) {
        this.path = path;
        this.size = size;
    }
}
