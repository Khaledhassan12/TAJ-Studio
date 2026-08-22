package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.util.ArrayList;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.utility.FileUtil;

public final class AssetListTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("dir", new JSONObject().put("type", "string"));
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
        String sc_id = args.optString("sc_id", ctx.sc_id);
        String root = ProjectPaths.assetsRoot(sc_id) + (dir.isEmpty() ? "" : "/" + dir);

        if (!FileUtil.isExistFile(root)) return new ToolResult("Asset dir not found: " + dir, true);

        ArrayList<String> files = new ArrayList<>();
        FileUtil.listDir(root, files);
        
        JSONArray arr = new JSONArray();
        for (String f : files) arr.put(new File(f).getName());
        return new ToolResult(arr.toString(), false);
    }
}
