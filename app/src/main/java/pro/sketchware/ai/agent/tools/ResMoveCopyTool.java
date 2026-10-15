package pro.sketchware.ai.agent.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.live.LiveRegistry;

public final class ResMoveCopyTool implements Tool {
    @Override public ToolSpec spec() {
        try {
            JSONObject props = new JSONObject();
            props.put("fromFolder", new JSONObject().put("type", "string").put("maxLength", 128));
            props.put("fromName", new JSONObject().put("type", "string").put("maxLength", 4096));
            props.put("toFolder", new JSONObject().put("type", "string").put("maxLength", 128));
            props.put("toName", new JSONObject().put("type", "string").put("maxLength", 4096));
            props.put("op", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"move", "copy"})));
            JSONObject schema = new JSONObject();
            schema.put("type", "object"); schema.put("properties", props);
            schema.put("required", new JSONArray().put("fromFolder").put("fromName").put("toFolder").put("toName").put("op"));
            return new ToolSpec("res_move_copy", "Moves or copies a resource file; refuses overwrite and verifies content fingerprints.", schema);
        } catch (Exception e) { throw new IllegalStateException("Could not construct res_move_copy schema.", e); }
    }
    @Override public Domain domain() { return Domain.RES; }
    @Override public RiskLevel riskLevel(JSONObject args) { return RiskLevel.DESTRUCTIVE; }

    @Override public ToolResult run(JSONObject args, ToolContext ctx) {
        String fromPath = null;
        String toPath = null;
        try {
            String fromFolder = args.getString("fromFolder");
            String toFolder = args.getString("toFolder");
            String op = args.getString("op");
            if (!"move".equals(op) && !"copy".equals(op)) {
                return ToolResult.structured("res_move_copy", "INVALID_ARGUMENTS", null, "Unsupported operation.", null, true);
            }
            File source = PathResolver.resolve(ctx.sc_id, "res/" + fromFolder + "/" + args.getString("fromName"));
            File destination = PathResolver.resolve(ctx.sc_id, "res/" + toFolder + "/" + args.getString("toName"));
            fromPath = source.getAbsolutePath(); toPath = destination.getAbsolutePath();
            if (!source.isFile()) return ToolResult.structured("res_move_copy", "NOT_FOUND", fromPath, "Source resource file does not exist.", null, true);
            if (destination.exists()) return ToolResult.structured("res_move_copy", "CONFLICT", toPath, "Destination exists; it was not overwritten.", null, true);
            if (source.getCanonicalPath().equals(destination.getCanonicalPath())) {
                return ToolResult.structured("res_move_copy", "INVALID_ARGUMENTS", toPath, "Source and destination resolve to the same path.", null, true);
            }
            if (!ctx.checkPermission(toPath, false)
                    || ("move".equals(op) && !ctx.checkPermission(fromPath, true))) {
                return ToolResult.structured("res_move_copy", "DENIED", toPath, "Resource operation permission was denied.", null, true);
            }
            String before = DomainToolHelper.fingerprintTree(source);
            if ("copy".equals(op)) DomainToolHelper.copyTree(source, destination);
            else DomainToolHelper.moveTree(source, destination);
            if (!destination.isFile() || !before.equals(DomainToolHelper.fingerprintTree(destination))
                    || ("move".equals(op) && source.exists())) {
                return ToolResult.structured("res_move_copy", "VERIFICATION_FAILED", toPath,
                        "Source/destination state or fingerprint did not match the requested operation; inspect both paths.", null, true);
            }
            LiveRegistry.notifyChanged(fromFolder, fromPath);
            LiveRegistry.notifyChanged(toFolder, toPath);
            return ToolResult.structured("res_move_copy", "SUCCESS", toPath,
                    "Verified " + op + " using fingerprint " + before
                            + ("move".equals(op) ? "; source path removed." : "; source preserved."), null, false);
        } catch (Exception e) {
            return ToolResult.structured("res_move_copy", "ERROR", toPath,
                    e.getMessage() == null ? "Resource operation failed; inspect both paths before retrying." : e.getMessage(), null, true);
        }
    }
}
