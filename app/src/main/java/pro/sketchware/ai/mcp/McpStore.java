package pro.sketchware.ai.mcp;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.data.SecureKeyStore;

/**
 * [WHAT] SSOT + single writer for MCP server configs (P2-MCP, §4/R5).
 * [WHY] One place mutates kv "mcp_servers", SecureKeyStore header values, and
 * the per-server tools cache — so UI, registry, and client never disagree.
 * [HOW] Singleton over AiStorage kv; header VALUES only via SecureKeyStore
 * (RISK-4: never in kv); tools/list results cached in kv "mcp_tools_cache:<id>"
 * so agent specs survive restarts without network.
 */
public class McpStore {

    private static final String TAG = "McpStore";
    private static final String KEY_SERVERS = "mcp_servers";
    private static final String KEY_TOOLS_PREFIX = "mcp_tools_cache:";

    private static McpStore instance;

    private final Context context;
    private final AiStorage storage;
    private final SecureKeyStore keys;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final List<Runnable> changeListeners = new ArrayList<>();

    private McpStore(Context context) {
        this.context = context.getApplicationContext();
        this.storage = AiStorage.get(this.context);
        this.keys = SecureKeyStore.get(this.context);
    }

    public static synchronized McpStore get(Context context) {
        if (instance == null) {
            instance = new McpStore(context);
        }
        return instance;
    }

    // --- Server list (single writer) ---

    public List<McpServerConfig> list() {
        List<McpServerConfig> servers = new ArrayList<>();
        String json = storage.kvGet(KEY_SERVERS);
        if (json == null || json.isEmpty()) return servers;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                servers.add(McpServerConfig.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignored) {}
        return servers;
    }

    public McpServerConfig findById(String id) {
        for (McpServerConfig s : list()) {
            if (s.id.equals(id)) return s;
        }
        return null;
    }

    private void saveAll(List<McpServerConfig> servers) {
        JSONArray arr = new JSONArray();
        try {
            for (McpServerConfig s : servers) {
                arr.put(s.toJson());
            }
            storage.kvPut(KEY_SERVERS, arr.toString());
        } catch (JSONException ignored) {}
        notifyChange();
    }

    /** Insert or replace by id. Header values must be written separately. */
    public void upsert(McpServerConfig server) {
        List<McpServerConfig> servers = list();
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).id.equals(server.id)) {
                servers.set(i, server);
                saveAll(servers);
                return;
            }
        }
        servers.add(server);
        saveAll(servers);
    }

    public void setEnabled(String id, boolean enabled) {
        List<McpServerConfig> servers = list();
        for (McpServerConfig s : servers) {
            if (s.id.equals(id)) {
                s.enabled = enabled;
                break;
            }
        }
        saveAll(servers);
    }

    /** Cascade delete (single writer): config + encrypted header values + tools cache. */
    public void delete(String id) {
        McpServerConfig server = findById(id);
        if (server != null) {
            for (McpServerConfig.Header h : server.headers) {
                keys.removeKey(McpServerConfig.valueKey(id, h.name));
            }
        }
        storage.kvPut(KEY_TOOLS_PREFIX + id, null);
        List<McpServerConfig> servers = list();
        for (int i = servers.size() - 1; i >= 0; i--) {
            if (servers.get(i).id.equals(id)) {
                servers.remove(i);
            }
        }
        saveAll(servers);
        Log.i(TAG, "delete: server " + id + " removed (config + headers + tools cache)");
    }

    // --- Header values (SecureKeyStore ONLY, RISK-4) ---

    public void putHeaderValue(String serverId, String headerName, String value) {
        keys.putKey(McpServerConfig.valueKey(serverId, headerName), value);
    }

    public String getHeaderValue(String serverId, String headerName) {
        return keys.getKey(McpServerConfig.valueKey(serverId, headerName));
    }

    /** Removes one encrypted header value (used on header rename/remove, RISK-4). */
    public void removeHeaderValue(String serverId, String headerName) {
        keys.removeKey(McpServerConfig.valueKey(serverId, headerName));
    }

    // --- Tools cache (specs survive restarts without network) ---

    public List<McpClient.McpToolInfo> readToolsCache(String serverId) {
        return McpClient.toolsFromJson(storage.kvGet(KEY_TOOLS_PREFIX + serverId));
    }

    public void writeToolsCache(String serverId, List<McpClient.McpToolInfo> tools) {
        storage.kvPut(KEY_TOOLS_PREFIX + serverId, McpClient.toolsToJson(tools));
    }

    /**
     * Background tools/list refresh for all ENABLED servers (RISK-15: never on
     * the main thread). Each success updates the cache and re-syncs the registry.
     */
    public void refreshToolsAsync() {
        refreshToolsAsync(null);
    }

    /** Same as {@link #refreshToolsAsync()} with a main-thread completion callback (§16 loading state). */
    public void refreshToolsAsync(Runnable onDoneMain) {
        executor.execute(() -> {
            boolean anyUpdate = false;
            for (McpServerConfig server : list()) {
                if (!server.enabled) continue;
                try {
                    McpClient client = new McpClient(context, server);
                    client.initialize();
                    List<McpClient.McpToolInfo> tools = client.listTools();
                    writeToolsCache(server.id, tools);
                    anyUpdate = true;
                    Log.i(TAG, "refreshToolsAsync: '" + server.name + "' cached " + tools.size() + " tools");
                } catch (Exception e) {
                    // Honest: keep the previous cache (if any); log the failure only.
                    Log.w(TAG, "refreshToolsAsync: '" + server.name + "' failed: " + e.getMessage());
                }
            }
            if (anyUpdate) {
                pro.sketchware.ai.agent.tools.ToolRegistry.syncMcp(context);
            }
            if (onDoneMain != null) {
                mainHandler.post(onDoneMain);
            }
        });
    }

    // --- Change listeners (UI refresh + registry re-sync) ---

    public void addChangeListener(Runnable listener) {
        synchronized (changeListeners) {
            changeListeners.add(listener);
        }
    }

    public void removeChangeListener(Runnable listener) {
        synchronized (changeListeners) {
            changeListeners.remove(listener);
        }
    }

    private void notifyChange() {
        List<Runnable> copy;
        synchronized (changeListeners) {
            copy = new ArrayList<>(changeListeners);
        }
        for (Runnable r : copy) r.run();
    }
}
