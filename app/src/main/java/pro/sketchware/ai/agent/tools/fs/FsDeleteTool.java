package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.tools.DomainToolHelper;

public final class FsDeleteTool implements Tool {
    @Override
    public ToolSpec spec() {
        try {
            JSONObject props = new JSONObject();
            props.put("path", new JSONObject().put("type", "string").put("maxLength", 4096));
            JSONObject schema = new JSONObject();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", new JSONArray().put("path"));
            return new ToolSpec("fs_delete", "Moves one regular project file to project-scoped recoverable trash; directory deletion is not supported.", schema);
        } catch (Exception e) { throw new IllegalStateException("Could not construct fs_delete schema.", e); }
    }

    @Override public Domain domain() { return Domain.FS; }
    @Override public RiskLevel riskLevel(JSONObject args) { return RiskLevel.DESTRUCTIVE; }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) {
        String path = null;
        try {
            File target = PathResolver.resolve(ctx.sc_id, args.getString("path"));
            path = target.getAbsolutePath();
            if (!target.exists()) return ToolResult.structured("fs_delete", "NOT_FOUND", path, "Target does not exist.", null, true);
            if (!target.isFile()) return ToolResult.structured("fs_delete", "UNSUPPORTED", path,
                    "Only a single regular file can be moved to recoverable trash; directories are preserved.", null, true);
            if (!ctx.checkPermission(path, true)) return ToolResult.structured("fs_delete", "DENIED", path, "Delete permission was denied.", null, true);

            // Recheck after permission UI and immediately before the irreversible filesystem step.
            if (ctx.isCancellationRequested()) {
                return ToolResult.structured("fs_delete", "CANCELLED", path,
                        "Cancellation was received before moving the file to recoverable trash; no delete was started.", null, true);
            }
            if (!target.isFile()) {
                return ToolResult.structured("fs_delete", "CONFLICT", path,
                        "The target changed after approval; no delete was started.", null, true);
            }
            String trashPath = DomainToolHelper.moveFileToTrash(path, ctx.sc_id);
            if (target.exists() || !new File(trashPath).isFile()) {
                return ToolResult.structured("fs_delete", "VERIFICATION_FAILED", path,
                        "Trash move verification failed; inspect the original and trash location.", null, true);
            }
            return ToolResult.structured("fs_delete", "SUCCESS", path,
                    "Original path is absent; file was moved to verified recoverable trash: " + trashPath, null, false);
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Delete failed; inspect source and trash before retrying." : e.getMessage();
            return ToolResult.structured("fs_delete", "ERROR", path, message, null, true);
        }
    }
}
