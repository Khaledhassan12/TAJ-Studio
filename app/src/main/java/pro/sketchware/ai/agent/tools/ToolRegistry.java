package pro.sketchware.ai.agent.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [WHAT] Registry of all available agent tools.
 */
public class ToolRegistry {
    private static final Map<String, Tool> tools = new HashMap<>();

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
