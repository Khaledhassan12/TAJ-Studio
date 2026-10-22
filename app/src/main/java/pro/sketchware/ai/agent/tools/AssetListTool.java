package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.util.ArrayList;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.utility.FileUtil;

public final class AssetListTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("dir", new JSONObject().put("type", "string").put("maxLength", 4096)
                    .put("description", "Directory relative to the assets root. Use an empty string for the root; do not prefix with assets/."));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            return new ToolSpec("asset_list", "Lists assets.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.ASSET;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String dir = args.optString("dir", "");
        String sc_id = ctx.sc_id;
        File rootDir = PathResolver.resolve(sc_id, "assets/" + dir);
        String root = rootDir.getAbsolutePath();

        // Match ManageAssetsActivity: create the assets root for fresh projects.
        if (!rootDir.exists() && !rootDir.mkdirs() && !rootDir.isDirectory()) {
            return ToolResult.structured("asset_list", "ERROR", root,
                    "Could not create the project's assets directory.", null, true);
        }
        if (!rootDir.isDirectory()) {
            return ToolResult.structured("asset_list", "NOT_FOUND", root,
                    "The requested assets path is not a directory.", null, true);
        }

        ArrayList<String> files = new ArrayList<>();
        FileUtil.listDir(root, files);
        
        JSONArray arr = new JSONArray();
        for (String f : files) arr.put(new File(f).getName());
        return new ToolResult(arr.toString(), false);
    }
}
