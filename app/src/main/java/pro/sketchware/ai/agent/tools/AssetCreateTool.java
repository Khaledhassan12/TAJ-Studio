package pro.sketchware.ai.agent.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;

import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.live.LiveRegistry;

public final class AssetCreateTool implements Tool {
    @Override public ToolSpec spec() {
        try {
            JSONObject props = new JSONObject();
            props.put("name", new JSONObject().put("type", "string").put("maxLength", 4096));
            props.put("dir", new JSONObject().put("type", "string").put("maxLength", 4096));
            props.put("type", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"folder", "file"})));
            props.put("content", new JSONObject().put("type", "string").put("maxLength", 262144));
            JSONObject schema = new JSONObject(); schema.put("type", "object"); schema.put("properties", props);
            schema.put("required", new JSONArray().put("name").put("type"));
            return new ToolSpec("asset_create", "Creates a new asset file or folder without overwriting existing user data.", schema);
        } catch (Exception e) { throw new IllegalStateException("Could not construct asset_create schema.", e); }
    }
    @Override public Domain domain() { return Domain.ASSET; }

    @Override public ToolResult run(JSONObject args, ToolContext ctx) {
        File target = null;
        String path = null;
        String type = "";
        String content = "";
        try {
            String name = args.getString("name");
            type = args.getString("type");
            content = args.optString("content", "");
            String dir = args.optString("dir", "");
            if (!"folder".equals(type) && !"file".equals(type)) {
                return ToolResult.structured("asset_create", "INVALID_ARGUMENTS", null, "Unsupported asset type.", null, true);
            }
            if ("file".equals(type) && !name.contains(".")) {
                return ToolResult.structured("asset_create", "INVALID_ARGUMENTS", null,
                        "Asset file creation without an extension is refused; specify the correct file type.", null, true);
            }

            String relative = "assets/" + (dir.isEmpty() ? "" : dir + "/") + name;
            target = PathResolver.resolve(ctx.sc_id, relative);
            path = target.getAbsolutePath();
            if (target.exists()) return ToolResult.structured("asset_create", "CONFLICT", path,
                    "Destination already exists and was not overwritten.", null, true);
            if (!ctx.checkPermission(path, false)) return ToolResult.structured("asset_create", "DENIED", path, "Create permission was denied.", null, true);

            if ("folder".equals(type)) {
                if (!target.mkdirs() && !target.isDirectory()) {
                    return ToolResult.structured("asset_create", "ERROR", path,
                            "Folder creation failed; inspect the path for partial parent-directory creation.", null, true);
                }
                if (!target.isDirectory()) return ToolResult.structured("asset_create", "VERIFICATION_FAILED", path,
                        "The requested folder is not present after creation.", null, true);
                LiveRegistry.notifyChanged("assets", path);
                return ToolResult.structured("asset_create", "SUCCESS", path, "Folder exists and was verified.", null, false);
            }

            DomainToolHelper.atomicWrite(target, content, null);
            if (!target.isFile() || !content.equals(DomainToolHelper.readUtf8(target))) {
                return ToolResult.structured("asset_create", "VERIFICATION_FAILED", path,
                        "Created asset content did not match after read-back.", null, true);
            }
            LiveRegistry.notifyChanged("assets", path);
            return ToolResult.structured("asset_create", "SUCCESS", path,
                    "New asset file content verified by exact UTF-8 read-back.", null, false);
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Asset creation failed." : e.getMessage();
            return ToolResult.structured("asset_create", "ERROR", path,
                    message + " Inspect the destination before retrying; a partial create may have occurred.", null, true);
        }
    }
}
