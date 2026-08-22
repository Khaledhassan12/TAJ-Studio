package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.utility.FileUtil;

public final class FsCopyTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            params.put("properties", new JSONObject().put("src", new JSONObject().put("type", "string")).put("dst", new JSONObject().put("type", "string")));
            params.put("required", new JSONArray().put("src").put("dst"));
            return new ToolSpec("fs_copy", "Copies a file or folder.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.FS;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String src = args.getString("src");
        String dst = args.getString("dst");
        if (!FileUtil.isExistFile(src)) return new ToolResult("Source not found.", true);
        if (!ctx.checkPermission(dst, FileUtil.isExistFile(dst))) return new ToolResult("Denied.", true);

        try {
            if (FileUtil.isDirectory(src)) {
                FileUtil.copyDirectory(new File(src), new File(dst));
            } else {
                FileUtil.copyFile(src, dst);
            }
            return new ToolResult("verified: copied to " + dst, false);
        } catch (Exception e) {
            return new ToolResult("Copy failed: " + e.getMessage(), true);
        }
    }
}
