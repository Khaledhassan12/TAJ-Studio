package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.ai.live.LiveRegistry;

public final class ResCreateTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("folder", new JSONObject().put("type", "string"));
            props.put("name", new JSONObject().put("type", "string"));
            props.put("content", new JSONObject().put("type", "string"));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("folder").put("name").put("content"));
            return new ToolSpec("res_create", "Creates a resource file with validation.", params);
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
        String content = args.getString("content");
        String sc_id = args.optString("sc_id", ctx.sc_id);
        
        File f = PathResolver.resolve(sc_id, "res/" + folder + "/" + name);
        String path = f.getAbsolutePath();

        if (f.exists()) return new ToolResult("Resource already exists: " + path, true);
        if (!ctx.checkPermission(path, false)) return new ToolResult("Denied.", true);

        // Validation for XML
        if (name.endsWith(".xml") && !DomainToolHelper.verifyXml(content)) {
            return new ToolResult("Malformed XML content.", true);
        }

        FileUtil.writeFile(path, content);
        if (FileUtil.isExistFile(path)) {
            if ("layout".equals(folder)) {
                try {
                    DomainToolHelper.syncLayoutToDesigner(sc_id, name, content);
                } catch (Exception ignored) {}
            }
            LiveRegistry.notifyChanged(folder, path);
            return new ToolResult("verified: created @ " + path, false);
        }
        return new ToolResult("Failed to create file.", true);
    }
}
