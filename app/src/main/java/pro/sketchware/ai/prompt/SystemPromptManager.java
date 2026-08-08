package pro.sketchware.ai.prompt;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import pro.sketchware.ai.context.ContextSnapshot;
import pro.sketchware.ai.context.ProjectContextManager;

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
        
        // Budget check for Context
        int systemTokens = TokenEstimator.estimate(sb.toString());
        int contextTokens = TokenEstimator.estimate(contextText);
        
        if (systemTokens + contextTokens > budget.getSystemLimit()) {
            // Trim contextDetail before Engineering if needed
            contextText = "# Project Context (Trimmed)\n" + snapshot.projectName;
        }

        sb.append("# PROJECT CONTEXT\n").append(contextText).append("\n\n");
        layers.add(PromptAsset.Layer.CONTEXT);

        return new ComposedPrompt(sb.toString(), layers, TokenEstimator.estimate(sb.toString()), false);
    }
}
