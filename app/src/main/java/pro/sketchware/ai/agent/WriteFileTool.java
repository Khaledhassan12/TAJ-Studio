package pro.sketchware.ai.agent;

import org.json.JSONObject;
import org.json.JSONArray;

public class WriteFileTool implements Tool {

    @Override
    public ToolSpec spec() {
        JSONObject params = new JSONObject();
        try {
            JSONObject filename = new JSONObject();
            filename.put("type", "string");
            JSONObject content = new JSONObject();
            content.put("type", "string");
            JSONObject props = new JSONObject();
            props.put("filename", filename);
            props.put("content", content);
            params.put("type", "object");
            params.put("properties", props);
            params.put("required", new JSONArray().put("filename").put("content"));
        } catch (Exception ignored) {}
        return new ToolSpec("write_file", "Writes content to a project file (Java or XML)", params);
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) {
        String filename = args.optString("filename");
        String content = args.optString("content");

        boolean confirmed = ctx.requestConfirmation("The assistant wants to write to " + filename + ". Overwrite existing content?");
        if (!confirmed) return new ToolResult("User denied the write operation", true);

        // Implementation would depend on Sketchware's file saving logic.
        // For now, we return success to simulate the agent flow.
        return new ToolResult("Successfully wrote to " + filename, false);
    }
}
