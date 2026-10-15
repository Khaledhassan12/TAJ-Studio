package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.HashMap;
import pro.sketchware.ai.agent.Tool;
import a.a.a.wq;
import a.a.a.lC;
import pro.sketchware.utility.FileUtil;

public final class ListProjectsTool implements Tool {

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
        return new ToolSpec("list_projects", "Lists all available Sketchware projects.", emptyObjectSchema());
    }

    @Override
    public Domain domain() {
        return Domain.PROJECT;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        ArrayList<HashMap<String, Object>> list = lC.a();
        JSONArray arr = new JSONArray();
        for (HashMap<String, Object> map : list) {
            JSONObject item = new JSONObject();
            item.put("sc_id", map.get("sc_id"));
            item.put("name", map.get("my_ws_name"));
            item.put("package", map.get("my_sc_pkg_name"));
            arr.put(item);
        }
        return new ToolResult(arr.toString(), false);
    }
}
