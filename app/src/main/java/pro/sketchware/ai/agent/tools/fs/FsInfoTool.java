package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
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
        String path = args.getString("path");
        if (!FileUtil.isExistFile(path)) return new ToolResult("Not found.", true);
        
        File f = new File(path);
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
