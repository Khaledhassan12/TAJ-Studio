package pro.sketchware.ai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ToolRegistry {

    private final Map<String, Tool> tools = new HashMap<>();

    public ToolRegistry() {
        register(new BuildProjectTool());
        register(new ListProjectStructureTool());
        register(new ReadFileTool());
        register(new WriteFileTool());
        register(new OpenManagerTool());
        register(new ReadLogcatTool());
    }

    public void register(Tool tool) {
        tools.put(tool.spec().name, tool);
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public List<Tool> all() {
        return new ArrayList<>(tools.values());
    }

    public JSONArray getSpecsJson() {
        JSONArray arr = new JSONArray();
        for (Tool t : tools.values()) {
            JSONObject spec = new JSONObject();
            try {
                spec.put("name", t.spec().name);
                spec.put("description", t.spec().description);
                spec.put("parameters", t.spec().parameters);
                arr.put(spec);
            } catch (Exception ignored) {}
        }
        return arr;
    }
}
