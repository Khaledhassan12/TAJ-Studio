package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        String sc_id = ctx.sc_id;
        
        File f = PathResolver.resolve(sc_id, "res/" + folder + "/" + name);
        String path = f.getAbsolutePath();

        if (f.exists()) return new ToolResult("Resource already exists: " + path, true);
        if (!ctx.checkPermission(path, false)) return new ToolResult("Denied.", true);

        // Validation for XML
        if (name.endsWith(".xml") && !DomainToolHelper.verifyXml(content)) {
            return new ToolResult("Malformed XML content.", true);
        }

        try {
            DomainToolHelper.atomicWrite(f, content, null);
            if (!f.isFile() || !content.equals(DomainToolHelper.readUtf8(f))) {
                return ToolResult.structured("res_create", "VERIFICATION_FAILED", path,
                        "The new resource did not match the requested content after read-back.", null, true);
            }
            if ("layout".equals(folder)) {
                DomainToolHelper.syncLayoutToDesigner(sc_id, name, content);
                if (!DomainToolHelper.verifyLiveLayout(sc_id, name, content)) {
                    return ToolResult.structured("res_create", "DESIGNER_SYNC_FAILED", path,
                            "The resource file was created and read-back verified, but the live Sketchware designer model could not be verified. The file was preserved for inspection; this is not a full success.", null, true);
                }
            }
            // Rebind the active visual editor after the layout model has been synchronized
            // and verified. Resource creation must not leave the View tab displaying stale beans.
            if ("layout".equals(folder)) {
                DomainToolHelper.refreshEditor(ctx);
            }
            LiveRegistry.notifyChanged(folder, path);
            return ToolResult.structured("res_create", "SUCCESS", path,
                    "Exact resource read-back verified" + ("layout".equals(folder) ? "; live designer model also verified." : "."), null, false);
        } catch (Exception e) {
            return ToolResult.structured("res_create", "ERROR", path,
                    (e.getMessage() == null ? "Resource creation failed." : e.getMessage())
                            + " Inspect the target before retrying because a partial create may have occurred.", null, true);
        }
    }
}
