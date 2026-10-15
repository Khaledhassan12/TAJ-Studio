package pro.sketchware.ai.agent.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;

import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.live.LiveRegistry;

public final class AssetEditTool implements Tool {
    @Override public ToolSpec spec() {
        try {
            JSONObject props = new JSONObject();
            props.put("path", new JSONObject().put("type", "string").put("maxLength", 4096));
            props.put("mode", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"replace_all", "search_replace", "append"})));
            props.put("content", new JSONObject().put("type", "string").put("maxLength", 262144));
            props.put("search", new JSONObject().put("type", "string").put("maxLength", 65536));
            JSONObject schema = new JSONObject(); schema.put("type", "object"); schema.put("properties", props);
            schema.put("required", new JSONArray().put("path").put("mode").put("content"));
            return new ToolSpec("asset_edit", "Text-edits only a supported textual asset with conflict detection, atomic write and exact read-back verification.", schema);
        } catch (Exception e) { throw new IllegalStateException("Could not construct asset_edit schema.", e); }
    }
    @Override public Domain domain() { return Domain.ASSET; }

    @Override public ToolResult run(JSONObject args, ToolContext ctx) {
        File file = null;
        String path = null;
        String next = null;
        boolean existed = false;
        try {
            String relative = args.getString("path");
            String mode = args.getString("mode");
            String replacement = args.getString("content");
            if (!isTextAsset(relative)) {
                return ToolResult.structured("asset_edit", "UNSUPPORTED", relative,
                        "Text editing is limited to known text formats. Binary/unknown assets must not be rewritten as UTF-8.", null, true);
            }
            file = PathResolver.resolve(ctx.sc_id, "assets/" + relative);
            path = file.getAbsolutePath();
            if (!file.isFile()) return ToolResult.structured("asset_edit", "NOT_FOUND", path, "Asset is not an existing regular file.", null, true);
            if (!ctx.checkPermission(path, true)) return ToolResult.structured("asset_edit", "DENIED", path, "Write permission was denied.", null, true);

            String beforeHash = DomainToolHelper.sha256(file);
            String original = DomainToolHelper.readUtf8(file);
            if (!beforeHash.equals(DomainToolHelper.sha256(file))) {
                return ToolResult.structured("asset_edit", "CONFLICT", path,
                        "Asset changed while it was being read; no edit was applied.", null, true);
            }
            existed = true;
            switch (mode) {
                case "replace_all": next = replacement; break;
                case "append": next = original + (original.endsWith("\n") ? "" : "\n") + replacement; break;
                case "search_replace":
                    String search = args.optString("search", "");
                    if (search.isEmpty()) return ToolResult.structured("asset_edit", "INVALID_ARGUMENTS", path,
                            "Search text must not be empty; no edit was made.", null, true);
                    if (!original.contains(search)) return ToolResult.structured("asset_edit", "NO_MATCH", path,
                            "Search text was not found; no edit was made.", null, true);
                    next = original.replace(search, replacement);
                    break;
                default: return ToolResult.structured("asset_edit", "INVALID_ARGUMENTS", path, "Unsupported edit mode.", null, true);
            }

            DomainToolHelper.backup(path);
            DomainToolHelper.atomicWrite(file, next, beforeHash);
            if (!next.equals(DomainToolHelper.readUtf8(file))) {
                boolean restored = DomainToolHelper.restoreIfUnchanged(path, DomainToolHelper.contentSha256(next));
                return ToolResult.structured("asset_edit", "VERIFICATION_FAILED", path,
                        "Read-back did not match the requested text. Original restoration " + (restored ? "was verified." : "could not be safely verified."), null, true);
            }
            LiveRegistry.notifyChanged("assets", path);
            return ToolResult.structured("asset_edit", "SUCCESS", path, "Exact UTF-8 read-back verified.", null, false);
        } catch (Exception e) {
            boolean restored = false;
            try {
                if (existed && file != null && path != null && next != null && file.isFile()
                        && DomainToolHelper.contentSha256(next).equals(DomainToolHelper.sha256(file))) {
                    restored = DomainToolHelper.restoreIfUnchanged(path, DomainToolHelper.contentSha256(next));
                }
            } catch (Exception ignored) {}
            String message = e.getMessage() == null ? "Asset edit failed; inspect the target before retrying." : e.getMessage();
            if (restored) message += " Original asset restoration was verified.";
            return ToolResult.structured("asset_edit", "ERROR", path, message, null, true);
        }
    }

    private static boolean isTextAsset(String path) {
        if (path == null) return false;
        String name = path.toLowerCase(java.util.Locale.ROOT);
        String[] suffixes = {".txt", ".md", ".json", ".xml", ".html", ".css", ".js", ".ts",
                ".kt", ".java", ".properties", ".yaml", ".yml", ".csv", ".svg", ".gradle", ".kts"};
        for (String suffix : suffixes) if (name.endsWith(suffix)) return true;
        return false;
    }
}
