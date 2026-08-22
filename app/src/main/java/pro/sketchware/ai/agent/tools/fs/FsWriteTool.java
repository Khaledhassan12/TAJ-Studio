package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.agent.tools.DomainToolHelper;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.ai.live.LiveRegistry;

public final class FsWriteTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("path", new JSONObject().put("type", "string"));
            props.put("content", new JSONObject().put("type", "string"));
            props.put("expectContains", new JSONObject().put("type", "string"));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("path").put("content"));
            return new ToolSpec("fs_write", "Writes a file with PathResolver and structured result.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.FS;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String userPath = args.getString("path");
        String content = args.getString("content");
        String expect = args.optString("expectContains", "");
        String sc_id = args.optString("sc_id", ctx.sc_id);

        File f = PathResolver.resolve(sc_id, userPath);
        String path = f.getAbsolutePath();

        if (!ctx.checkPermission(path, f.exists())) return new ToolResult("Write denied.", true);

        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) FileUtil.makeDir(parent.getAbsolutePath());

        DomainToolHelper.backup(path);
        try {
            FileUtil.writeFile(path, content);
            String rb = FileUtil.readFile(path);
            if (!expect.isEmpty() && !rb.contains(expect)) {
                DomainToolHelper.restore(path);
                return ToolResult.structured("fs_write", "ERROR", path, "Verification failed: '" + expect + "' not found.", null, true);
            }
            
            notifyLive(path);
            
            return ToolResult.structured("fs_write", "SUCCESS", path, "verified", null, false);
        } catch (Exception e) {
            DomainToolHelper.restore(path);
            return ToolResult.structured("fs_write", "ERROR", path, e.getMessage(), new JSONArray(PathResolver.candidates(sc_id, f.getName())), true);
        }
    }

    private void notifyLive(String path) {
        if (path.contains("/layout/")) LiveRegistry.notifyChanged("layout", path);
        else if (path.contains("/values/")) LiveRegistry.notifyChanged("values", path);
        else if (path.contains("/java/")) LiveRegistry.notifyChanged("project_java", path);
    }
}
