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

public final class ResEditTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("folder", new JSONObject().put("type", "string"));
            props.put("name", new JSONObject().put("type", "string"));
            props.put("mode", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"replace_all", "search_replace", "append"})));
            props.put("content", new JSONObject().put("type", "string"));
            props.put("search", new JSONObject().put("type", "string"));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("folder").put("name").put("mode").put("content"));
            return new ToolSpec("res_edit", "Edits an existing resource file via PathResolver with per-operation approval, conflict detection, and read-back verification.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.RES;
    }

    /** Existing resource edits are destructive and require per-operation approval. */
    @Override public RiskLevel riskLevel(JSONObject args) { return RiskLevel.DESTRUCTIVE; }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String folder = args.getString("folder");
        String name = args.getString("name");
        String mode = args.getString("mode");
        String sc_id = ctx.sc_id;

        if (!"replace_all".equals(mode) && !"search_replace".equals(mode) && !"append".equals(mode)) {
            return ToolResult.structured("res_edit", "INVALID_ARGUMENTS", null,
                    "Unsupported edit mode; no resource file was changed.", null, true);
        }
        if (!args.has("content") || args.isNull("content")) {
            return ToolResult.structured("res_edit", "INVALID_ARGUMENTS", null,
                    "The selected edit mode requires a content string; no resource file was changed.", null, true);
        }
        if ("search_replace".equals(mode)
                && (!args.has("search") || args.isNull("search") || args.optString("search", "").isEmpty())) {
            return ToolResult.structured("res_edit", "INVALID_ARGUMENTS", null,
                    "search_replace requires a non-empty search string; no resource file was changed.", null, true);
        }

        File f = PathResolver.resolve(sc_id, "res/" + folder + "/" + name);
        String path = f.getAbsolutePath();

        if (!f.exists()) {
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", "Not found: " + path);
            err.put("absolutePath", path);
            err.put("candidates", new JSONArray(PathResolver.candidates(sc_id, name)));
            return new ToolResult(err.toString(), true);
        }
        
        if (!ctx.checkPermission(path, true)) return new ToolResult("Denied.", true);

        String beforeHash = DomainToolHelper.sha256(f);
        String original = DomainToolHelper.readUtf8(f);
        if (!beforeHash.equals(DomainToolHelper.sha256(f))) {
            return new ToolResult("Conflict: the resource changed while it was being read. No edit was applied.", true);
        }
        String next = "";
        switch (mode) {
            case "replace_all" -> next = args.getString("content");
            case "append" -> next = original + "\n" + args.getString("content");
            case "search_replace" -> {
                String s = args.getString("search");
                String r = args.getString("content");
                if (!original.contains(s)) return new ToolResult("Pattern not found.", true);
                next = original.replace(s, r);
            }
        }

        if (name.endsWith(".xml") && !DomainToolHelper.verifyXml(next)) {
            return new ToolResult("Edit resulted in malformed XML.", true);
        }

        DomainToolHelper.backup(path);
        try {
            DomainToolHelper.atomicWrite(f, next, beforeHash);
            String actual = DomainToolHelper.readUtf8(f);
            if (!next.equals(actual)) throw new java.io.IOException("Read-back differs from the intended resource content.");
            if ("layout".equals(folder)) {
                DomainToolHelper.syncLayoutToDesigner(sc_id, name, next);
                if (!DomainToolHelper.verifyLiveLayout(sc_id, name, next)) {
                    throw new java.io.IOException("Persisted layout differs from the live Sketchware designer model.");
                }
            }
            LiveRegistry.notifyChanged(folder, path);
            return ToolResult.structured("res_edit", "SUCCESS", path,
                    "Exact file read-back verified" + ("layout".equals(folder) ? "; live designer model verified." : "."), null, false);
        } catch (Exception e) {
            boolean restored = false;
            try {
                if (f.isFile() && DomainToolHelper.contentSha256(next).equals(DomainToolHelper.sha256(f))) {
                    restored = DomainToolHelper.restoreIfUnchanged(path, DomainToolHelper.contentSha256(next));
                    if (restored && "layout".equals(folder)) {
                        DomainToolHelper.syncLayoutToDesigner(sc_id, name, original);
                        if (!DomainToolHelper.verifyLiveLayout(sc_id, name, original)) restored = false;
                    }
                }
            } catch (Exception ignored) {}
            String message = e.getMessage() == null ? "Edit failed." : e.getMessage();
            if (restored) message += " Original file/designer state restoration was verified.";
            else message += " Any partial disk/designer effect was not fully verified; inspect the project before retrying.";
            return ToolResult.structured("res_edit", "ERROR", path, message, null, true);
        }
    }
}
