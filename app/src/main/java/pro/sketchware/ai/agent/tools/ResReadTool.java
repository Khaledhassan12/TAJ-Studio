package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.agent.FormatRegistry;
import pro.sketchware.utility.FileUtil;

public final class ResReadTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("folder", new JSONObject().put("type", "string"));
            props.put("name", new JSONObject().put("type", "string"));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("folder").put("name"));
            return new ToolSpec("res_read", "Reads a resource file via PathResolver.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.RES;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String folder = args.getString("folder");
        String name = args.getString("name");
        String sc_id = args.optString("sc_id", ctx.sc_id);
        
        File f = PathResolver.resolve(sc_id, "res/" + folder + "/" + name);
        String path = f.getAbsolutePath();

        if (!f.exists()) {
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", "Not found: " + path);
            err.put("absolutePath", path);
            err.put("candidates", new JSONArray(PathResolver.candidates(sc_id, name)));
            return new ToolResult(err.toString(), true);
        }
        
        JSONObject res = FormatRegistry.parse(path);
        res.put("path", path);
        res.put("raw", FileUtil.readFile(path));
        return new ToolResult(res.toString(), false);
    }
}
