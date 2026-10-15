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
        String id = args.getString("id").trim();
        String path = new FilePathUtil().getPathLocalLibrary(ctx.sc_id);
        if (!FileUtil.isExistFile(path)) return new ToolResult("No local libraries found in the current project.", true);

        ArrayList<String> libs;
        try {
            libs = GsonUtils.getGson().fromJson(FileUtil.readFile(path), new com.google.gson.reflect.TypeToken<ArrayList<String>>(){}.getType());
        } catch (RuntimeException e) {
            return new ToolResult("Local library list is malformed; refusing to overwrite it.", true);
        }
        if (libs == null || !libs.contains(id)) return new ToolResult("Library not found in the current project.", true);
        if (!ctx.checkPermission(path, true)) return new ToolResult("Denied.", true);

        libs.remove(id);
        String next = GsonUtils.getGson().toJson(libs);
        DomainToolHelper.backup(path);
        try {
            FileUtil.writeFile(path, next);
            ArrayList<String> verified = GsonUtils.getGson().fromJson(FileUtil.readFile(path), new com.google.gson.reflect.TypeToken<ArrayList<String>>(){}.getType());
            if (verified == null || verified.contains(id)) throw new IllegalStateException("Persisted library list still contains the requested id.");
            LiveRegistry.notifyChanged("libs", path);
            return new ToolResult("verified: library '" + id + "' removed from current project.", false);
        } catch (Exception e) {
            DomainToolHelper.restore(path);
            return new ToolResult("Library removal failed: " + e.getMessage(), true);
        }
    }
}
