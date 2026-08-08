package pro.sketchware.ai.prompt;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import pro.sketchware.ai.context.ContextSnapshot;
import pro.sketchware.ai.context.ProjectContextManager;

/**
 * [WHAT] The ONLY place that assembles the final system prompt.
 * [WHY] Ensures consistent identity, rules, and context across all models.
 * [HOW] Gathers assets by priority, applies budget, and adapt to provider.
 */
public class SystemPromptManager {

    private final PromptLoader loader;

    public SystemPromptManager(Context context) {
        this.loader = new PromptLoader(context);
    }

    public ComposedPrompt compose(ComposeRequest req, int contextSize) {
        StringBuilder sb = new StringBuilder();
        List<PromptAsset.Layer> layers = new ArrayList<>();
        ContextBudget budget = new ContextBudget(contextSize);

        // 1. Identity (High Priority)
        String identity = loader.load(PromptAsset.IDENTITY);
        sb.append(identity).append("\n\n");
        layers.add(PromptAsset.Layer.IDENTITY);

        // 2. Engineering (High Priority)
        String engineering = loader.load(PromptAsset.ANDROID_ENGINEERING);
        sb.append(engineering).append("\n\n");
        layers.add(PromptAsset.Layer.ENGINEERING);

        // 3. Project Context
        ContextSnapshot snapshot = ProjectContextManager.snapshot(req.scId);
        String contextText = snapshot.toString();
        
        int currentTokens = TokenEstimator.estimate(sb.toString());
        int contextTokens = TokenEstimator.estimate(contextText);
        
        boolean trimmed = false;
        if (currentTokens + contextTokens > budget.getSystemLimit()) {
            // Trim context layer
            contextText = "# PROJECT CONTEXT (Trimmed)\nProject: " + snapshot.projectName;
            trimmed = true;
        }

        sb.append("# PROJECT CONTEXT\n").append(contextText).append("\n\n");
        layers.add(PromptAsset.Layer.CONTEXT);

        return new ComposedPrompt(sb.toString(), layers, TokenEstimator.estimate(sb.toString()), trimmed);
    }
}
