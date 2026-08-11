package pro.sketchware.ai.mcp;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import pro.sketchware.ai.data.SecureKeyStore;

/**
 * [WHAT] Real MCP client: JSON-RPC 2.0 over Streamable HTTP or legacy SSE (P2-MCP, D16).
 * [WHY] Enables initialize → notifications/initialized → tools/list → tools/call
 * against user-added MCP servers so their tools become agent tools.
 * [HOW] Reuses the providers' OkHttp recipe; 10s timeouts; background threads
 * only (callers wrap in Executors — RISK-15 class). Every failure becomes a
 * typed honest message; this class never crashes the caller (§16).
 */
public class McpClient {

    /** Tool metadata parsed from tools/list (and cached as JSON by McpStore). */
    public static class McpToolInfo {
        public String name;
        public String description;
        public String inputSchemaJson;

        public McpToolInfo(String name, String description, String inputSchemaJson) {
            this.name = name;
            this.description = description;
            this.inputSchemaJson = inputSchemaJson;
        }
    }

    private static final OkHttpClient sharedClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    private final Context context;
    private final McpServerConfig config;
    private final SecureKeyStore keys;
    private final AtomicInteger idSeq = new AtomicInteger(1);
    private volatile String sessionId; // Streamable HTTP Mcp-Session-Id round-trip

