package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.GsonUtils;
import mod.hey.studios.util.Helper;

public final class BlockReadTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            params.put("properties", new JSONObject().put("id", new JSONObject().put("type", "string")));
            params.put("required", new org.json.JSONArray().put("id"));
            return new ToolSpec("block_read", "Reads a block spec.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.BLOCK;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String id = args.getString("id");
        String blocksPath = ProjectPaths.blocksRoot("");

        if (FileUtil.isExistFile(blocksPath)) {
            ArrayList<HashMap<String, Object>> blocks = GsonUtils.getGson().fromJson(FileUtil.readFile(blocksPath), Helper.TYPE_MAP_LIST);
            if (blocks != null) {
                for (HashMap<String, Object> block : blocks) {
                    if (id.equals(block.get("name"))) {
                        return new ToolResult(new JSONObject(block).toString(), false);
                    }
                }
            }
        }
        return new ToolResult("Block not found.", true);
    }
}
