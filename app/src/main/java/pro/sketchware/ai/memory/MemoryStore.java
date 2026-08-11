package pro.sketchware.ai.memory;

import android.content.Context;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.data.AiStorage;

/**
 * [WHAT] SSOT + single writer for the TAJ Memory subsystem (P2-MEM, D18).
 * [WHY] All memory state lives in AiStorage kv: access gates (both DEF TRUE),
 * the active-memory text injected into every composed prompt, and the saved
 * memories list. Every mutation goes through this class (R5).
 */
public class MemoryStore {

    public static final String KEY_SAVED_ACCESS = "mem_saved_access";
    public static final String KEY_ACTIVE_ACCESS = "mem_active_access";
    public static final String KEY_ACTIVE_CONTENT = "mem_active_content";
    public static final String KEY_SAVED_LIST = "mem_saved_list";

    private static MemoryStore instance;
    private final AiStorage storage;

    private MemoryStore(Context context) {
        this.storage = AiStorage.get(context);
    }

    public static synchronized MemoryStore get(Context context) {
        if (instance == null) {
            instance = new MemoryStore(context.getApplicationContext());
        }
        return instance;
    }

    // --- Access gates (D18: both default TRUE) ---

    /** Gate for the 5 saved-memory CRUD tools. */
    public boolean isSavedAccessEnabled() {
        String val = storage.kvGet(KEY_SAVED_ACCESS);
        return val == null || Boolean.parseBoolean(val);
    }

    public void setSavedAccessEnabled(boolean enabled) {
        storage.kvPut(KEY_SAVED_ACCESS, String.valueOf(enabled));
    }

    /** Gate for update_active_memory AND prompt injection. */
    public boolean isActiveAccessEnabled() {
        String val = storage.kvGet(KEY_ACTIVE_ACCESS);
        return val == null || Boolean.parseBoolean(val);
    }

    public void setActiveAccessEnabled(boolean enabled) {
        storage.kvPut(KEY_ACTIVE_ACCESS, String.valueOf(enabled));
    }

    // --- Active memory (included in every composed prompt IFF gate ON) ---

    public String getActiveContent() {
        return storage.kvGet(KEY_ACTIVE_CONTENT);
    }

    /** Empty or blank content clears the active memory honestly. */
    public void setActiveContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            storage.kvPut(KEY_ACTIVE_CONTENT, "");
        } else {
            storage.kvPut(KEY_ACTIVE_CONTENT, content.trim());
        }
    }

    // --- Saved memories CRUD (single writer) ---

    public synchronized List<MemoryEntry> list() {
        List<MemoryEntry> out = new ArrayList<>();
        String json = storage.kvGet(KEY_SAVED_LIST);
        if (json == null || json.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                out.add(MemoryEntry.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {
            // Corrupt entry never crashes the app; honest empty list instead.
        }
        return out;
    }

    public synchronized MemoryEntry findById(String id) {
        if (id == null) return null;
        for (MemoryEntry e : list()) {
            if (id.equals(e.id)) return e;
        }
        return null;
    }

    public synchronized MemoryEntry findByTitle(String title) {
        if (title == null) return null;
        for (MemoryEntry e : list()) {
            if (title.equalsIgnoreCase(e.title)) return e;
        }
        return null;
    }

    public synchronized void add(MemoryEntry entry) {
        List<MemoryEntry> all = list();
        all.add(entry);
        save(all);
    }

    public synchronized void update(MemoryEntry entry) {
        List<MemoryEntry> all = list();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(entry.id)) {
                all.set(i, entry);
                save(all);
                return;
            }
        }
    }

    public synchronized void delete(String id) {
        List<MemoryEntry> all = list();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).id.equals(id)) {
                all.remove(i);
            }
        }
        save(all);
    }

    private void save(List<MemoryEntry> all) {
        try {
            JSONArray arr = new JSONArray();
            for (MemoryEntry e : all) {
                arr.put(e.toJson());
            }
            storage.kvPut(KEY_SAVED_LIST, arr.toString());
        } catch (Exception ignored) {
            // Serialization of our own entries cannot fail; never crash.
        }
    }
}
