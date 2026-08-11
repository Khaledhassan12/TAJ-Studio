package pro.sketchware.ai.mcp;

import android.content.Context;

import org.json.JSONObject;

import pro.sketchware.ai.agent.tools.Tool;
import pro.sketchware.ai.agent.tools.ToolArgs;
import pro.sketchware.ai.agent.tools.ToolCtx;
import pro.sketchware.ai.agent.tools.ToolResult;
import pro.sketchware.ai.agent.tools.ToolSpec;

/**
 * [WHAT] One MCP server tool exposed to the agent as "mcp_<server>_<tool>" (P2-MCP).
 * [WHY] Real effect (§11): invoking it performs a JSON-RPC tools/call against the
 * configured server; the text content of the result becomes the ToolResult.
 * [HOW] Specs come from the cached tools/list (McpStore); execution happens on
 * the agent's background thread with McpClient's 10s timeouts — errors are
 * surfaced as honest text, never crashes.
 */
public class McpTool implements Tool {

    private final Context context;
    private final McpServerConfig server;
    private final String toolName;
    private final ToolSpec spec;

    public McpTool(Context context, McpServerConfig server, McpClient.McpToolInfo info, String registeredName) {
        this.context = context.getApplicationContext();
        this.server = server;
        this.toolName = info.name;
        String description = (info.description == null || info.description.isEmpty())
                ? "MCP tool '" + info.name + "' from server '" + server.name + "'"
                : info.description + " (MCP server '" + server.name + "')";
        this.spec = new ToolSpec(registeredName, description, info.inputSchemaJson);
    }

    @Override
    public ToolSpec spec() {
        return spec;
    }

    @Override
    public ToolResult execute(ToolArgs args, ToolCtx ctx) {
        // Re-read the server from the SSOT: it may have been disabled or deleted
        // between spec composition and invocation (honest behavior, no stale calls).
        McpServerConfig current = McpStore.get(context).findById(server.id);
        if (current == null) {
            return ToolResult.error("MCP server '" + server.name + "' no longer exists");
        }
        if (!current.enabled) {
            return ToolResult.error("MCP server '" + server.name + "' is disabled");
        }

        try {
            JSONObject arguments = new JSONObject(args.getRaw());
            String result = new McpClient(context, current).callTool(toolName, arguments);
            return ToolResult.success(result);
        } catch (Exception e) {
            return ToolResult.error(e.getMessage() != null ? e.getMessage() : "MCP tool call failed");
        }
    }
}
