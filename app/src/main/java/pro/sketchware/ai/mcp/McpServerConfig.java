package pro.sketchware.ai.mcp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * [WHAT] Configuration of one MCP (Model Context Protocol) server (P2-MCP).
 * [WHY] Persisted as JSON in AiStorage kv "mcp_servers" via McpStore (SSOT, §4).
 * Header NAMES live here; header VALUES never do — values exist only in
 * SecureKeyStore under {@link #valueKey(String, String)} (RISK-4, D16).
 */
public class McpServerConfig {

    public static final String TRANSPORT_STREAMABLE_HTTP = "STREAMABLE_HTTP";
    public static final String TRANSPORT_SSE = "SSE";

    public String id;
    public String name;
    public String url;
    public String transport = TRANSPORT_STREAMABLE_HTTP;
    public boolean enabled = true;
    public final List<Header> headers = new ArrayList<>();

    /** Only the header NAME is persisted in kv; the value key points into SecureKeyStore. */
    public static class Header {
        public String name;

        public Header() {}

        public Header(String name) {
            this.name = name;
        }
    }

    /** SecureKeyStore slot for one header value (never stored in kv, RISK-4). */
    public static String valueKey(String serverId, String headerName) {
        return "mcp_header:" + serverId + ":" + headerName;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("name", name);
        obj.put("url", url);
        obj.put("transport", transport);
        obj.put("enabled", enabled);
        JSONArray arr = new JSONArray();
        for (Header h : headers) {
            arr.put(new JSONObject().put("name", h.name));
        }
        obj.put("headers", arr);
        return obj;
    }

    public static McpServerConfig fromJson(JSONObject obj) throws JSONException {
        McpServerConfig c = new McpServerConfig();
        c.id = obj.getString("id");
        c.name = obj.getString("name");
        c.url = obj.getString("url");
        c.transport = obj.optString("transport", TRANSPORT_STREAMABLE_HTTP);
        c.enabled = obj.optBoolean("enabled", true);
        JSONArray arr = obj.optJSONArray("headers");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject h = arr.getJSONObject(i);
                String headerName = h.optString("name");
                if (headerName != null && !headerName.isEmpty()) {
                    c.headers.add(new Header(headerName));
                }
            }
        }
        return c;
    }
}
