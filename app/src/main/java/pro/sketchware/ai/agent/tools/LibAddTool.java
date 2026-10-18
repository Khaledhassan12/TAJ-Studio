package pro.sketchware.ai.agent.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;

import pro.sketchware.ai.agent.Tool;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.GsonUtils;
import pro.sketchware.ai.live.LiveRegistry;

public final class LibAddTool implements Tool {
    @Override public ToolSpec spec() {
        try {
            JSONObject props = new JSONObject();
            props.put("id", new JSONObject().put("type", "string").put("maxLength", 1024));
            props.put("sc_id", new JSONObject().put("type", "string").put("maxLength", 128));
            JSONObject schema = new JSONObject(); schema.put("type", "object"); schema.put("properties", props);
            schema.put("required", new JSONArray().put("id"));
            return new ToolSpec("lib_add", "Adds a local library entry to the active project's metadata with approval and write verification.", schema);
        } catch (Exception e) { throw new IllegalStateException("Could not construct lib_add schema.", e); }
    }
    @Override public Domain domain() { return Domain.LIB; }
    @Override public RiskLevel riskLevel(JSONObject args) { return RiskLevel.DESTRUCTIVE; }
    @Override public String approvalSummary(JSONObject args, ToolContext ctx) {
        String id = args == null ? "(id missing)" : args.optString("id", "(id missing)");
        return "Active-project library metadata: " + new FilePathUtil().getPathLocalLibrary(ctx.sc_id) + "; add library: " + id;
    }

    @Override public ToolResult run(JSONObject args, ToolContext ctx) {
        String path = null;
        File file = null;
        String serialized = null;
        try {
            String id = args.getString("id").trim();
            if (id.isEmpty()) return ToolResult.structured("lib_add", "INVALID_ARGUMENTS", null, "Library ID must not be empty.", null, true);
            String scId = ctx.sc_id;
            if (scId == null || !scId.matches("[A-Za-z0-9_-]+")) {
                return ToolResult.structured("lib_add", "INVALID_PROJECT", null, "No valid active project is bound to this task.", null, true);
            }
            // The model cannot redirect the write to a different project's library metadata.
            path = new FilePathUtil().getPathLocalLibrary(scId);
            file = new File(path);
            if (file.exists() && !file.isFile()) return ToolResult.structured("lib_add", "ERROR", path, "Library metadata path is not a regular file.", null, true);

            String beforeHash = file.isFile() ? DomainToolHelper.sha256(file) : null;
            ArrayList<String> libs = new ArrayList<>();
            if (file.isFile()) {
                String content = DomainToolHelper.readUtf8(file);
                if (!beforeHash.equals(DomainToolHelper.sha256(file))) {
                    return ToolResult.structured("lib_add", "CONFLICT", path, "Library metadata changed while being read; no update was applied.", null, true);
                }
                libs = GsonUtils.getGson().fromJson(content, new com.google.gson.reflect.TypeToken<ArrayList<String>>(){}.getType());
            }
            if (libs == null) return ToolResult.structured("lib_add", "INVALID_DATA", path, "Library metadata could not be decoded as a list.", null, true);
            if (libs.contains(id)) return ToolResult.structured("lib_add", "ALREADY_PRESENT", path, "Library is already registered in the active project; no change was needed.", null, false);
            if (!ctx.checkPermission(path, file.exists())) return ToolResult.structured("lib_add", "DENIED", path, "Write permission was denied.", null, true);

            libs.add(id);
            serialized = GsonUtils.getGson().toJson(libs);
            if (file.isFile()) DomainToolHelper.backup(path, scId);
            DomainToolHelper.atomicWrite(file, serialized, beforeHash);
            if (!serialized.equals(DomainToolHelper.readUtf8(file))) {
                boolean restored = file.isFile() && DomainToolHelper.restoreIfUnchanged(path, DomainToolHelper.contentSha256(serialized));
                return ToolResult.structured("lib_add", "VERIFICATION_FAILED", path,
                        "Library metadata read-back failed. Original restoration " + (restored ? "was verified." : "could not be safely verified."), null, true);
            }
            LiveRegistry.notifyChanged("libs", path);
            return ToolResult.structured("lib_add", "SUCCESS", path, "Library metadata write and exact read-back verified for the active project.", null, false);
        } catch (Exception e) {
            boolean restored = false;
            try {
                if (file != null && file.isFile() && serialized != null
                        && DomainToolHelper.contentSha256(serialized).equals(DomainToolHelper.sha256(file))) {
                    restored = DomainToolHelper.restoreIfUnchanged(path, DomainToolHelper.contentSha256(serialized));
                }
            } catch (Exception ignored) {}
            String message = e.getMessage() == null ? "Library registration failed." : e.getMessage();
            if (restored) message += " Original library metadata restoration was verified.";
            return ToolResult.structured("lib_add", "ERROR", path, message, null, true);
        }
    }
}
