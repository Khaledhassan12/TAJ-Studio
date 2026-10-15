package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.tools.DomainToolHelper;

public final class FsCopyTool implements Tool {
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
            return new ToolSpec("fs_copy", "Copies one file or directory tree without following symbolic links; refuses overwrite and verifies SHA-256 fingerprints.", schema);
        } catch (Exception e) { throw new IllegalStateException("Could not construct fs_copy schema.", e); }
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

            if (!source.exists()) return ToolResult.structured("fs_copy", "NOT_FOUND", src, "Source does not exist.", null, true);
            String canonicalSource = source.getCanonicalPath();
            String canonicalDestination = destination.getCanonicalPath();
            if (canonicalSource.equals(canonicalDestination)
                    || canonicalDestination.startsWith(canonicalSource + File.separator)) {
                return ToolResult.structured("fs_copy", "INVALID_ARGUMENTS", dst,
                        "Destination cannot be the source itself or a descendant of the source.", null, true);
            }
            if (destination.exists()) return ToolResult.structured("fs_copy", "CONFLICT", dst,
                    "Destination already exists. It was not overwritten.", null, true);
            if (!ctx.checkPermission(dst, false)) return ToolResult.structured("fs_copy", "DENIED", dst, "Copy permission was denied.", null, true);

            String sourceHash = DomainToolHelper.fingerprintTree(source);
            DomainToolHelper.copyTree(source, destination);
            if (!source.exists() || !destination.exists()
                    || !sourceHash.equals(DomainToolHelper.fingerprintTree(destination))) {
                return ToolResult.structured("fs_copy", "VERIFICATION_FAILED", dst,
                        "Destination fingerprint does not match the source; inspect both paths before retrying.", null, true);
            }
            return ToolResult.structured("fs_copy", "SUCCESS", dst,
                    "Destination tree matches source fingerprint " + sourceHash + "; source was preserved.", null, false);
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Copy failed; inspect the source and destination before retrying." : e.getMessage();
            return ToolResult.structured("fs_copy", "ERROR", dst, message, null, true);
        }
    }
}
