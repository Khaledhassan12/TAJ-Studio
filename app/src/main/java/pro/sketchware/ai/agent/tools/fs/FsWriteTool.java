package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.tools.DomainToolHelper;
import pro.sketchware.ai.live.LiveRegistry;

public final class FsWriteTool implements Tool {
    @Override
    public ToolSpec spec() {
        try {
            JSONObject props = new JSONObject();
            props.put("path", new JSONObject().put("type", "string").put("maxLength", 4096));
            props.put("content", new JSONObject().put("type", "string").put("maxLength", 262144));
            props.put("expectContains", new JSONObject().put("type", "string").put("maxLength", 4096));
            props.put("sc_id", new JSONObject().put("type", "string").put("maxLength", 128));
            JSONObject schema = new JSONObject();
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("required", new JSONArray().put("path").put("content"));
            return new ToolSpec("fs_write", "Atomically writes a project file with conflict detection and exact read-back verification.", schema);
        } catch (Exception e) {
            throw new IllegalStateException("Could not construct fs_write schema.", e);
        }
    }

    @Override public Domain domain() { return Domain.FS; }

    @Override
    public RiskLevel riskLevel(JSONObject args, ToolContext ctx) {
        try {
            if (args == null || !args.has("path")) return RiskLevel.DESTRUCTIVE;
            File target = PathResolver.resolve(ctx.sc_id, args.getString("path"));
            // New-file creation is non-destructive; replacing an existing file requires
            // a one-call approval even when persistent permission mode is set to "full".
            return target.isFile() ? RiskLevel.DESTRUCTIVE : RiskLevel.STANDARD;
        } catch (Exception e) {
            // Treat an unresolvable target conservatively; the executor will still reject it.
            return RiskLevel.DESTRUCTIVE;
        }
    }



    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) {
        File target = null;
        String path = null;
        String requestedContent = null;
        boolean existed = false;
        boolean layoutXml = false;
        String originalContent = null;
        try {
            String userPath = args.getString("path");
            requestedContent = args.getString("content");
            String expect = args.optString("expectContains", "");
            if (!expect.isEmpty() && !requestedContent.contains(expect)) {
                return ToolResult.structured("fs_write", "INVALID_ARGUMENTS", null,
                        "expectContains is not present in the requested content; no file was changed.", null, true);
            }

            target = PathResolver.resolve(ctx.sc_id, userPath);
            path = target.getAbsolutePath();
            String layoutRoot = new File(ProjectPaths.layoutDir(ctx.sc_id)).getCanonicalPath();
            String canonicalTarget = target.getCanonicalPath();
            layoutXml = target.getName().endsWith(".xml")
                    && canonicalTarget.startsWith(layoutRoot + File.separator);
            if (target.exists() && !target.isFile()) {
                return ToolResult.structured("fs_write", "ERROR", path,
                        "The target exists but is not a regular file.", null, true);
            }
            existed = target.isFile();
            if (layoutXml && !DomainToolHelper.verifyXml(requestedContent)) {
                return ToolResult.structured("fs_write", "INVALID_XML", path,
                        "Layout XML is malformed; no file or designer state was changed.", null, true);
            }
            if (!ctx.checkPermission(path, existed)) {
                return ToolResult.structured("fs_write", "DENIED", path, "Write permission was denied.", null, true);
            }

            String beforeHash = existed ? DomainToolHelper.sha256(target) : null;
            originalContent = existed ? DomainToolHelper.readUtf8(target) : null;
            if (existed) DomainToolHelper.backup(path, ctx.sc_id);
            DomainToolHelper.atomicWrite(target, requestedContent, beforeHash);

            String actual = DomainToolHelper.readUtf8(target);
            if (!requestedContent.equals(actual) || (!expect.isEmpty() && !actual.contains(expect))) {
                boolean restored = existed && DomainToolHelper.restoreIfUnchanged(
                        path, DomainToolHelper.contentSha256(requestedContent));
                return ToolResult.structured("fs_write", "VERIFICATION_FAILED", path,
                        "Read-back did not match the requested content. Original restoration "
                                + (restored ? "was verified." : "could not be safely verified; inspect the file before retrying."),
                        null, true);
            }

            if (layoutXml) {
                DomainToolHelper.syncLayoutToDesigner(ctx.sc_id, target.getName(), actual);
                if (!DomainToolHelper.verifyLiveLayout(ctx.sc_id, target.getName(), actual)) {
                    throw new java.io.IOException("Layout XML was written, but the live Sketchware designer model did not match it.");
                }
            }

            String hash = DomainToolHelper.sha256(target);
            // A successful layout write must refresh the active visual designer, not only emit a status.
            if (layoutXml) DomainToolHelper.refreshEditor(ctx, target.getName());
            notifyLive(path);
            return ToolResult.structured("fs_write", "SUCCESS", path,
                    "Exact UTF-8 read-back verified; SHA-256=" + hash
                            + (layoutXml ? "; live designer model read-back verified." : ""), null, false);
        } catch (Exception e) {
            boolean restored = false;
            try {
                if (path != null && requestedContent != null && target != null && target.isFile()
                        && DomainToolHelper.contentSha256(requestedContent).equals(DomainToolHelper.sha256(target))) {
                    if (existed) {
                        restored = DomainToolHelper.restoreIfUnchanged(
                                path, DomainToolHelper.contentSha256(requestedContent));
                        if (restored && layoutXml && originalContent != null) {
                            DomainToolHelper.syncLayoutToDesigner(ctx.sc_id, target.getName(), originalContent);
                            restored = DomainToolHelper.verifyLiveLayout(ctx.sc_id, target.getName(), originalContent);
                        }
                    } else {
                        Files.deleteIfExists(target.toPath());
                        restored = !target.exists();
                    }
                }
            } catch (Exception ignored) {}
            String message = safeMessage(e);
            if (restored) message += " Original file restoration was verified.";
            else if (path != null) message += " No unguarded rollback was attempted; inspect the target if the write may have started.";
            return ToolResult.structured("fs_write", "ERROR", path, message, null, true);
        }
    }

    private static String safeMessage(Exception error) {
        if (error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()) {
            return "Write failed; the file state must be inspected before retrying.";
        }
        String message = error.getMessage().replaceAll("[\\p{Cntrl}]", " ");
        return message.length() > 400 ? message.substring(0, 400) : message;
    }

    private void notifyLive(String path) {
        if (path == null) return;
        String normalized = path.replace('\\', '/');
        if (normalized.contains("/layout/")) LiveRegistry.notifyChanged("layout", path);
        else if (normalized.contains("/values/")) LiveRegistry.notifyChanged("values", path);
        else if (normalized.contains("/java/")) LiveRegistry.notifyChanged("project_java", path);
    }
}
