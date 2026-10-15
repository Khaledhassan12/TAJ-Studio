package pro.sketchware.ai.agent.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

import pro.sketchware.ai.agent.Tool;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.GsonUtils;
import pro.sketchware.ai.live.LiveRegistry;

public final class LibRemoveTool implements Tool {
    @Override public ToolSpec spec() {
        try {
            JSONObject props = new JSONObject();
            props.put("id", new JSONObject().put("type", "string").put("maxLength", 1024));
            props.put("sc_id", new JSONObject().put("type", "string").put("maxLength", 128));
            JSONObject schema = new JSONObject();
            schema.put("type", "object"); schema.put("properties", props);
            schema.put("required", new JSONArray().put("id"));
            return new ToolSpec("lib_remove", "Removes a local library entry from the active project's metadata with approval and write verification.", schema);
        } catch (Exception e) { throw new IllegalStateException("Could not construct lib_remove schema.", e); }
    }
    @Override public Domain domain() { return Domain.LIB; }
    @Override public RiskLevel riskLevel(JSONObject args) { return RiskLevel.DESTRUCTIVE; }
    @Override public String approvalSummary(JSONObject args, ToolContext ctx) {
        return "Active-project library metadata: " + new FilePathUtil().getPathLocalLibrary(ctx.sc_id)
                + "; remove library: " + (args == null ? "(id missing)" : args.optString("id", "(id missing)"));
    }

    @Override public ToolResult run(JSONObject args, ToolContext ctx) {
        String path = null;
        try {
            // Never accept a model-selected project identity; the task's bound sc_id is authoritative.
            String id = args.getString("id");
            String scId = ctx.sc_id;
            if (scId == null || !scId.matches("[A-Za-z0-9_-]+")) {
                return ToolResult.structured("lib_remove", "INVALID_PROJECT", null, "No valid active project is bound to this task.", null, true);
            }
            path = new FilePathUtil().getPathLocalLibrary(scId);
            File file = new File(path);
            if (!file.isFile()) return ToolResult.structured("lib_remove", "NOT_FOUND", path, "Local library metadata does not exist.", null, true);
            if (!ctx.checkPermission(path, true)) return ToolResult.structured("lib_remove", "DENIED", path, "Permission was denied.", null, true);

            String beforeHash = DomainToolHelper.sha256(file);
            ArrayList<String> libraries = GsonUtils.getGson().fromJson(
                    DomainToolHelper.readUtf8(file),
                    new com.google.gson.reflect.TypeToken<ArrayList<String>>(){}.getType());
            if (libraries == null || !libraries.contains(id)) {
                return ToolResult.structured("lib_remove", "NOT_FOUND", path, "Library entry was not present in the active project.", null, true);
            }
            libraries.remove(id);
            String next = GsonUtils.getGson().toJson(libraries);
            DomainToolHelper.backup(path);
            DomainToolHelper.atomicWrite(file, next, beforeHash);
            String actual = DomainToolHelper.readUtf8(file);
            if (!next.equals(actual)) {
                boolean restored = DomainToolHelper.restoreIfUnchanged(path, DomainToolHelper.contentSha256(next));
                return ToolResult.structured("lib_remove", "VERIFICATION_FAILED", path,
                        "Library metadata read-back failed; original restoration " + (restored ? "was verified." : "could not be safely verified."), null, true);
            }
            LiveRegistry.notifyChanged("libs", path);
            return ToolResult.structured("lib_remove", "SUCCESS", path,
                    "Library entry removed and metadata read-back verified for the active project.", null, false);
        } catch (Exception e) {
            return ToolResult.structured("lib_remove", "ERROR", path,
                    e.getMessage() == null ? "Library removal failed; inspect the active project's metadata before retrying." : e.getMessage(), null, true);
        }
    }
}
