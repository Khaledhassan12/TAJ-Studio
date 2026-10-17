package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import pro.sketchware.ai.agent.Tool;
import a.a.a.lC;
import java.util.HashMap;

public final class ProjectInfoTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            params.put("properties", new JSONObject().put("sc_id",
                    new JSONObject().put("type", "string")
                            .put("description", "Optional project ID. Omit it to inspect the project already bound to this agent task.")));
            return new ToolSpec("project_info", "Gets metadata for the active project bound to this agent task. Omit sc_id to inspect the currently open project; do not list every project just to discover the active one.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.PROJECT;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String sc_id = args.optString("sc_id", ctx.sc_id);
        if (sc_id == null || sc_id.trim().isEmpty()) {
            return ToolResult.structured("project_info", "NO_ACTIVE_PROJECT", null,
                    "No active project is bound to this agent task.", null, true);
        }
        HashMap<String, Object> map = lC.b(sc_id);
        if (map == null) return ToolResult.structured("project_info", "NOT_FOUND", null,
                "The active project metadata could not be found for the bound project ID.", null, true);
        JSONObject result = new JSONObject(map);
        try {
            result.put("agent_bound_project_id", ctx.sc_id);
            result.put("is_active_agent_project", sc_id.equals(ctx.sc_id));
        } catch (org.json.JSONException ignored) {}
        return new ToolResult(result.toString(), false);
    }
}
