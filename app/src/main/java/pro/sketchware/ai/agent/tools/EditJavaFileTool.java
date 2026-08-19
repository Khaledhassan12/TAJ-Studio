package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.utility.FileUtil;

public final class EditJavaFileTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            
            JSONObject target = new JSONObject();
            target.put("type", "string");
            target.put("enum", new JSONArray(new String[]{"manager", "project"}));
            
            JSONObject path = new JSONObject();
            path.put("type", "string");
            path.put("description", "Absolute path or unique relative name.");

            JSONObject mode = new JSONObject();
            mode.put("type", "string");
            mode.put("enum", new JSONArray(new String[]{"replace_all", "append", "search_replace"}));

            JSONObject content = new JSONObject();
            content.put("type", "string");
            content.put("description", "New content (for replace_all/append) or replacement string (for search_replace).");

            JSONObject search = new JSONObject();
            search.put("type", "string");
            search.put("description", "String to find (for search_replace).");

            props.put("target", target);
            props.put("path", path);
            props.put("mode", mode);
            props.put("content", content);
            props.put("search", search);
            
            params.put("properties", props);
            params.put("required", new JSONArray(new String[]{"target", "path", "mode"}));
            
            return new ToolSpec("edit_java_file", 
                "Edits a Java or Kotlin file. Automatically creates a .bak copy.", 
                params);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String target = args.getString("target");
        String path = args.getString("path");
        String mode = args.getString("mode");

        String root = "manager".equals(target) ? JavaToolHelper.getManagerPath(ctx.sc_id) : JavaToolHelper.getProjectPath(ctx.sc_id);
        
        if ("manager".equals(target)) {
            File managerDir = new File(root);
            String[] list = managerDir.list((dir, name) -> name.endsWith(".java") || name.endsWith(".kt"));
            if (list == null || list.length == 0) {
                return new ToolResult("Java Manager is empty — no java/kotlin files to edit. Create one first.", false);
            }
        }

        if (!FileUtil.isExistFile(path)) {
            if (!path.startsWith("/")) {
                path = root + File.separator + path;
            }
        }

        if (!FileUtil.isExistFile(path)) {
            return new ToolResult("File not found in " + target + ": " + path, true);
        }

        if (!ctx.checkPermission(path, true)) {
            return new ToolResult("Edit denied by user.", true);
        }

        String originalContent = FileUtil.readFile(path);
        String newContent = "";

        switch (mode) {
            case "replace_all":
                newContent = args.getString("content");
                break;
            case "append":
                newContent = originalContent + "\n" + args.getString("content");
                break;
            case "search_replace":
                String s = args.getString("search");
                String r = args.getString("content");
                if (!originalContent.contains(s)) {
                    return new ToolResult("pattern not found", true);
                }
                newContent = originalContent.replace(s, r);
                break;
        }

        JavaToolHelper.backup(path);
        try {
            FileUtil.writeFile(path, newContent);
            return new ToolResult("File edited: " + path + " (" + newContent.length() + " bytes)", false);
        } catch (Exception e) {
            JavaToolHelper.restore(path);
            return new ToolResult("Failed to write file. Original restored. Error: " + e.getMessage(), true);
        }
    }
}
