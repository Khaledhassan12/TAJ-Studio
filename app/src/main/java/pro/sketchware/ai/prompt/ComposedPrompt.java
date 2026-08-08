package pro.sketchware.ai.prompt;

import java.util.List;

public class ComposedPrompt {
    public String systemText;
    public List<PromptAsset.Layer> layersIncluded;
    public int estimatedTokens;
    public boolean trimmed;

    public ComposedPrompt(String systemText, List<PromptAsset.Layer> layersIncluded, int estimatedTokens, boolean trimmed) {
        this.systemText = systemText;
        this.layersIncluded = layersIncluded;
        this.estimatedTokens = estimatedTokens;
        this.trimmed = trimmed;
    }
}
