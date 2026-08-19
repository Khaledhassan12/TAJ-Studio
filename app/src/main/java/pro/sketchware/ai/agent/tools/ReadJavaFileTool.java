package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.utility.FileUtil;

public final class ReadJavaFileTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject properties = new JSONObject();
            
            JSONObject target = new JSONObject();
            target.put("type", "string");
            target.put("enum", new JSONArray(new String[]{"manager", "project"}));
            
            JSONObject path = new JSONObject();
            path.put("type", "string");
            path.put("description", "Absolute path to the file (preferred) or relative name if unique.");

            properties.put("target", target);
            properties.put("path", path);
            params.put("properties", properties);
            params.put("required", new JSONArray(new String[]{"target", "path"}));
            
            return new ToolSpec("read_java_file", 
                "Reads the content of a Java or Kotlin file (capped at 20k chars).", 
                params);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String target = args.getString("target");
        String path = args.getString("path");

        if (!FileUtil.isExistFile(path)) {
            // Try to resolve relative path
            String root = "manager".equals(target) ? JavaToolHelper.getManagerPath(ctx.sc_id) : JavaToolHelper.getProjectPath(ctx.sc_id);
            if (!path.startsWith("/")) {
                path = root + (path.startsWith(java.io.File.separator) ? "" : java.io.File.separator) + path;
            }
        }

        if (!FileUtil.isExistFile(path)) {
            return new ToolResult("File not found: " + path, true);
        }

        String content = FileUtil.readFile(path);
        boolean truncated = false;
        if (content.length() > 20000) {
            content = content.substring(0, 20000);
            truncated = true;
        }

        return new ToolResult(content + (truncated ? "\n\n(Content truncated to 20k chars)" : ""), false);
    }
}
