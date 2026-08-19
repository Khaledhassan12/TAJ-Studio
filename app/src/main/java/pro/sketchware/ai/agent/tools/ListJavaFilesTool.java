package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.utility.FileUtil;

public final class ListJavaFilesTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject properties = new JSONObject();
            
            JSONObject target = new JSONObject();
            target.put("type", "string");
            target.put("enum", new JSONArray(new String[]{"manager", "project"}));
            target.put("description", "Target location: 'manager' for Java Manager files, 'project' for app source tree.");
            
            properties.put("target", target);
            params.put("properties", properties);
            params.put("required", new JSONArray(new String[]{"target"}));
            
            return new ToolSpec("list_java_files", 
                "Lists Java and Kotlin files in the specified target location.", 
                params);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String target = args.getString("target");
        String rootPath;
        
        if ("manager".equals(target)) {
            rootPath = JavaToolHelper.getManagerPath(ctx.sc_id);
        } else {
            rootPath = JavaToolHelper.getProjectPath(ctx.sc_id);
        }

        if (!FileUtil.isExistFile(rootPath)) {
            return new ToolResult("Target directory does not exist: " + rootPath, true);
        }

        List<String> files = new ArrayList<>();
        scanDir(new File(rootPath), files, 0);
        
        if (files.isEmpty()) {
            return new ToolResult("No Java/Kotlin files found in " + target + ".", false);
        }

        StringBuilder sb = new StringBuilder("Java/Kotlin files in ").append(target).append(":\n");
        for (String f : files) {
            sb.append("- ").append(f).append("\n");
        }
        return new ToolResult(sb.toString(), false);
    }

    private void scanDir(File dir, List<String> result, int count) {
        if (result.size() >= 200) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File f : files) {
            if (f.isDirectory()) {
                scanDir(f, result, count);
            } else {
                String name = f.getName();
                if ((name.endsWith(".java") || name.endsWith(".kt")) && !name.endsWith(".bak")) {
                    result.add(f.getAbsolutePath());
                }
            }
            if (result.size() >= 200) break;
        }
    }
}
