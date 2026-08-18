package pro.sketchware.ai.core;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One model returned by a provider's models endpoint.
 * Free flag: null means the provider exposes no pricing metadata,
 * TRUE - the model is free, FALSE - it is a paid model.
 */
public final class ModelItem {

    public final String id;
    public final Boolean free;

    public ModelItem(String id, Boolean free) {
        this.id = id == null ? "" : id;
        this.free = free;
    }

    public boolean hasPricing() {
        return free != null;
    }

    public boolean isFree() {
        return Boolean.TRUE.equals(free);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            if (free != null) {
                json.put("free", free.booleanValue());
            }
        } catch (JSONException ignored) {
            // Static values cannot fail to serialize.
        }
        return json;
    }

    public static ModelItem fromJson(JSONObject json) {
        Boolean free = json.has("free") && !json.isNull("free") ? json.optBoolean("free", false) : null;
        return new ModelItem(json.optString("id", ""), free);
    }
}
