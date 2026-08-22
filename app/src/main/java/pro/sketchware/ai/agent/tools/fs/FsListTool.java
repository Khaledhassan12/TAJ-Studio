package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.util.ArrayList;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.utility.FileUtil;

public final class FsListTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("path", new JSONObject().put("type", "string"));
            props.put("sc_id", new JSONObject().put("type", "string"));
            props.put("recursive", new JSONObject().put("type", "boolean"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("path"));
            return new ToolSpec("fs_list", "Lists files in a path via PathResolver.", params);
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
        boolean recursive = args.optBoolean("recursive", false);
        
        File dir = PathResolver.resolve(sc_id, userPath);
        String path = dir.getAbsolutePath();
        
        if (!dir.exists()) return new ToolResult("Path does not exist: " + path, true);
        if (!dir.isDirectory()) return new ToolResult("Path is not a directory: " + path, true);

        ArrayList<String> files = new ArrayList<>();
        if (recursive) {
            FileUtil.listDir(path, files);
        } else {
            File[] list = dir.listFiles();
            if (list != null) {
                for (File f : list) files.add(f.getAbsolutePath());
            }
        }
        
        JSONArray arr = new JSONArray();
        for (String f : files) arr.put(f);
        return new ToolResult(arr.toString(), false);
    }
}
