package pro.sketchware.ai.automation;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * [WHAT] One model-managed conversation Loop (P2-AU, D17).
 * [WHY] A repeating headless prompt sent to the creating conversation every
 * intervalMinutes while running. Fires are guarded against concurrent runs.
 */
public class LoopEntity {

    public String id;
    public String name;
    public String prompt;
    public int intervalMinutes = 5;
    public boolean running = false;
    public String scId;
    public String conversationId;
    public long createdAt;

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("name", name);
        obj.put("prompt", prompt);
        obj.put("intervalMinutes", intervalMinutes);
        obj.put("running", running);
        obj.put("scId", scId);
        obj.put("conversationId", conversationId);
        obj.put("createdAt", createdAt);
        return obj;
    }

    public static LoopEntity fromJson(JSONObject obj) throws JSONException {
        LoopEntity l = new LoopEntity();
        l.id = obj.getString("id");
        l.name = obj.optString("name", "");
        l.prompt = obj.optString("prompt", "");
        l.intervalMinutes = obj.optInt("intervalMinutes", 5);
        l.running = obj.optBoolean("running", false);
        l.scId = obj.optString("scId", "");
        l.conversationId = obj.optString("conversationId", "");
        l.createdAt = obj.optLong("createdAt", 0L);
        return l;
    }
}
