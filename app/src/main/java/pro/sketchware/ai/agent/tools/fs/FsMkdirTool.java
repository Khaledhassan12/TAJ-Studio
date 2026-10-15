package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONObject;
import org.json.JSONArray;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.utility.FileUtil;

public final class FsMkdirTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            params.put("properties", new JSONObject().put("path", new JSONObject().put("type", "string")));
            params.put("required", new JSONArray().put("path"));
            return new ToolSpec("fs_mkdir", "Creates a directory.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.FS;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        java.io.File directory = PathResolver.resolve(ctx.sc_id, args.getString("path"));
        String path = directory.getAbsolutePath();
        if (directory.exists()) return new ToolResult("Path already exists.", false);
        if (!ctx.checkPermission(path, false)) return new ToolResult("Denied.", true);
        FileUtil.makeDir(path);
        if (directory.isDirectory()) return new ToolResult("verified: folder created @ " + path, false);
        return new ToolResult("Failed to create folder.", true);
    }
}
