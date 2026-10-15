package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.ai.live.LiveRegistry;

public final class AssetCreateTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("name", new JSONObject().put("type", "string"));
            props.put("dir", new JSONObject().put("type", "string"));
            props.put("type", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"folder", "file"})));
            props.put("content", new JSONObject().put("type", "string"));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("name").put("type"));
            return new ToolSpec("asset_create", "Creates an asset file or folder.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.ASSET;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String name = args.getString("name");
        String type = args.getString("type");
        String dir = args.optString("dir", "");
        String sc_id = ctx.sc_id;

        if (type.equals("file") && !name.contains(".")) {
            return new ToolResult("Asset file creation WITHOUT extension is refused.", true);
        }

        String relativePath = "assets/" + (dir.isEmpty() ? "" : dir + "/") + name;
        File target = PathResolver.resolve(sc_id, relativePath);
        String path = target.getAbsolutePath();

        if (type.equals("folder")) {
            if (!ctx.checkPermission(path, target.exists())) return new ToolResult("Denied.", true);
            FileUtil.makeDir(path);
            return new ToolResult(target.isDirectory() ? "verified: folder created @ " + path : "Failed.", !target.isDirectory());
        }

        if (!ctx.checkPermission(path, false)) return new ToolResult("Denied.", true);

        FileUtil.writeFile(path, args.optString("content", ""));
        if (FileUtil.isExistFile(path)) {
            LiveRegistry.notifyChanged("assets", path);
            return new ToolResult("verified: created @ " + path, false);
        }
        return new ToolResult("Failed.", true);
    }
}
