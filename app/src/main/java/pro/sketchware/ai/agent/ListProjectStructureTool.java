package pro.sketchware.ai.agent;

import org.json.JSONObject;
import org.json.JSONArray;
import a.a.a.jC;
import com.besome.sketch.beans.ProjectFileBean;

public class ListProjectStructureTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            params.put("properties", new JSONObject().put("sc_id", new JSONObject().put("type", "string")));
            return new ToolSpec("list_project_structure", "Lists project activities and layout aliases.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.PROJECT;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) {
        try {
            String sc_id = args.optString("sc_id", ctx.sc_id);
            JSONObject res = new JSONObject();
            JSONArray activities = new JSONArray();
            for (ProjectFileBean file : jC.b(sc_id).b()) {
                JSONObject f = new JSONObject();
                f.put("name", file.getJavaName());
                f.put("xml", file.getXmlName());
                f.put("type", file.fileType);
                f.put("alias", "res/layout/" + file.getXmlName());
                activities.put(f);
            }
            res.put("files", activities);
            res.put("sc_id", sc_id);
            return new ToolResult(res.toString(), false);
        } catch (Exception e) {
            return new ToolResult("Error listing structure: " + e.getMessage(), true);
        }
    }
}
