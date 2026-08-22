package pro.sketchware.ai.agent;

import org.json.JSONObject;
import com.topjohnwu.superuser.Shell;
import java.util.List;

public class ReadLogcatTool implements Tool {

    @Override
    public ToolSpec spec() {
        JSONObject params = new JSONObject();
        try {
            JSONObject limit = new JSONObject();
            limit.put("type", "integer");
            limit.put("description", "Number of lines to read");
            JSONObject props = new JSONObject();
            props.put("limit", limit);
            params.put("type", "object");
            params.put("properties", props);
        } catch (Exception ignored) {}
        return new ToolSpec("read_logcat", "Reads the last N lines of system logcat", params);
    }

    @Override
    public Domain domain() {
        return Domain.PROJECT;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) {
        int limit = args.optInt("limit", 100);
        List<String> logs = Shell.cmd("logcat -d -t " + limit).exec().getOut();
        return new ToolResult(String.join("\n", logs), false);
    }
}
