package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.ai.live.LiveRegistry;

public final class ResMoveCopyTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("fromFolder", new JSONObject().put("type", "string"));
            props.put("fromName", new JSONObject().put("type", "string"));
            props.put("toFolder", new JSONObject().put("type", "string"));
            props.put("toName", new JSONObject().put("type", "string"));
            props.put("op", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"move", "copy"})));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("fromFolder").put("fromName").put("toFolder").put("toName").put("op"));
            return new ToolSpec("res_move_copy", "Moves or copies a resource file.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.RES;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String sc_id = ctx.sc_id;
        File source = PathResolver.resolve(sc_id, "res/" + args.getString("fromFolder") + "/" + args.getString("fromName"));
        File destination = PathResolver.resolve(sc_id, "res/" + args.getString("toFolder") + "/" + args.getString("toName"));
        String fromPath = source.getAbsolutePath();
        String toPath = destination.getAbsolutePath();
        String op = args.getString("op");

        if (!FileUtil.isExistFile(fromPath)) return new ToolResult("Source not found.", true);
        if ("move".equals(op) && !ctx.checkPermission(fromPath, true)) return new ToolResult("Denied.", true);
        if (!ctx.checkPermission(toPath, FileUtil.isExistFile(toPath))) return new ToolResult("Denied.", true);

        if ("copy".equals(op)) {
            FileUtil.copyFile(fromPath, toPath);
        } else {
            FileUtil.moveFile(fromPath, toPath);
            LiveRegistry.notifyChanged(args.getString("fromFolder"), fromPath);
        }
        LiveRegistry.notifyChanged(args.getString("toFolder"), toPath);
        return new ToolResult("verified: " + op + " done to " + toPath, false);
    }
}
