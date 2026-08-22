package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.utility.FileUtil;

public final class FsDeleteTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            params.put("properties", new JSONObject().put("path", new JSONObject().put("type", "string")).put("sc_id", new JSONObject().put("type", "string")));
            params.put("required", new org.json.JSONArray().put("path"));
            return new ToolSpec("fs_delete", "Moves a file to trash via PathResolver.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.FS;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String userPath = args.getString("path");
        String sc_id = args.optString("sc_id", ctx.sc_id);
        
        File f = PathResolver.resolve(sc_id, userPath);
        String path = f.getAbsolutePath();
        
        if (!f.exists()) {
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", "Not found: " + path);
            err.put("absolutePath", path);
            err.put("candidates", new JSONArray(PathResolver.candidates(sc_id, f.getName())));
            return new ToolResult(err.toString(), true);
        }
        
        if (!ctx.checkPermission(path, true)) return new ToolResult("Delete denied.", true);

        String trashDir = FileUtil.getExternalStorageDir() + "/.sketchware/.agent_trash";
        FileUtil.makeDir(trashDir);
        String trashPath = trashDir + "/" + System.currentTimeMillis() + "_" + f.getName();
        
        FileUtil.moveFile(path, trashPath);
        if (FileUtil.isExistFile(trashPath)) {
            return new ToolResult("verified: moved to trash @ " + trashPath, false);
        } else {
            return new ToolResult("Delete failed.", true);
        }
    }
}
