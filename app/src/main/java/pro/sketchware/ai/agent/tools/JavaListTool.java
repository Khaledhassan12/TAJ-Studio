package pro.sketchware.ai.agent.tools;

import pro.sketchware.ai.agent.ProjectPaths;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.utility.FileUtil;

public final class JavaListTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("target", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"manager", "project"})));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("target"));
            return new ToolSpec("java_list", "Lists Java/Kotlin files.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.JAVA;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String target = args.getString("target");
        String sc_id = args.optString("sc_id", ctx.sc_id);
        String root = "manager".equals(target) ? ProjectPaths.javaManagerRoot(sc_id) : ProjectPaths.projectJavaRoot(sc_id);

        if (!FileUtil.isExistFile(root)) return new ToolResult("Root not found: " + root, true);

        ArrayList<String> files = new ArrayList<>();
        FileUtil.listDir(root, files);
        
        JSONArray arr = new JSONArray();
        for (String f : files) {
            if (f.endsWith(".java") || f.endsWith(".kt")) arr.put(f);
        }
        return new ToolResult(arr.toString(), false);
    }
}
