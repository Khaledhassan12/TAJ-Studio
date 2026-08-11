package pro.sketchware.ai.agent.tools;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pro.sketchware.ai.agent.tools.impl.*;

/**
 * [WHAT] Registry of all available agent tools.
 */
public class ToolRegistry {
    private static final String TAG = "ToolRegistry";
    public static final String IMAGE_GEN_TOOL = "generate_image";
    public static final String WEB_SEARCH_TOOL = "web_search";

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

    public static synchronized void register(Tool tool) {
        tools.put(tool.spec().name, tool);
    }

    public static synchronized void unregister(String name) {
        tools.remove(name);
    }

    /**
     * [P2-IG] Registers the generate_image tool IFF Image Generation is enabled.
     * Single writer for this slot (R5/R16); re-checked on AiManager resume and
     * before every agent turn.
     */
    public static synchronized void syncImageGen(Context context) {
        boolean enabled = pro.sketchware.ai.images.ImageGenSettings.get(context).isEnabled();
        if (enabled && !tools.containsKey(IMAGE_GEN_TOOL)) {
            register(new ImageGenTool());
        } else if (!enabled) {
            tools.remove(IMAGE_GEN_TOOL);
        }
        Log.i(TAG, "syncImageGen: " + IMAGE_GEN_TOOL + " " + (enabled ? "REGISTERED" : "NOT REGISTERED") + " (enabled=" + enabled + ")");
    }

    /**
     * [P2-WS] Registers the web_search tool IFF Web Search is enabled.
     */
    public static synchronized void syncWebSearch(Context context) {
        boolean enabled = pro.sketchware.ai.websearch.WebSearchSettings.get(context).isEnabled();
        if (enabled && !tools.containsKey(WEB_SEARCH_TOOL)) {
            register(new WebSearchTool());
        } else if (!enabled) {
            tools.remove(WEB_SEARCH_TOOL);
        }
        Log.i(TAG, "syncWebSearch: " + WEB_SEARCH_TOOL + " " + (enabled ? "REGISTERED" : "NOT REGISTERED") + " (enabled=" + enabled + ")");
    }

    public static synchronized Tool get(String name) {
        return tools.get(name);
    }

    public static synchronized List<Tool> list() {
        return new ArrayList<>(tools.values());
    }
}
