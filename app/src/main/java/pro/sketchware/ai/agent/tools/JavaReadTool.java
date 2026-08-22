package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.agent.FormatRegistry;
import pro.sketchware.utility.FileUtil;

public final class JavaReadTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("target", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"manager", "project"})));
            props.put("path", new JSONObject().put("type", "string"));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("target").put("path"));
            return new ToolSpec("java_read", "Reads a Java/Kotlin file via PathResolver.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.JAVA;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String target = args.getString("target");
        String userPath = args.getString("path");
        String sc_id = args.optString("sc_id", ctx.sc_id);

        File f = PathResolver.resolve(sc_id, ("manager".equals(target) ? userPath : "java/" + userPath));
        String path = f.getAbsolutePath();

        if (!f.exists()) {
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", "File not found: " + path);
            err.put("absolutePath", path);
            err.put("candidates", new JSONArray(PathResolver.candidates(sc_id, f.getName())));
            return new ToolResult(err.toString(), true);
        }
        
        JSONObject res = FormatRegistry.parse(path);
        res.put("path", path);
        res.put("raw", FileUtil.readFile(path));
        return new ToolResult(res.toString(), false);
    }
}
