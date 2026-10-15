package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.utility.FileUtil;

public final class FsInfoTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            params.put("properties", new JSONObject().put("path", new JSONObject().put("type", "string")));
            params.put("required", new JSONArray().put("path"));
            return new ToolSpec("fs_info", "Gets file/folder info.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.FS;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        File f = PathResolver.resolve(ctx.sc_id, args.getString("path"));
        String path = f.getAbsolutePath();
        if (!f.exists()) return new ToolResult("Not found.", true);
        JSONObject res = new JSONObject();
        res.put("path", path);
        res.put("isDirectory", f.isDirectory());
        res.put("size", f.length());
        res.put("lastModified", f.lastModified());
        res.put("canRead", f.canRead());
        res.put("canWrite", f.canWrite());
        
        return new ToolResult(res.toString(), false);
    }
}
