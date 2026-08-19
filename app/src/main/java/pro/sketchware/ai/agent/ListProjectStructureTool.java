package pro.sketchware.ai.agent;

import org.json.JSONObject;
import org.json.JSONArray;
import a.a.a.jC;
import com.besome.sketch.beans.ProjectFileBean;

public class ListProjectStructureTool implements Tool {

    @Override
    public ToolSpec spec() {
        return new ToolSpec("list_project_structure", "Lists all activities, layouts and files in the project", new JSONObject());
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) {
        try {
            JSONObject res = new JSONObject();
            JSONArray activities = new JSONArray();
            for (ProjectFileBean file : jC.b(ctx.sc_id).b()) {
                JSONObject f = new JSONObject();
                f.put("name", file.getJavaName());
                f.put("xml", file.getXmlName());
                f.put("type", file.fileType);
                activities.put(f);
            }
            res.put("files", activities);
            return new ToolResult(res.toString(), false);
        } catch (Exception e) {
            return new ToolResult("Error listing structure: " + e.getMessage(), true);
        }
    }
}
