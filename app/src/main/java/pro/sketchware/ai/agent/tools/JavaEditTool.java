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

public final class JavaEditTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("target", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"manager", "project"})));
            props.put("path", new JSONObject().put("type", "string"));
            props.put("mode", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"replace_all", "search_replace", "append"})));
            props.put("content", new JSONObject().put("type", "string"));
            props.put("search", new JSONObject().put("type", "string"));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("target").put("path").put("mode"));
            return new ToolSpec("java_edit", "Edits a Java/Kotlin file via PathResolver.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.JAVA;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String target = args.getString("target");
        String userPath = args.getString("path");
        String mode = args.getString("mode");
        String sc_id = ctx.sc_id;

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
        
        if (!ctx.checkPermission(path, true)) return new ToolResult("Denied.", true);

        String beforeHash = DomainToolHelper.sha256(f);
        String original = DomainToolHelper.readUtf8(f);
        if (!beforeHash.equals(DomainToolHelper.sha256(f))) {
            return new ToolResult("Conflict: the source changed while it was being read. No edit was applied.", true);
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

        DomainToolHelper.backup(path);
        try {
            DomainToolHelper.atomicWrite(f, next, beforeHash);
            String actual = DomainToolHelper.readUtf8(f);
            if (!next.equals(actual)) {
                boolean restored = DomainToolHelper.restoreIfUnchanged(path, DomainToolHelper.contentSha256(next));
                return ToolResult.structured("java_edit", "VERIFICATION_FAILED", path,
                        "Saved source does not match the requested edit. Original restoration " + (restored ? "was verified." : "could not be safely verified."), null, true);
            }
            LiveRegistry.notifyChanged("manager".equals(target) ? "java_manager" : "project_java", path);
            return ToolResult.structured("java_edit", "SUCCESS", path,
                    "Exact UTF-8 read-back verified; syntax/build validation was not performed by this tool.", null, false);
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
