package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.ai.live.LiveRegistry;

public final class AssetDeleteTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("path", new JSONObject().put("type", "string"));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("path"));
            return new ToolSpec("asset_delete", "Deletes an asset via PathResolver.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.ASSET;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String userPath = args.getString("path");
        String sc_id = args.optString("sc_id", ctx.sc_id);
        
        File f = PathResolver.resolve(sc_id, "assets/" + userPath);
        String path = f.getAbsolutePath();

        if (!f.exists()) {
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", "Not found: " + path);
            err.put("absolutePath", path);
            err.put("candidates", new JSONArray(PathResolver.candidates(sc_id, f.getName())));
            return new ToolResult(err.toString(), true);
        }
        
        if (!ctx.checkPermission(path, true)) return new ToolResult("Denied.", true);

        String trashDir = FileUtil.getExternalStorageDir() + "/.sketchware/.agent_trash";
        FileUtil.makeDir(trashDir);
        String trashPath = trashDir + "/" + System.currentTimeMillis() + "_" + f.getName();
        
        FileUtil.moveFile(path, trashPath);
        LiveRegistry.notifyChanged("assets", path);
        return new ToolResult("verified: moved to trash @ " + trashPath, false);
    }
}
