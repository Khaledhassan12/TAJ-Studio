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
            params.put("properties", new JSONObject().put("sc_id", new JSONObject().put("type", "string")));
            params.put("required", new org.json.JSONArray().put("sc_id"));
            return new ToolSpec("project_info", "Gets metadata for a specific project.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.PROJECT;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String sc_id = args.getString("sc_id");
        HashMap<String, Object> map = lC.b(sc_id);
        if (map == null) return new ToolResult("Project not found: " + sc_id, true);
        return new ToolResult(new JSONObject(map).toString(), false);
    }
}
