package pro.sketchware.ai.prompt;

import pro.sketchware.R;

/**
 * [WHAT] Registry of system prompt assets.
 */
public enum PromptAsset {
    IDENTITY("identity", R.raw.taj_identity, Layer.IDENTITY, 100),
    ANDROID_ENGINEERING("engineering", R.raw.taj_android_engineering, Layer.ENGINEERING, 200),
    AGENT_PROTOCOL("agent_protocol", 0, Layer.AGENT, 300),
    TOOL_DEFINITIONS("tools", 0, Layer.TOOLS, 400),
    PROJECT_CONTEXT_PLACEHOLDER("context", 0, Layer.CONTEXT, 500);

    public enum Layer {
        IDENTITY, ENGINEERING, AGENT, TOOLS, CONTEXT, TASK
    }

    public final String id;
    public final int rawResId;
    public final Layer layer;
    public final int priority;

    PromptAsset(String id, int rawResId, Layer layer, int priority) {
        this.id = id;
        this.rawResId = rawResId;
        this.layer = layer;
        this.priority = priority;
    }
}
