package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.tools.DomainToolHelper;
import pro.sketchware.utility.FileUtil;

public final class FsMoveTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            params.put("properties", new JSONObject().put("src", new JSONObject().put("type", "string")).put("dst", new JSONObject().put("type", "string")));
            params.put("required", new JSONArray().put("src").put("dst"));
            return new ToolSpec("fs_move", "Moves a file or folder.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.FS;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        File source = PathResolver.resolve(ctx.sc_id, args.getString("src"));
        File destination = PathResolver.resolve(ctx.sc_id, args.getString("dst"));
        String src = source.getAbsolutePath();
        String dst = destination.getAbsolutePath();
        if (!source.exists()) return new ToolResult("Source not found.", true);
        if (src.equals(dst)) return new ToolResult("Source and destination are identical.", true);
        if (source.isDirectory() && dst.startsWith(src + File.separator)) {
            return new ToolResult("Cannot move a directory into itself or one of its descendants.", true);
        }
        if (destination.exists()) return new ToolResult("Destination already exists. Move it elsewhere first to prevent accidental data loss.", true);
        if (!ctx.checkPermission(src, true)) return new ToolResult("Denied.", true);
        if (!ctx.checkPermission(dst, false)) return new ToolResult("Denied.", true);

        if (DomainToolHelper.moveVerified(source, destination)) {
            return new ToolResult("verified: moved to " + dst, false);
        }
        return new ToolResult("Move failed safely: the source was preserved because a verified move could not be completed.", true);
    }
}
