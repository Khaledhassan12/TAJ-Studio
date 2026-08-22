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

public final class BlockCreateTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            params.put("properties", new JSONObject().put("spec", new JSONObject().put("type", "object")));
            params.put("required", new org.json.JSONArray().put("spec"));
            return new ToolSpec("block_create", "Creates a custom block.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.BLOCK;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        JSONObject newSpec = args.getJSONObject("spec");
        String id = newSpec.optString("name", "");
        if (id.isEmpty()) return new ToolResult("Block name is required in spec.", true);

        String path = ProjectPaths.blocksRoot("");
        ArrayList<HashMap<String, Object>> blocks = new ArrayList<>();
        if (FileUtil.isExistFile(path)) {
            blocks = GsonUtils.getGson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
        }
        if (blocks == null) blocks = new ArrayList<>();

        HashMap<String, Object> nextMap = GsonUtils.getGson().fromJson(newSpec.toString(), Helper.TYPE_MAP);
        blocks.add(nextMap);

        DomainToolHelper.backup(path);
        try {
            FileUtil.writeFile(path, GsonUtils.getGson().toJson(blocks));
            BlockLoader.refresh();
            LiveRegistry.notifyChanged("blocks", path);
            return new ToolResult("verified: block '" + id + "' created @ " + path, false);
        } catch (Exception e) {
            DomainToolHelper.restore(path);
            return new ToolResult("Failed: " + e.getMessage(), true);
        }
    }
}
