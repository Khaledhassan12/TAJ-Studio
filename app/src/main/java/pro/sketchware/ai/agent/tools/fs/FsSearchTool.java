package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.utility.FileUtil;

public final class FsSearchTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("pattern", new JSONObject().put("type", "string"));
            props.put("path", new JSONObject().put("type", "string").put("description", "Base path to search. Defaults to project root."));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("pattern"));
            return new ToolSpec("fs_search", "Recursive walk of project data to find patterns or files.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.FS;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String pattern = args.getString("pattern");
        String sc_id = args.optString("sc_id", ctx.sc_id);
        String basePath = args.optString("path", "");
        
        File root = PathResolver.resolve(sc_id, basePath);
        String rootPath = root.getAbsolutePath();
        if (!root.exists()) return new ToolResult("Search root not found: " + rootPath, true);

        JSONArray results = new JSONArray();
        try {
            walk(root, pattern, results, 0);
        } catch (Exception ignored) {}

        JSONObject res = new JSONObject();
        res.put("tool", "fs_search");
        res.put("status", "SUCCESS");
        if (results.length() == 0) {
            res.put("message", "no matches; searched roots: " + rootPath);
        } else {
            res.put("matches", results);
        }
        return new ToolResult(res.toString(), false);
    }

    private void walk(File dir, String pattern, JSONArray results, int depth) {
        if (depth > 8 || results.length() >= 50) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            String name = f.getName();
            if (name.endsWith(".bak") || name.contains(".agent_trash")) continue;
            
            if (f.isDirectory()) {
                walk(f, pattern, results, depth + 1);
            } else {
                if (name.toLowerCase().contains(pattern.toLowerCase())) {
                    results.put(f.getAbsolutePath());
                } else {
                    try {
                        String content = FileUtil.readFile(f.getAbsolutePath());
                        if (content.contains(pattern)) {
                            results.put(f.getAbsolutePath());
                        }
                    } catch (Exception ignored) {}
                }
            }
            if (results.length() >= 50) break;
        }
    }
}
