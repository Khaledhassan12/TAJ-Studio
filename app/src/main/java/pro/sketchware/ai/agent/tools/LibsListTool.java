package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.FilePathUtil;
import a.a.a.jC;
import a.a.a.iC;
import pro.sketchware.utility.GsonUtils;

public final class LibsListTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            params.put("properties", new JSONObject().put("sc_id", new JSONObject().put("type", "string")));
            return new ToolSpec("libs_list", "Lists libraries in a project.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.LIB;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String sc_id = args.optString("sc_id", ctx.sc_id);
        iC libManager = jC.c(sc_id);
        
        JSONObject res = new JSONObject();
        res.put("admob", new JSONObject(GsonUtils.getGson().toJson(libManager.b())));
        res.put("appCompat", new JSONObject(GsonUtils.getGson().toJson(libManager.c())));
        res.put("firebase", new JSONObject(GsonUtils.getGson().toJson(libManager.d())));
        res.put("googleMaps", new JSONObject(GsonUtils.getGson().toJson(libManager.e())));
        
        String localPath = new FilePathUtil().getPathLocalLibrary(sc_id);
        if (FileUtil.isExistFile(localPath)) {
            res.put("local", new JSONArray(FileUtil.readFile(localPath)));
        }
        
        return new ToolResult(res.toString(), false);
    }
}
