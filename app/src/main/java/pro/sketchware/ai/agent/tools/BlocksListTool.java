package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.HashMap;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.GsonUtils;
import mod.hey.studios.util.Helper;

public final class BlocksListTool implements Tool {

    @Override
    public ToolSpec spec() {
        return new ToolSpec("blocks_list", "Lists custom blocks and palettes.", new JSONObject());
    }

    @Override
    public Domain domain() {
        return Domain.BLOCK;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String blocksPath = ProjectPaths.blocksRoot("");
        String palettePath = ProjectPaths.paletteRoot("");

        JSONObject res = new JSONObject();
        
        if (FileUtil.isExistFile(palettePath)) {
            ArrayList<HashMap<String, Object>> palettes = GsonUtils.getGson().fromJson(FileUtil.readFile(palettePath), Helper.TYPE_MAP_LIST);
            res.put("palettes", new JSONArray(palettes));
        }

        if (FileUtil.isExistFile(blocksPath)) {
            ArrayList<HashMap<String, Object>> blocks = GsonUtils.getGson().fromJson(FileUtil.readFile(blocksPath), Helper.TYPE_MAP_LIST);
            res.put("blocks", new JSONArray(blocks));
        }

        return new ToolResult(res.toString(), false);
    }
}
