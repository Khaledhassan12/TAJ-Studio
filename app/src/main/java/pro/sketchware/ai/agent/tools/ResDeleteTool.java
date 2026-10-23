package pro.sketchware.ai.agent.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.live.LiveRegistry;

public final class ResDeleteTool implements Tool {
    @Override public ToolSpec spec() {
        try {
            JSONObject props = new JSONObject();
            props.put("folder", new JSONObject().put("type", "string").put("maxLength", 128));
            props.put("name", new JSONObject().put("type", "string").put("maxLength", 4096));
            JSONObject schema = new JSONObject();
            schema.put("type", "object"); schema.put("properties", props);
            schema.put("required", new JSONArray().put("folder").put("name"));
            return new ToolSpec("res_delete", "Moves one resource file to verified recoverable trash; directory deletion is unsupported.", schema);
        } catch (Exception e) { throw new IllegalStateException("Could not construct res_delete schema.", e); }
    }
    @Override public Domain domain() { return Domain.RES; }
    @Override public RiskLevel riskLevel(JSONObject args) { return RiskLevel.DESTRUCTIVE; }

    @Override public ToolResult run(JSONObject args, ToolContext ctx) {
        String path = null;
        try {
            File target = PathResolver.resolve(ctx.sc_id, "res/" + args.getString("folder") + "/" + args.getString("name"));
            path = target.getAbsolutePath();
            if (!target.exists()) return ToolResult.structured("res_delete", "NOT_FOUND", path, "Resource does not exist.", null, true);
            if (!target.isFile()) return ToolResult.structured("res_delete", "UNSUPPORTED", path, "Only a single resource file can be moved to trash.", null, true);
            if (!ctx.checkPermission(path, true)) return ToolResult.structured("res_delete", "DENIED", path, "Delete permission was denied.", null, true);
            String trash = DomainToolHelper.moveFileToTrash(path, ctx.sc_id);
            if (target.exists() || !new File(trash).isFile()) {
                return ToolResult.structured("res_delete", "VERIFICATION_FAILED", path, "Original path or trash verification failed.", null, true);
            }
            String changedFolder = args.getString("folder");
            LiveRegistry.notifyChanged(changedFolder, path);
            return ToolResult.structured("res_delete", "SUCCESS", path, "Original path is absent; verified recoverable trash: " + trash, null, false);
        } catch (Exception e) {
            return ToolResult.structured("res_delete", "ERROR", path, e.getMessage() == null ? "Resource deletion failed." : e.getMessage(), null, true);
        }
    }
}
