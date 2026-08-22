package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.GsonUtils;
import mod.hey.studios.util.Helper;
import mod.hey.studios.editor.manage.block.v2.BlockLoader;
import pro.sketchware.ai.live.LiveRegistry;

public final class BlockEditTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("id", new JSONObject().put("type", "string"));
            props.put("spec", new JSONObject().put("type", "object"));
            params.put("properties", props);
            params.put("required", new org.json.JSONArray().put("id").put("spec"));
            return new ToolSpec("block_edit", "Edits a custom block.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.BLOCK;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String id = args.getString("id");
        JSONObject newSpec = args.getJSONObject("spec");

        String path = ProjectPaths.blocksRoot("");
        ArrayList<HashMap<String, Object>> blocks = new ArrayList<>();
        if (FileUtil.isExistFile(path)) {
            blocks = GsonUtils.getGson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
        }
        if (blocks == null) return new ToolResult("No blocks to edit.", true);

        boolean found = false;
        HashMap<String, Object> nextMap = GsonUtils.getGson().fromJson(newSpec.toString(), Helper.TYPE_MAP);
        for (int i = 0; i < blocks.size(); i++) {
            if (id.equals(blocks.get(i).get("name"))) {
                blocks.set(i, nextMap);
                found = true;
                break;
            }
        }

        if (!found) return new ToolResult("Block not found: " + id, true);

        DomainToolHelper.backup(path);
        try {
            FileUtil.writeFile(path, GsonUtils.getGson().toJson(blocks));
            BlockLoader.refresh();
            LiveRegistry.notifyChanged("blocks", path);
            return new ToolResult("verified: block '" + id + "' edited @ " + path, false);
        } catch (Exception e) {
            DomainToolHelper.restore(path);
            return new ToolResult("Failed: " + e.getMessage(), true);
        }
    }
}
