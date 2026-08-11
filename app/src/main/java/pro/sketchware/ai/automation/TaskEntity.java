package pro.sketchware.ai.automation;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * [WHAT] One model-managed Task (P2-AU, D17).
 * [WHY] Either CRON (recurring, cronExpr set) or ONE-SHOT (runAt millis).
 * Fires execute headlessly in the conversation that created the task
 * (scId + conversationId binding; provider/model resolved from kv at fire).
 */
public class TaskEntity {

    public String id;
    public String name;
    public String prompt;
    /** Optional model override; null => use the conversation's bound model. */
    public String modelId;
    /** 5-field cron expression; null/empty => one-shot at runAt. */
    public String cronExpr;
    /** One-shot fire time (millis). Ignored for cron tasks. */
    public long runAt;
    public boolean enabled = true;
    public String scId;
    public String conversationId;
    public long createdAt;

    public boolean isCron() {
        return cronExpr != null && !cronExpr.isEmpty();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("name", name);
        obj.put("prompt", prompt);
        obj.put("modelId", modelId == null ? JSONObject.NULL : modelId);
        obj.put("cronExpr", cronExpr == null ? JSONObject.NULL : cronExpr);
        obj.put("runAt", runAt);
        obj.put("enabled", enabled);
        obj.put("scId", scId);
        obj.put("conversationId", conversationId);
        obj.put("createdAt", createdAt);
        return obj;
    }

    public static TaskEntity fromJson(JSONObject obj) throws JSONException {
        TaskEntity t = new TaskEntity();
        t.id = obj.getString("id");
        t.name = obj.optString("name", "");
        t.prompt = obj.optString("prompt", "");
        t.modelId = obj.isNull("modelId") ? null : obj.optString("modelId", null);
        t.cronExpr = obj.isNull("cronExpr") ? null : obj.optString("cronExpr", null);
        t.runAt = obj.optLong("runAt", 0L);
        t.enabled = obj.optBoolean("enabled", true);
        t.scId = obj.optString("scId", "");
        t.conversationId = obj.optString("conversationId", "");
        t.createdAt = obj.optLong("createdAt", 0L);
        return t;
    }
}
