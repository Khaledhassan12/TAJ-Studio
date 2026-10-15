package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.live.LiveRegistry;

public final class RefreshUiTool implements Tool {

    private static JSONObject emptyObjectSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            schema.put("properties", new JSONObject());
        } catch (org.json.JSONException ignored) {}
        return schema;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec("refresh_ui", "Triggers a full UI refresh across all active domains.", emptyObjectSchema());
    }

    @Override
    public Domain domain() {
        return Domain.PROJECT;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        LiveRegistry.notifyChanged("layout", "");
        LiveRegistry.notifyChanged("values", "");
        LiveRegistry.notifyChanged("project_java", "");
        return new ToolResult("UI refresh triggered.", false);
    }
}
