package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.FormatRegistry;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.utility.FileUtil;

public final class FsReadTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("path", new JSONObject().put("type", "string"));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new org.json.JSONArray().put("path"));
            return new ToolSpec("fs_read", "Reads a file with PathResolver and structured result.", params);
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
            return ToolResult.structured("fs_read", "ERROR", path, "File not found.", new JSONArray(PathResolver.candidates(sc_id, f.getName())), true);
        }
        
        JSONObject res = FormatRegistry.parse(path);
        res.put("tool", "fs_read");
        res.put("status", "SUCCESS");
        res.put("path", path);
        String content = FileUtil.readFile(path);
        if (content.length() > 20000) {
            res.put("raw", content.substring(0, 20000));
            res.put("truncated", true);
        } else {
            res.put("raw", content);
            res.put("truncated", false);
        }
        res.put("bytes", f.length());
        
        return new ToolResult(res.toString(), false);
    }
}