    public McpClient(Context context, McpServerConfig config) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.keys = SecureKeyStore.get(this.context);
    }

    /**
     * Full handshake: initialize → notifications/initialized.
     * Returns a short human description of the server on success.
     */
    public String initialize() throws Exception {
        JSONObject params = new JSONObject();
        params.put("protocolVersion", "2025-03-26");
        params.put("capabilities", new JSONObject());
        params.put("clientInfo", new JSONObject().put("name", "TAJ Studio").put("version", "1.0"));

        JSONObject result = rpc("initialize", params);
        if (result == null) throw new Exception("MCP handshake failed: empty initialize result");

        // Notification carries no id and expects no result (202 Accepted typical).
        sendNotification("notifications/initialized");

        JSONObject serverInfo = result.optJSONObject("serverInfo");
        String serverName = serverInfo != null ? serverInfo.optString("name", "") : "";
        String version = result.optString("protocolVersion", "");
        return (serverName.isEmpty() ? config.name : serverName) + (version.isEmpty() ? "" : " (" + version + ")");
    }

    /** tools/list → parsed tool metadata. */
    public List<McpToolInfo> listTools() throws Exception {
        JSONObject result = rpc("tools/list", new JSONObject());
        List<McpToolInfo> tools = new ArrayList<>();
        if (result == null) return tools;
        JSONArray arr = result.optJSONArray("tools");
        if (arr == null) return tools;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject t = arr.getJSONObject(i);
            String name = t.optString("name");
            if (name == null || name.isEmpty()) continue;
            String description = t.optString("description", "");
            JSONObject schema = t.optJSONObject("inputSchema");
            tools.add(new McpToolInfo(name, description, schema != null ? schema.toString() : "{}"));
        }
        return tools;
    }

    /** tools/call → joined text content (honest error text when the tool reports one). */
    public String callTool(String toolName, JSONObject arguments) throws Exception {
        JSONObject params = new JSONObject();
        params.put("name", toolName);
        params.put("arguments", arguments != null ? arguments : new JSONObject());

        JSONObject result = rpc("tools/call", params);
        if (result == null) throw new Exception("MCP tool '" + toolName + "' returned no result");

        StringBuilder sb = new StringBuilder();
        JSONArray content = result.optJSONArray("content");
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                JSONObject part = content.optJSONObject(i);
                if (part != null && "text".equals(part.optString("type"))) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(part.optString("text", ""));
                }
            }
        }
        if (sb.length() == 0) {
            JSONObject structured = result.optJSONObject("structuredContent");
            sb.append(structured != null ? structured.toString() : result.toString());
        }
        if (result.optBoolean("isError", false)) {
            return "MCP tool error: " + sb;
        }
        return sb.toString();
    }

    // --- JSON-RPC transport ---

    private JSONObject rpc(String method, JSONObject params) throws Exception {
        if (McpServerConfig.TRANSPORT_SSE.equals(config.transport)) {
            return rpcViaSse(method, params);
        }
        return rpcViaStreamableHttp(method, params);
    }

    private void sendNotification(String method) {
        try {
            JSONObject body = baseBody(method, null);
            body.remove("id");
            if (McpServerConfig.TRANSPORT_SSE.equals(config.transport)) {
                // Legacy SSE notifications go to the message endpoint if known; safe no-op otherwise.
                return;
            }
            Request request = streamableRequestBuilder(config.url)
                    .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                    .build();
            try (Response response = sharedClient.newCall(request).execute()) {
                // 202/200 both acceptable; body (if any) discarded.
            }
        } catch (Exception ignored) {
            // Notifications are best-effort per the MCP spec.
        }
    }

    /** Streamable HTTP: POST with Accept: application/json,text/event-stream. */
    private JSONObject rpcViaStreamableHttp(String method, JSONObject params) throws Exception {
        JSONObject body = baseBody(method, params);
        Request request = streamableRequestBuilder(config.url)
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                .build();

        int expectedId = body.getInt("id");
        try (Response response = sharedClient.newCall(request).execute()) {
            String newSession = response.header("Mcp-Session-Id");
            if (newSession != null && !newSession.isEmpty()) sessionId = newSession;

            if (!response.isSuccessful()) {
                throw new Exception("MCP server returned HTTP " + response.code() + " for '" + method + "'");
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) throw new Exception("MCP server returned an empty response");

            String contentType = response.header("Content-Type", "");
            if (contentType != null && contentType.contains("text/event-stream")) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream(), "UTF-8"))) {
                    JSONObject match = readSseForId(reader, expectedId);
                    if (match == null) throw new Exception("MCP stream closed without a result for '" + method + "'");
                    return extractResult(match, method);
                }
            }

            String text = responseBody.string();
            if (text.trim().isEmpty()) throw new Exception("MCP server returned an empty response");
            try {
                return extractResult(new JSONObject(text), method);
            } catch (JSONException e) {
                throw new Exception("MCP response parse error: not valid JSON");
            }
        } catch (SocketTimeoutException e) {
            throw new Exception("MCP request timed out (10s) for '" + method + "'");
        }
    }

    private Request.Builder streamableRequestBuilder(String url) {
        Request.Builder rb = new Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/event-stream");
        applyCustomHeaders(rb);
        if (sessionId != null) rb.header("Mcp-Session-Id", sessionId);
        return rb;
    }

    /**
     * Legacy SSE: GET the stream, wait for the "endpoint" event, POST JSON-RPC
     * there, then read the matching result from the stream.
     */
    private JSONObject rpcViaSse(String method, JSONObject params) throws Exception {
        Request.Builder getBuilder = new Request.Builder().url(config.url).header("Accept", "text/event-stream");
        applyCustomHeaders(getBuilder);

        try (Response getResponse = sharedClient.newCall(getBuilder.build()).execute()) {
            if (!getResponse.isSuccessful()) {
                throw new Exception("MCP SSE stream returned HTTP " + getResponse.code());
            }
            ResponseBody body = getResponse.body();
            if (body == null) throw new Exception("MCP SSE stream returned an empty response");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream(), "UTF-8"))) {
                String endpoint = awaitEndpointEvent(reader);
                if (endpoint == null) throw new Exception("MCP SSE stream closed before announcing an endpoint");
                String postUrl = resolveEndpoint(config.url, endpoint);

                JSONObject rpcBody = baseBody(method, params);
                int expectedId = rpcBody.getInt("id");

                Request.Builder postBuilder = new Request.Builder()
                        .url(postUrl)
                        .post(RequestBody.create(rpcBody.toString(), MediaType.get("application/json")));
                applyCustomHeaders(postBuilder);
                try (Response postResponse = sharedClient.newCall(postBuilder.build()).execute()) {
                    if (!postResponse.isSuccessful() && postResponse.code() != 202) {
                        throw new Exception("MCP server returned HTTP " + postResponse.code() + " for '" + method + "'");
                    }
                }

                JSONObject match = readSseForId(reader, expectedId);
                if (match == null) throw new Exception("MCP SSE stream closed without a result for '" + method + "'");
                return extractResult(match, method);
            }
        } catch (SocketTimeoutException e) {
            throw new Exception("MCP SSE request timed out (10s) for '" + method + "'");
        }
    }

    // --- SSE parsing helpers ---

    /** Reads SSE frames until an "endpoint" event's data arrives (or EOF/timeout). */
    private String awaitEndpointEvent(BufferedReader reader) throws Exception {
        String currentEvent = null;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("event:")) {
                currentEvent = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                String data = line.substring(5).trim();
                if ("endpoint".equals(currentEvent) && !data.isEmpty()) return data;
            } else if (line.isEmpty()) {
                currentEvent = null;
            }
        }
        return null;
    }

    /** Reads SSE frames until a data frame carrying a JSON-RPC message with the expected id. */
    private JSONObject readSseForId(BufferedReader reader, int expectedId) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) continue;
            try {
                JSONObject obj = new JSONObject(data);
                if (obj.optInt("id", -1) == expectedId) return obj;
            } catch (JSONException ignored) {
                // Non-JSON keep-alive frames are ignored.
            }
        }
        return null;
    }

    /** Resolves the endpoint event's (possibly relative) URI against the server URL. */
    private String resolveEndpoint(String baseUrl, String endpoint) {
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) return endpoint;
        try {
            return new java.net.URI(baseUrl).resolve(endpoint).toString();
        } catch (Exception e) {
            // Fallback: naive join.
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            return base + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
        }
    }

    // --- Shared helpers ---

    private JSONObject baseBody(String method, JSONObject params) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("jsonrpc", "2.0");
        body.put("id", idSeq.getAndIncrement());
        body.put("method", method);
        if (params != null) body.put("params", params);
        return body;
    }

    private void applyCustomHeaders(Request.Builder rb) {
        for (McpServerConfig.Header header : config.headers) {
            if (header.name == null || header.name.isEmpty()) continue;
            String value = keys.getKey(McpServerConfig.valueKey(config.id, header.name));
            if (value != null && !value.isEmpty()) {
                rb.header(header.name, value);
            }
        }
    }

    /** Extracts result or raises a typed error from a JSON-RPC envelope. */
    private JSONObject extractResult(JSONObject envelope, String method) throws Exception {
        JSONObject error = envelope.optJSONObject("error");
        if (error != null) {
            String message = error.optString("message", "unknown error");
            throw new Exception("MCP error on '" + method + "': " + message);
        }
        JSONObject result = envelope.optJSONObject("result");
        if (result == null) {
            // Some servers return a non-object result; surface honestly.
            Object raw = envelope.opt("result");
            if (raw == null || JSONObject.NULL.equals(raw)) {
                throw new Exception("MCP server returned no result for '" + method + "'");
            }
            return new JSONObject().put("raw", String.valueOf(raw));
        }
        return result;
    }

    /** Serializes tool metadata for the kv cache (single format for store + registry). */
    public static String toolsToJson(List<McpToolInfo> tools) {
        JSONArray arr = new JSONArray();
        try {
            for (McpToolInfo t : tools) {
                arr.put(new JSONObject()
                        .put("name", t.name)
                        .put("description", t.description)
                        .put("inputSchema", new JSONObject(t.inputSchemaJson)));
            }
        } catch (JSONException ignored) {}
        return arr.toString();
    }

    /** Parses the kv cache back into tool metadata. */
    public static List<McpToolInfo> toolsFromJson(String json) {
        List<McpToolInfo> tools = new ArrayList<>();
        if (json == null || json.isEmpty()) return tools;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject t = arr.getJSONObject(i);
                tools.add(new McpToolInfo(
                        t.optString("name"),
                        t.optString("description", ""),
                        t.optJSONObject("inputSchema") != null ? t.getJSONObject("inputSchema").toString() : "{}"));
            }
        } catch (JSONException ignored) {}
        return tools;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "McpClient[%s %s]", config.transport, config.url);
    }
}
