package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.ai.live.LiveRegistry;

public final class ResEditTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("folder", new JSONObject().put("type", "string"));
            props.put("name", new JSONObject().put("type", "string"));
            props.put("mode", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"replace_all", "search_replace", "append"})));
            props.put("content", new JSONObject().put("type", "string"));
            props.put("search", new JSONObject().put("type", "string"));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("folder").put("name").put("mode"));
            return new ToolSpec("res_edit", "Edits a resource file via PathResolver.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.RES;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String folder = args.getString("folder");
        String name = args.getString("name");
        String mode = args.getString("mode");
        String sc_id = args.optString("sc_id", ctx.sc_id);
        
        File f = PathResolver.resolve(sc_id, "res/" + folder + "/" + name);
        String path = f.getAbsolutePath();

        if (!f.exists()) {
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", "Not found: " + path);
            err.put("absolutePath", path);
            err.put("candidates", new JSONArray(PathResolver.candidates(sc_id, name)));
            return new ToolResult(err.toString(), true);
        }
        
        if (!ctx.checkPermission(path, true)) return new ToolResult("Denied.", true);

        String original = FileUtil.readFile(path);
        String next = "";
        switch (mode) {
            case "replace_all" -> next = args.getString("content");
            case "append" -> next = original + "\n" + args.getString("content");
            case "search_replace" -> {
                String s = args.getString("search");
                String r = args.getString("content");
                if (!original.contains(s)) return new ToolResult("Pattern not found.", true);
                next = original.replace(s, r);
            }
        }

        if (name.endsWith(".xml") && !DomainToolHelper.verifyXml(next)) {
            return new ToolResult("Edit resulted in malformed XML.", true);
        }

        DomainToolHelper.backup(path);
        try {
            FileUtil.writeFile(path, next);
            if ("layout".equals(folder)) DomainToolHelper.syncLayoutToDesigner(sc_id, name, next);
            LiveRegistry.notifyChanged(folder, path);
            return new ToolResult("verified: content matches @ " + path, false);
        } catch (Exception e) {
            DomainToolHelper.restore(path);
            return new ToolResult("Edit failed: " + e.getMessage(), true);
        }
    }
}
