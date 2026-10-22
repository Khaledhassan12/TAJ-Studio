package pro.sketchware.ai.agent;

import android.text.TextUtils;

import com.besome.sketch.design.DesignActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * In-process MCP JSON-RPC adapter over the existing TAJ tool registry.
 *
 * This is deliberately a protocol adapter, not a second tool runtime or network server.
 * The first version exposes discovery and read-only inspection. Mutations stay in
 * AgentOrchestrator until execution-lock and approval handling can be shared directly.
 */
public final class McpBridge {
    public static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String SERVER_NAME = "taj-studio-agent";
    private static final String SERVER_VERSION = "1.0.0";

    private static final Set<String> READ_ONLY_TOOLS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "project_info", "list_projects", "list_project_structure",
                    "fs_list", "fs_info", "fs_read", "fs_search",
                    "java_list", "java_read", "res_list", "res_read",
                    "asset_list", "asset_read", "blocks_list", "block_read",
                    "manifest_read", "libs_list"
            )));

    private final ToolRegistry registry;
    private final Tool.ToolContext context;

    public McpBridge(ToolRegistry registry, Tool.ToolContext context) {
        if (registry == null) throw new IllegalArgumentException("ToolRegistry is required.");
        if (context == null) throw new IllegalArgumentException("ToolContext is required.");
        this.registry = registry;
        this.context = context;
    }

    /** Handles one MCP JSON-RPC request and returns a JSON-RPC response. Notifications return null. */
    public JSONObject handle(JSONObject request) {
        if (request == null) return error(null, -32600, "Invalid request: expected a JSON object.");
        Object id = request.opt("id");
        boolean notification = !request.has("id");
        if (!"2.0".equals(request.optString("jsonrpc"))) {
            return notification ? null : error(id, -32600, "jsonrpc must be 2.0.");
        }

        String method = request.optString("method", "");
        JSONObject params = request.optJSONObject("params");
        if (params == null) params = new JSONObject();

        try {
            JSONObject result;
            switch (method) {
                case "initialize":
                    result = initialize();
                    break;
                case "notifications/initialized":
                    return null;
                case "ping":
                    result = new JSONObject();
                    break;
                case "tools/list":
                    result = listTools();
                    break;
                case "resources/list":
                    result = listResources();
                    break;
                case "resources/read":
                    result = readResource(params.optString("uri", ""));
                    break;
                case "tools/call":
                    result = callReadOnlyTool(params);
                    break;
                default:
                    return notification ? null : error(id, -32601, "Method not found: " + safe(method));
            }
            return notification ? null : success(id, result);
        } catch (McpException e) {
            return notification ? null : error(id, e.code, e.getMessage());
        } catch (Exception e) {
            String message = e.getMessage();
            return notification ? null : error(id, -32603,
                    "MCP request failed safely: " + safe(TextUtils.isEmpty(message)
                            ? e.getClass().getSimpleName() : message));
        }
    }

    private JSONObject initialize() throws JSONException {
        JSONObject capabilities = new JSONObject();
        capabilities.put("tools", new JSONObject().put("listChanged", false));
        capabilities.put("resources", new JSONObject().put("subscribe", false).put("listChanged", false));
        JSONObject serverInfo = new JSONObject().put("name", SERVER_NAME).put("version", SERVER_VERSION);
        return new JSONObject().put("protocolVersion", PROTOCOL_VERSION)
                .put("capabilities", capabilities).put("serverInfo", serverInfo)
                .put("instructions", "In-process bridge only. Read-only tool calls are enabled; mutations must use AgentOrchestrator approval and project locking.");
    }

    private JSONObject listTools() throws JSONException {
        JSONArray tools = new JSONArray();
        // Advertise only tools that this bridge can actually execute. Listing every
        // registry tool while rejecting writes at call-time misleads MCP clients and
        // encourages unsafe retries through alternate paths.
        for (String name : READ_ONLY_TOOLS) {
            Tool tool = registry.get(name);
            Tool.ToolSpec spec = registry.spec(name);
            if (tool == null || spec == null) continue;
            JSONObject item = new JSONObject().put("name", spec.name)
                    .put("description", spec.description)
                    .put("inputSchema", new JSONObject(spec.parameters.toString()));
            item.put("annotations", new JSONObject().put("readOnlyHint", true)
                    .put("destructiveHint", false).put("idempotentHint", true));
            tools.put(item);
        }
        return new JSONObject().put("tools", tools);
    }

    private JSONObject listResources() throws JSONException {
        JSONArray resources = new JSONArray();
        resources.put(new JSONObject().put("uri", "taj://project/info")
                .put("name", "Active Sketchware project information")
                .put("description", "Project metadata for the project bound to the current editor session.")
                .put("mimeType", "application/json"));
        resources.put(new JSONObject().put("uri", "taj://project/structure")
                .put("name", "Active project structure")
                .put("description", "Read-only project structure from the existing TAJ tool executor.")
                .put("mimeType", "application/json"));
        return new JSONObject().put("resources", resources);
    }

    private JSONObject readResource(String uri) throws Exception {
        requireActiveProject();
        final String toolName;
        if ("taj://project/info".equals(uri)) {
            toolName = "project_info";
        } else if ("taj://project/structure".equals(uri)) {
            toolName = "list_project_structure";
        } else {
            throw new McpException(-32602, "Unknown resource URI.");
        }
        Tool.ToolResult result = runReadOnly(toolName, new JSONObject());
        if (result.error) throw new McpException(-32001, "Resource read failed: " + safe(result.content));
        JSONArray contents = new JSONArray().put(new JSONObject()
                .put("uri", uri).put("mimeType", "application/json").put("text", result.content));
        return new JSONObject().put("contents", contents);
    }

    private JSONObject callReadOnlyTool(JSONObject params) throws Exception {
        String name = params.optString("name", "");
        if (TextUtils.isEmpty(name)) throw new McpException(-32602, "Tool name is required.");
        if (!READ_ONLY_TOOLS.contains(name)) {
            throw new McpException(-32002,
                    "Mutation is not exposed through this adapter. Use AgentOrchestrator so the existing approval, cancellation, and project-lock safeguards remain authoritative.");
        }
        JSONObject args = params.optJSONObject("arguments");
        if (args == null) args = new JSONObject();
        Tool.ToolResult result = runReadOnly(name, args);
        JSONArray content = new JSONArray().put(new JSONObject()
                .put("type", "text").put("text", result.content == null ? "" : result.content));
        JSONObject response = new JSONObject().put("content", content).put("isError", result.error);
        if (result.error) response.put("structuredContent", new JSONObject()
                .put("status", "error").put("message", safe(result.content)));
        return response;
    }

    private Tool.ToolResult runReadOnly(String name, JSONObject args) throws Exception {
        requireActiveProject();
        Tool tool = registry.get(name);
        Tool.ToolSpec spec = registry.spec(name);
        if (tool == null || spec == null || !READ_ONLY_TOOLS.contains(name)) {
            throw new McpException(-32602, "Unknown or non-read-only tool.");
        }
        String validationError = ToolArgumentValidator.validate(spec, args);
        if (validationError != null) {
            throw new McpException(-32602, validationError);
        }
        requireActiveProject();
        Tool.ToolResult result = tool.run(args, context);
        requireActiveProject();
        return result;
    }

    private void requireActiveProject() throws McpException {
        if (TextUtils.isEmpty(context.sc_id)) {
            throw new McpException(-32010, "No active Sketchware project is bound to this MCP session.");
        }
        DesignActivity activity = context.activity.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            throw new McpException(-32010, "The editor session is no longer active.");
        }
        String activeId = DesignActivity.sc_id;
        if (TextUtils.isEmpty(activeId) || !context.sc_id.equals(activeId)) {
            throw new McpException(-32010, "The active project changed. Rebind the bridge to the current project.");
        }
    }

    private static JSONObject success(Object id, JSONObject result) throws JSONException {
        return new JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result);
    }

    private static JSONObject error(Object id, int code, String message) throws JSONException {
        return new JSONObject().put("jsonrpc", "2.0").put("id", id)
                .put("error", new JSONObject().put("code", code).put("message", safe(message)));
    }

    private static String safe(String value) {
        if (value == null) return "";
        String sanitized = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").trim();
        return sanitized.length() > 2000 ? sanitized.substring(0, 2000) + "…" : sanitized;
    }

    private static final class McpException extends Exception {
        final int code;
        McpException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
