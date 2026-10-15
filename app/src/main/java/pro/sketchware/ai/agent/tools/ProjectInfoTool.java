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
            params.put("properties", new JSONObject());
            return new ToolSpec("project_info", "Gets metadata for the project currently open in TAJ Studio.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.PROJECT;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String sc_id = ctx.sc_id;
        HashMap<String, Object> map = lC.b(sc_id);
        if (map == null) return new ToolResult("Project not found: " + sc_id, true);
        return new ToolResult(new JSONObject(map).toString(), false);
    }
}
