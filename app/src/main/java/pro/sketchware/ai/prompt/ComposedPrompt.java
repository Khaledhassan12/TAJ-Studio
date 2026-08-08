package pro.sketchware.ai.prompt;

import java.util.List;

/**
 * [WHAT] The result of prompt composition.
 */
public class ComposedPrompt {
    public final String systemText;
    public final List<PromptAsset.Layer> layersIncluded;
    public final int estimatedTokens;
    public final boolean trimmed;

    public ComposedPrompt(String systemText, List<PromptAsset.Layer> layersIncluded, int estimatedTokens, boolean trimmed) {
        this.systemText = systemText;
        this.layersIncluded = layersIncluded;
        this.estimatedTokens = estimatedTokens;
        this.trimmed = trimmed;
    }
}
