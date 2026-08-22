package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONObject;
import org.json.JSONArray;
import pro.sketchware.ai.agent.Tool;
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
        String path = args.getString("path");
        if (FileUtil.isExistFile(path)) return new ToolResult("Path already exists.", false);
        FileUtil.makeDir(path);
        if (FileUtil.isExistFile(path)) return new ToolResult("verified: folder created @ " + path, false);
        return new ToolResult("Failed to create folder.", true);
    }
}
