package pro.sketchware.ai.agent.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pro.sketchware.ai.agent.tools.impl.*;

/**
 * [WHAT] Registry of all available agent tools.
 */
public class ToolRegistry {
    private static final Map<String, Tool> tools = new HashMap<>();

    static {
        register(new FileTools.ReadFileTool());
        register(new FileTools.ListFilesTool());
        register(new FileTools.WriteFileTool());
        register(new FileTools.CreateFileTool());
        register(new FileTools.PatchFileTool());
        register(new FileTools.DeleteFileTool());
        register(new SearchTool());
        register(new ProjectTools.RunBuildTool());
        register(new ProjectTools.InspectProjectTool());
        register(new ProjectTools.ReadBuildErrorTool());
    }

    public static void register(Tool tool) {
        tools.put(tool.spec().name, tool);
    }

    public static Tool get(String name) {
        return tools.get(name);
    }

    public static List<Tool> list() {
        return new ArrayList<>(tools.values());
    }
}
