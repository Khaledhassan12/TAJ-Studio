package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;

/**
 * [WHAT] Arguments passed to a tool.
 */
public class ToolArgs {
    private final JSONObject args;

    public ToolArgs(String json) throws Exception {
        this.args = new JSONObject(json);
    }

    public String getString(String key) {
        return args.optString(key, null);
    }
    
    public String getString(String key, String def) {
        return args.optString(key, def);
    }

    public boolean getBoolean(String key, boolean def) {
        return args.optBoolean(key, def);
    }
}
