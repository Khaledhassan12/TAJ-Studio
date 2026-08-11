package pro.sketchware.ai.agent.tools;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import pro.sketchware.ai.agent.tools.impl.*;
import pro.sketchware.ai.mcp.McpClient;
import pro.sketchware.ai.mcp.McpServerConfig;
import pro.sketchware.ai.mcp.McpStore;
import pro.sketchware.ai.mcp.McpTool;

/**
 * [WHAT] Registry of all available agent tools.
 */
public class ToolRegistry {
    private static final String TAG = "ToolRegistry";
    public static final String IMAGE_GEN_TOOL = "generate_image";
    public static final String WEB_SEARCH_TOOL = "web_search";
    public static final String CONV_SEARCH_TOOL = "search_conversations";
    // [P2-AU] Automation tools (gated by auto_tasks_loops, D17).
    public static final String CREATE_TASK_TOOL = "create_task";
    public static final String LIST_TASKS_TOOL = "list_tasks";
    public static final String DELETE_TASK_TOOL = "delete_task";
    public static final String START_LOOP_TOOL = "start_loop";
    public static final String STOP_LOOP_TOOL = "stop_loop";

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

    /**
     * [P2-CS] Registers the search_conversations tool IFF Access is enabled.
     */
    public static synchronized void syncConversationSearch(Context context) {
        boolean enabled = pro.sketchware.ai.search.ConversationSearchSettings.get(context).isAccessEnabled();
        if (enabled && !tools.containsKey(CONV_SEARCH_TOOL)) {
            register(new ConversationSearchTool());
        } else if (!enabled) {
            tools.remove(CONV_SEARCH_TOOL);
        }
        Log.i(TAG, "syncConversationSearch: " + CONV_SEARCH_TOOL + " " + (enabled ? "REGISTERED" : "NOT REGISTERED") + " (enabled=" + enabled + ")");
    }

    /** Prefix of all MCP server tools (D16). */
    public static final String MCP_TOOL_PREFIX = "mcp_";

    /**
     * [P2-AU] Registers the Tasks & Loops tools IFF auto_tasks_loops is enabled
     * (D17). Rebuilds on every toggle; re-checked on AiManager resume and
     * before every agent turn. Single reader of the SSOT flag for registration.
     */
    public static synchronized void syncAutomation(Context context) {
        boolean enabled = pro.sketchware.ai.automation.AutomationSettings.get(context).isTasksLoopsEnabled();
        if (enabled && !tools.containsKey(CREATE_TASK_TOOL)) {
            register(new CreateTaskTool());
            register(new ListTasksTool());
            register(new DeleteTaskTool());
            register(new StartLoopTool());
            register(new StopLoopTool());
        } else if (!enabled) {
            tools.remove(CREATE_TASK_TOOL);
            tools.remove(LIST_TASKS_TOOL);
            tools.remove(DELETE_TASK_TOOL);
            tools.remove(START_LOOP_TOOL);
            tools.remove(STOP_LOOP_TOOL);
        }
        Log.i(TAG, "syncAutomation: automation tools " + (enabled ? "REGISTERED" : "NOT REGISTERED") + " (enabled=" + enabled + ")");
    }

    /**
     * [P2-MCP] Registers tools of ENABLED MCP servers from the cached tools/list
     * as "mcp_<server>_<tool>" (sanitized). Cache is refreshed in the background
     * by McpStore.refreshToolsAsync() on session start and on store change.
     * Disabled servers contribute nothing (honest, RISK-5 class).
     */
    public static synchronized void syncMcp(Context context) {
        // Single writer: clear stale MCP tools, then rebuild from the SSOT.
        List<String> stale = new ArrayList<>();
        for (String name : tools.keySet()) {
            if (name.startsWith(MCP_TOOL_PREFIX)) stale.add(name);
        }
        for (String name : stale) tools.remove(name);

        int registered = 0;
        McpStore store = McpStore.get(context);
        for (McpServerConfig server : store.list()) {
            if (!server.enabled) continue;
            for (McpClient.McpToolInfo info : store.readToolsCache(server.id)) {
                String toolName = MCP_TOOL_PREFIX + sanitizeName(server.name) + "_" + sanitizeName(info.name);
                register(new McpTool(context, server, info, toolName));
                registered++;
            }
        }
        Log.i(TAG, "syncMcp: " + registered + " MCP tools REGISTERED");
    }

    /** Tool names must be model-safe: lowercase [a-z0-9_], collapsed, non-empty. */
    private static String sanitizeName(String raw) {
        if (raw == null) return "x";
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toLowerCase(Locale.US).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) sb.append(c);
            else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') sb.append('_');
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '_') sb.deleteCharAt(sb.length() - 1);
        return sb.length() == 0 ? "x" : sb.toString();
    }

    public static synchronized Tool get(String name) {
        return tools.get(name);
    }

    public static synchronized List<Tool> list() {
        return new ArrayList<>(tools.values());
    }
}
