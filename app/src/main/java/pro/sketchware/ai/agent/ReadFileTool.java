package pro.sketchware.ai.agent;

import org.json.JSONObject;
import a.a.a.yq;
import pro.sketchware.ai.agent.tools.JavaToolHelper;
import pro.sketchware.utility.FileUtil;
import android.content.Context;
import com.besome.sketch.design.DesignActivity;

public class ReadFileTool implements Tool {

    @Override
    public ToolSpec spec() {
        JSONObject params = new JSONObject();
        try {
            JSONObject path = new JSONObject();
            path.put("type", "string");
            path.put("description", "The name of the file to read (e.g. main.xml or MainActivity.java)");
            JSONObject props = new JSONObject();
            props.put("filename", path);
            params.put("type", "object");
            params.put("properties", props);
            params.put("required", new org.json.JSONArray().put("filename"));
        } catch (Exception ignored) {}
        return new ToolSpec("read_file", "Reads the source code of a project file", params);
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) {
        String filename = args.optString("filename");
        if (filename.isEmpty()) return new ToolResult("Filename is required", true);

        String path;
        if (filename.endsWith(".java") || filename.endsWith(".kt")) {
            path = JavaToolHelper.getProjectPackagePath(ctx.sc_id) + java.io.File.separator + filename;
        } else if (filename.equals("strings.xml") || filename.equals("colors.xml") || filename.equals("styles.xml")) {
            path = JavaToolHelper.getValuesPath(ctx.sc_id) + java.io.File.separator + filename;
        } else if (filename.endsWith(".xml")) {
            path = JavaToolHelper.getLayoutPath(ctx.sc_id) + java.io.File.separator + filename;
        } else {
            return new ToolResult("Unsupported file type for read_file: " + filename, true);
        }

        if (FileUtil.isExistFile(path)) {
            String content = FileUtil.readFile(path);
            if (content.isEmpty()) return new ToolResult("File exists but is empty: " + path, true);
            return new ToolResult(content, false);
        }

        // Fallback to Sketchware's generation logic if not in data dir
        DesignActivity activity = ctx.activity.get();
        if (activity == null) return new ToolResult("Activity lost", true);

        Context context = activity.getApplicationContext();
        yq generator = new yq(context, ctx.sc_id);
        String code = generator.getFileSrc(filename, a.a.a.jC.b(ctx.sc_id), a.a.a.jC.a(ctx.sc_id), a.a.a.jC.c(ctx.sc_id));
        
        if (code.isEmpty()) return new ToolResult("File not found: " + filename, true);
        return new ToolResult(code, false);
    }
}
