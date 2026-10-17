package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.File;

import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.live.LiveRegistry;

public final class FsMkdirTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            params.put("properties", new JSONObject().put("path",
                    new JSONObject().put("type", "string").put("maxLength", 4096)));
            params.put("required", new JSONArray().put("path"));
            return new ToolSpec("fs_mkdir",
                    "Creates and verifies a project directory, refuses file/directory conflicts, and notifies the affected live manager.",
                    params);
        } catch (Exception e) {
            throw new IllegalStateException("Could not construct fs_mkdir schema.", e);
        }
    }

    @Override
    public Domain domain() {
        return Domain.FS;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) {
        File directory = null;
        String path = null;
        try {
            directory = PathResolver.resolve(ctx.sc_id, args.getString("path"));
            path = directory.getAbsolutePath();

            if (directory.exists()) {
                if (directory.isDirectory()) {
                    return ToolResult.structured("fs_mkdir", "ALREADY_EXISTS", path,
                            "The requested directory already exists; no change was needed.", null, false);
                }
                return ToolResult.structured("fs_mkdir", "CONFLICT", path,
                        "A non-directory file already occupies the requested path.", null, true);
            }
            if (!ctx.checkPermission(path, false)) {
                return ToolResult.structured("fs_mkdir", "DENIED", path,
                        "Directory creation permission was denied.", null, true);
            }
            if (!directory.mkdirs() && !directory.isDirectory()) {
                return ToolResult.structured("fs_mkdir", "ERROR", path,
                        "Directory creation failed; inspect for partially created parent directories.", null, true);
            }
            if (!directory.isDirectory()) {
                return ToolResult.structured("fs_mkdir", "VERIFICATION_FAILED", path,
                        "The requested directory was not present after creation.", null, true);
            }

            notifyLive(ctx.sc_id, path);
            return ToolResult.structured("fs_mkdir", "SUCCESS", path,
                    "Directory exists and was verified.", null, false);
        } catch (Exception e) {
            return ToolResult.structured("fs_mkdir", "ERROR", path,
                    e.getMessage() == null ? "Directory creation failed." : e.getMessage(), null, true);
        }
    }

    private static void notifyLive(String scId, String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith(new File(ProjectPaths.javaManagerRoot(scId)).getAbsolutePath().replace('\\', '/') + "/")) {
            LiveRegistry.notifyChanged("java_manager", path);
        } else if (normalized.startsWith(new File(ProjectPaths.assetsRoot(scId)).getAbsolutePath().replace('\\', '/') + "/")) {
            LiveRegistry.notifyChanged("assets", path);
        } else {
            String resourceRoot = new File(ProjectPaths.resourceRoot(scId)).getAbsolutePath().replace('\\', '/');
            if (normalized.startsWith(resourceRoot + "/")) {
                String relative = normalized.substring(resourceRoot.length() + 1);
                int slash = relative.indexOf('/');
                String domain = slash < 0 ? relative : relative.substring(0, slash);
                if (!domain.isEmpty()) LiveRegistry.notifyChanged(domain, path);
            }
        }
    }
}
