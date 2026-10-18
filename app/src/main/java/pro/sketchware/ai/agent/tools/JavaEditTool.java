package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.util.Locale;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.live.LiveRegistry;

public final class JavaEditTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("target", new JSONObject().put("type", "string")
                    .put("enum", new JSONArray(new String[]{"manager", "project"})));
            props.put("path", new JSONObject().put("type", "string").put("minLength", 1).put("maxLength", 4096));
            props.put("mode", new JSONObject().put("type", "string")
                    .put("enum", new JSONArray(new String[]{"replace_all", "search_replace", "append"})));
            props.put("content", new JSONObject().put("type", "string").put("maxLength", 262144));
            props.put("search", new JSONObject().put("type", "string").put("maxLength", 65536));
            props.put("sc_id", new JSONObject().put("type", "string").put("maxLength", 128));
            params.put("properties", props);
            params.put("required", new JSONArray().put("target").put("path").put("mode").put("content"));
            return new ToolSpec("java_edit",
                    "Edits an existing Java/Kotlin source file in the active project's Java Manager or generated Java source root. Existing-file edits require per-operation approval and are verified by exact read-back.",
                    params);
        } catch (Exception e) {
            throw new IllegalStateException("Could not construct java_edit schema.", e);
        }
    }

    @Override
    public Domain domain() {
        return Domain.JAVA;
    }

    /** Editing an existing source file is destructive regardless of persistent permission mode. */
    @Override
    public RiskLevel riskLevel(JSONObject args) {
        return RiskLevel.DESTRUCTIVE;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String target = args.getString("target");
        String userPath = args.getString("path");
        String mode = args.getString("mode");
        String sc_id = ctx.sc_id;

        if (!"manager".equals(target) && !"project".equals(target)) {
            return ToolResult.structured("java_edit", "INVALID_ARGUMENTS", null,
                    "target must be manager or project; no source file was changed.", null, true);
        }
        if (userPath.trim().isEmpty() || userPath.contains("\\") || userPath.startsWith("/")
                || userPath.contains("../") || userPath.equals("..")) {
            return ToolResult.structured("java_edit", "INVALID_ARGUMENTS", null,
                    "path must be a non-empty project-relative Java/Kotlin source path; no source file was changed.",
                    null, true);
        }
        if (!"replace_all".equals(mode) && !"search_replace".equals(mode) && !"append".equals(mode)) {
            return ToolResult.structured("java_edit", "INVALID_ARGUMENTS", null,
                    "Unsupported edit mode; no source file was changed.", null, true);
        }
        if (!args.has("content") || args.isNull("content")) {
            return ToolResult.structured("java_edit", "INVALID_ARGUMENTS", null,
                    "The selected edit mode requires a content string; no source file was changed.", null, true);
        }
        if ("search_replace".equals(mode)
                && (!args.has("search") || args.isNull("search") || args.optString("search", "").isEmpty())) {
            return ToolResult.structured("java_edit", "INVALID_ARGUMENTS", null,
                    "search_replace requires a non-empty search string; no source file was changed.", null, true);
        }

        String lowerPath = userPath.toLowerCase(Locale.ROOT);
        if (!lowerPath.endsWith(".java") && !lowerPath.endsWith(".kt")) {
            return ToolResult.structured("java_edit", "UNSUPPORTED_FILE_TYPE", null,
                    "java_edit only modifies .java and .kt source files; no file was changed.", null, true);
        }

        File f = PathResolver.resolve(sc_id, ("manager".equals(target) ? userPath : "java/" + userPath));
        String path = f.getAbsolutePath();

        if (!f.exists()) {
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", "File not found: " + path);
            err.put("absolutePath", path);
            err.put("candidates", new JSONArray(PathResolver.candidates(sc_id, f.getName())));
            return new ToolResult(err.toString(), true);
        }
        if (!f.isFile()) {
            return ToolResult.structured("java_edit", "UNSUPPORTED_TARGET", path,
                    "The target is not a regular source file; no edit was applied.", null, true);
        }

        if (!ctx.checkPermission(path, true)) {
            return ToolResult.structured("java_edit", "DENIED", path,
                    "Write permission was denied; no source edit was applied.", null, true);
        }

        String beforeHash = DomainToolHelper.sha256(f);
        String original = DomainToolHelper.readUtf8(f);
        if (!beforeHash.equals(DomainToolHelper.sha256(f))) {
            return new ToolResult("Conflict: the source changed while it was being read. No edit was applied.", true);
        }
        String next;
        switch (mode) {
            case "replace_all" -> next = args.getString("content");
            case "append" -> next = original + "\n" + args.getString("content");
            case "search_replace" -> {
                String search = args.getString("search");
                String replacement = args.getString("content");
                if (!original.contains(search)) {
                    return ToolResult.structured("java_edit", "PATTERN_NOT_FOUND", path,
                            "The requested search text was not found; no edit was applied.", null, true);
                }
                next = original.replace(search, replacement);
            }
            default -> throw new IllegalStateException("Validated edit mode became unsupported.");
        }

        DomainToolHelper.backup(path, sc_id);
        try {
            DomainToolHelper.atomicWrite(f, next, beforeHash);
            String actual = DomainToolHelper.readUtf8(f);
            if (!next.equals(actual)) {
                boolean restored = DomainToolHelper.restoreIfUnchanged(path, DomainToolHelper.contentSha256(next));
                return ToolResult.structured("java_edit", "VERIFICATION_FAILED", path,
                        "Saved source does not match the requested edit. Original restoration "
                                + (restored ? "was verified." : "could not be safely verified."),
                        null, true);
            }
            LiveRegistry.notifyChanged("manager".equals(target) ? "java_manager" : "project_java", path);
            return ToolResult.structured("java_edit", "SUCCESS", path,
                    "Exact UTF-8 read-back verified; syntax/build validation was not performed by this tool.",
                    null, false);
        } catch (Exception e) {
            boolean restored = false;
            try {
                if (f.isFile() && DomainToolHelper.contentSha256(next).equals(DomainToolHelper.sha256(f))) {
                    restored = DomainToolHelper.restoreIfUnchanged(path, DomainToolHelper.contentSha256(next));
                }
            } catch (Exception ignored) {}
            String message = e.getMessage() == null ? "Edit failed." : e.getMessage();
            if (restored) message += " Original source restoration was verified.";
            return ToolResult.structured("java_edit", "ERROR", path, message, null, true);
        }
    }
}
