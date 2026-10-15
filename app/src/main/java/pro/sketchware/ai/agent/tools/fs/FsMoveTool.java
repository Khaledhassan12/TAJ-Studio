package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.tools.DomainToolHelper;

public final class FsMoveTool implements Tool {
    @Override
    public ToolSpec spec() {
        try {
            JSONObject props = new JSONObject();
            props.put("src", new JSONObject().put("type", "string").put("maxLength", 4096));
            props.put("dst", new JSONObject().put("type", "string").put("maxLength", 4096));
            JSONObject schema = new JSONObject();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", new JSONArray().put("src").put("dst"));
            return new ToolSpec("fs_move", "Moves a file or directory tree only after explicit approval, refuses overwrite, and verifies the destination fingerprint and source removal.", schema);
        } catch (Exception e) { throw new IllegalStateException("Could not construct fs_move schema.", e); }
    }
    @Override public Domain domain() { return Domain.FS; }
    @Override public RiskLevel riskLevel(JSONObject args) { return RiskLevel.DESTRUCTIVE; }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) {
        String src = null;
        String dst = null;
        try {
            File source = PathResolver.resolve(ctx.sc_id, args.getString("src"));
            File destination = PathResolver.resolve(ctx.sc_id, args.getString("dst"));
            src = source.getAbsolutePath();
            dst = destination.getAbsolutePath();

            if (!source.exists()) return ToolResult.structured("fs_move", "NOT_FOUND", src, "Source does not exist.", null, true);
            if (destination.exists()) return ToolResult.structured("fs_move", "CONFLICT", dst,
                    "Destination already exists. It was not overwritten.", null, true);
            String canonicalSource = source.getCanonicalPath();
            String canonicalDestination = destination.getCanonicalPath();
            if (canonicalSource.equals(canonicalDestination)
                    || canonicalDestination.startsWith(canonicalSource + File.separator)) {
                return ToolResult.structured("fs_move", "INVALID_ARGUMENTS", dst,
                        "Destination cannot be the source itself or a descendant of the source.", null, true);
            }
            if (!ctx.checkPermission(src, true) || !ctx.checkPermission(dst, false)) {
                return ToolResult.structured("fs_move", "DENIED", dst, "Move permission was denied.", null, true);
            }

            String sourceHash = DomainToolHelper.fingerprintTree(source);
            DomainToolHelper.moveTree(source, destination);
            if (source.exists() || !destination.exists()
                    || !sourceHash.equals(DomainToolHelper.fingerprintTree(destination))) {
                return ToolResult.structured("fs_move", "VERIFICATION_FAILED", dst,
                        "Move was not fully verified. Inspect both source and destination before retrying.", null, true);
            }
            return ToolResult.structured("fs_move", "SUCCESS", dst,
                    "Destination fingerprint matches " + sourceHash + "; original source path is absent.", null, false);
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Move failed; inspect both source and destination before retrying." : e.getMessage();
            return ToolResult.structured("fs_move", "ERROR", dst, message, null, true);
        }
    }
}
