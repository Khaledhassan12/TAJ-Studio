package pro.sketchware.ai.agent;

import org.json.JSONObject;
import a.a.a.yq;
import a.a.a.jC;
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

        DesignActivity activity = ctx.activity.get();
        if (activity == null) return new ToolResult("Activity lost", true);

        Context context = activity.getApplicationContext();
        yq generator = new yq(context, ctx.sc_id);
        String code = generator.getFileSrc(filename, jC.b(ctx.sc_id), jC.a(ctx.sc_id), jC.c(ctx.sc_id));
        
        if (code.isEmpty()) return new ToolResult("File not found or empty", true);
        return new ToolResult(code, false);
    }
}
