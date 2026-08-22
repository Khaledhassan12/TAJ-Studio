package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.util.ArrayList;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.utility.FileUtil;

public final class ResListTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("folder", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"anim", "drawable", "drawable-xhdpi", "layout", "menu", "values", "values-night"})));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("folder"));
            return new ToolSpec("res_list", "Lists resource files using PathResolver.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.RES;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String folder = args.getString("folder");
        String sc_id = args.optString("sc_id", ctx.sc_id);
        
        File rootDir = PathResolver.resolve(sc_id, "res/" + folder);
        String path = rootDir.getAbsolutePath();

        if (!rootDir.exists()) {
            return new ToolResult("Resource folder not found: " + path, true);
        }

        ArrayList<String> files = new ArrayList<>();
        FileUtil.listDir(path, files);
        
        JSONArray arr = new JSONArray();
        for (String f : files) arr.put(new File(f).getName());
        return new ToolResult(arr.toString(), false);
    }
}
