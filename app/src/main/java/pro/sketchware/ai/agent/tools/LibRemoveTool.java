package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.GsonUtils;
import pro.sketchware.ai.live.LiveRegistry;

public final class LibRemoveTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("id", new JSONObject().put("type", "string"));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("id"));
            return new ToolSpec("lib_remove", "Removes a local library.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.LIB;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String id = args.getString("id");
        String sc_id = args.optString("sc_id", ctx.sc_id);
        String path = new FilePathUtil().getPathLocalLibrary(sc_id);

        if (!FileUtil.isExistFile(path)) return new ToolResult("No local libraries found.", true);

        ArrayList<String> libs = GsonUtils.getGson().fromJson(FileUtil.readFile(path), new com.google.gson.reflect.TypeToken<ArrayList<String>>(){}.getType());
        if (libs != null && libs.remove(id)) {
            FileUtil.writeFile(path, GsonUtils.getGson().toJson(libs));
            LiveRegistry.notifyChanged("libs", path);
            return new ToolResult("verified: library '" + id + "' removed.", false);
        }
        return new ToolResult("Library not found in project.", true);
    }
}
