package pro.sketchware.ai.memory;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * [WHAT] One saved memory (P2-MEM, D18).
 * [WHY] Persisted by MemoryStore as JSON in kv "mem_saved_list"; surfaced to
 * the model via the gated memory tools and editable from the TAJ Memory screen.
 */
public class MemoryEntry {

    public String id;
    public String title;
    public String description;
    public String content;
    public long updatedAt;

    public MemoryEntry() {}

    public MemoryEntry(String id, String title, String description, String content, long updatedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.content = content;
        this.updatedAt = updatedAt;
    }

    public JSONObject toJson() throws JSONException {

        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("title", title);
        obj.put("description", description == null ? JSONObject.NULL : description);
        obj.put("content", content);
        obj.put("updatedAt", updatedAt);
        return obj;
    }

    public static MemoryEntry fromJson(JSONObject obj) throws JSONException {
        MemoryEntry e = new MemoryEntry();
        e.id = obj.getString("id");
        e.title = obj.optString("title", "");
        e.description = obj.isNull("description") ? null : obj.optString("description", null);
        e.content = obj.optString("content", "");
        e.updatedAt = obj.optLong("updatedAt", 0L);
        return e;
    }
}
