package pro.sketchware.ai.data;

import android.content.Context;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * [WHAT] Single Source of Truth (SSOT) for Data Control and Backup settings.
 * [WHY] Manages persistence of backup frequencies, content flags, and locations (P2-DC).
 * [HOW] Backed by AiStorage kv table.
 */
public class DataControlSettings {

    private static DataControlSettings instance;
    private final AiStorage storage;

    public static final String KEY_AUTO_BACKUP = "dc_auto_backup";
    public static final String KEY_BACKUP_FREQ = "dc_backup_freq_days";
    public static final String KEY_BACKUP_CONTENT = "dc_backup_content";
    public static final String KEY_BACKUP_TREE_URI = "dc_backup_tree_uri";
    public static final String KEY_AUTO_DELETE = "dc_auto_delete";
    public static final String KEY_RETENTION_DAYS = "dc_retention_days";
    public static final String KEY_LAST_BACKUP_AT = "dc_last_backup_at";

    public static synchronized DataControlSettings get(Context context) {
        if (instance == null) {
            instance = new DataControlSettings(context.getApplicationContext());
        }
        return instance;
    }

    private DataControlSettings(Context context) {
        this.storage = AiStorage.get(context);
    }

    public boolean isAutoBackupEnabled() {
        return Boolean.parseBoolean(storage.kvGet(KEY_AUTO_BACKUP));
    }

    public void setAutoBackupEnabled(boolean enabled) {
        storage.kvPut(KEY_AUTO_BACKUP, String.valueOf(enabled));
    }

    public int getBackupFreqDays() {
        String val = storage.kvGet(KEY_BACKUP_FREQ);
        return val != null ? Integer.parseInt(val) : 1;
    }

    public void setBackupFreqDays(int days) {
        storage.kvPut(KEY_BACKUP_FREQ, String.valueOf(days));
    }

    public BackupContent getBackupContent() {
        String json = storage.kvGet(KEY_BACKUP_CONTENT);
        if (json == null) return new BackupContent();
        try {
            return new BackupContent(new JSONObject(json));
        } catch (JSONException e) {
            return new BackupContent();
        }
    }

    public void setBackupContent(BackupContent content) {
        storage.kvPut(KEY_BACKUP_CONTENT, content.toJson().toString());
    }

    public String getBackupTreeUri() {
        String val = storage.kvGet(KEY_BACKUP_TREE_URI);
        return val != null ? val : "";
    }

    public void setBackupTreeUri(String uri) {
        storage.kvPut(KEY_BACKUP_TREE_URI, uri);
    }

    public boolean isAutoDeleteEnabled() {
        return Boolean.parseBoolean(storage.kvGet(KEY_AUTO_DELETE));
    }

    public void setAutoDeleteEnabled(boolean enabled) {
        storage.kvPut(KEY_AUTO_DELETE, String.valueOf(enabled));
    }

    public int getRetentionDays() {
        String val = storage.kvGet(KEY_RETENTION_DAYS);
        return val != null ? Integer.parseInt(val) : 7;
    }

    public void setRetentionDays(int days) {
        storage.kvPut(KEY_RETENTION_DAYS, String.valueOf(days));
    }

    public long getLastBackupAt() {
        String val = storage.kvGet(KEY_LAST_BACKUP_AT);
        return val != null ? Long.parseLong(val) : 0;
    }

    public void setLastBackupAt(long ts) {
        storage.kvPut(KEY_LAST_BACKUP_AT, String.valueOf(ts));
    }

    public boolean isAutoExactAlarmsEnabled() {
        return storage.isAutoExactAlarmsEnabled();
    }

    public static class BackupContent {

        public boolean conversations = true;
        public boolean memories = true;
        public boolean prompts = true;
        public boolean settings = true;
        public boolean secrets = false;

        public BackupContent() {}

        public BackupContent(JSONObject json) {
            this.conversations = json.optBoolean("conversations", true);
            this.memories = json.optBoolean("memories", true);
            this.prompts = json.optBoolean("prompts", true);
            this.settings = json.optBoolean("settings", true);
            this.secrets = json.optBoolean("secrets", false);
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("conversations", conversations);
                json.put("memories", memories);
                json.put("prompts", prompts);
                json.put("settings", settings);
                json.put("secrets", secrets);
            } catch (JSONException ignored) {}
            return json;
        }
    }
}
