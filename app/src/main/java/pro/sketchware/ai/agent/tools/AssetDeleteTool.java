package pro.sketchware.ai.agent.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.live.LiveRegistry;

public final class AssetDeleteTool implements Tool {
    @Override public ToolSpec spec() {
        try {
            JSONObject props = new JSONObject();
            props.put("path", new JSONObject().put("type", "string").put("maxLength", 4096));
            JSONObject schema = new JSONObject();
            schema.put("type", "object"); schema.put("properties", props);
            schema.put("required", new JSONArray().put("path"));
            return new ToolSpec("asset_delete", "Moves one asset file to verified project-scoped recoverable trash.", schema);
        } catch (Exception e) { throw new IllegalStateException("Could not construct asset_delete schema.", e); }
    }
    @Override public Domain domain() { return Domain.ASSET; }
    @Override public RiskLevel riskLevel(JSONObject args) { return RiskLevel.DESTRUCTIVE; }

    @Override public ToolResult run(JSONObject args, ToolContext ctx) {
        String path = null;
        try {
            File target = PathResolver.resolve(ctx.sc_id, "assets/" + args.getString("path"));
            path = target.getAbsolutePath();
            if (!target.exists()) return ToolResult.structured("asset_delete", "NOT_FOUND", path, "Asset does not exist.", null, true);
            if (!target.isFile()) return ToolResult.structured("asset_delete", "UNSUPPORTED", path, "Only a single asset file can be moved to trash.", null, true);
            if (!ctx.checkPermission(path, true)) return ToolResult.structured("asset_delete", "DENIED", path, "Delete permission was denied.", null, true);
            String trash = DomainToolHelper.moveFileToTrash(path, ctx.sc_id);
            if (target.exists() || !new File(trash).isFile()) {
                return ToolResult.structured("asset_delete", "VERIFICATION_FAILED", path, "Original path or trash verification failed.", null, true);
            }
            LiveRegistry.notifyChanged("assets", path);
            return ToolResult.structured("asset_delete", "SUCCESS", path, "Original path is absent; verified recoverable trash: " + trash, null, false);
        } catch (Exception e) {
            return ToolResult.structured("asset_delete", "ERROR", path, e.getMessage() == null ? "Asset deletion failed." : e.getMessage(), null, true);
        }
    }
}
