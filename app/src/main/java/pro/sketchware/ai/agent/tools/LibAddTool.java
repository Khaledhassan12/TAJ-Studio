package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.GsonUtils;
import pro.sketchware.ai.live.LiveRegistry;

public final class LibAddTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("id"));
            return new ToolSpec("lib_add", "Adds a local library to the project.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.LIB;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String id = args.getString("id").trim();
        if (id.isEmpty()) return new ToolResult("Library id is required.", true);
        String path = new FilePathUtil().getPathLocalLibrary(ctx.sc_id);

        ArrayList<String> libs = new ArrayList<>();
        if (FileUtil.isExistFile(path)) {
            try {
                libs = GsonUtils.getGson().fromJson(FileUtil.readFile(path), new com.google.gson.reflect.TypeToken<ArrayList<String>>(){}.getType());
            } catch (RuntimeException e) {
                return new ToolResult("Local library list is malformed; refusing to overwrite it.", true);
            }
        }
        if (libs == null) libs = new ArrayList<>();
        if (libs.contains(id)) return new ToolResult("Library already exists in this project: " + id, false);
        if (!ctx.checkPermission(path, FileUtil.isExistFile(path))) return new ToolResult("Denied.", true);
        libs.add(id);

        String next = GsonUtils.getGson().toJson(libs);
        DomainToolHelper.backup(path);
        try {
            FileUtil.writeFile(path, next);
            String persisted = FileUtil.readFile(path);
            ArrayList<String> verified = GsonUtils.getGson().fromJson(persisted, new com.google.gson.reflect.TypeToken<ArrayList<String>>(){}.getType());
            if (verified == null || !verified.contains(id)) throw new IllegalStateException("Persisted library list did not contain the requested id.");
            LiveRegistry.notifyChanged("libs", path);
            return new ToolResult("verified: library '" + id + "' added to current project @ " + path, false);
        } catch (Exception e) {
            DomainToolHelper.restore(path);
            return new ToolResult("Library add failed: " + e.getMessage(), true);
        }
    }
}
