package pro.sketchware.ai.data;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.ai.automation.AutomationReceiver;
import pro.sketchware.ai.memory.MemoryEntry;
import pro.sketchware.ai.memory.MemoryStore;
import pro.sketchware.ai.prompts.PromptTemplate;
import pro.sketchware.ai.prompts.PromptTemplateStore;

/**
 * [WHAT] Core engine for TAJ backup, export, and import.
 * [WHY] Ensures data portability and safety via .taj export files and scheduled backups.
 * [HOW] Background operations via Executor; AlarmManager for scheduling; SAF for storage.
 */
public class TajBackupManager {

    private static final String TAG = "TajBackupManager";
    private static final String EXPORT_PREFIX = "taj_export_";
    private static final String EXPORT_EXT = ".taj";

    private static TajBackupManager instance;
    private final Context context;
    private final AiStorage storage;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface ExportCallback {
        void onResult(boolean ok, String path, String error);
    }

    public interface ImportCallback {
        void onResult(boolean ok, String details, String error);
    }

    public static synchronized TajBackupManager get(Context context) {
        if (instance == null) {
            instance = new TajBackupManager(context.getApplicationContext());
        }
        return instance;
    }

    private TajBackupManager(Context context) {
        this.context = context.getApplicationContext();
        this.storage = AiStorage.get(context);
    }

    // --- Export ---

    public void exportAsync(DataControlSettings.BackupContent flags, ExportCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject payload = buildExportPayload(flags);
                String filename = EXPORT_PREFIX + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + EXPORT_EXT;
                
                DataControlSettings dc = DataControlSettings.get(context);
                String treeUri = dc.getBackupTreeUri();
                
                OutputStream out = null;
                String finalPath = "";

                if (!treeUri.isEmpty()) {
                    DocumentFile root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri));
                    if (root != null && root.canWrite()) {
                        DocumentFile file = root.createFile("application/octet-stream", filename);
                        if (file != null) {
                            out = context.getContentResolver().openOutputStream(file.getUri());
                            finalPath = file.getUri().toString();
                        }
                    }
                }

                if (out == null) {
                    File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File taj = new File(downloads, "TAJ/Backup");
                    if (!taj.exists()) taj.mkdirs();
                    File dest = new File(taj, filename);
                    out = new FileOutputStream(dest);
                    finalPath = dest.getAbsolutePath();
                }

                try {
                    out.write(payload.toString(2).getBytes());
                } finally {
                    out.close();
                }
                
                callback.onResult(true, finalPath, null);
            } catch (Exception e) {
                Log.e(TAG, "Export failed", e);
                callback.onResult(false, null, e.getMessage());
            }
        });
    }

    private JSONObject buildExportPayload(DataControlSettings.BackupContent flags) throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("format", "taj-export");
        payload.put("version", 1);
        payload.put("exportedAt", System.currentTimeMillis());

        if (flags.conversations) {
            payload.put("conversations", dumpConversations());
        }
        if (flags.memories) {
            payload.put("memories", dumpMemories());
        }
        if (flags.prompts) {
            payload.put("prompts", dumpPrompts());
        }
        if (flags.settings) {
            payload.put("settings", dumpSettings());
        }
        if (flags.secrets) {
            payload.put("secrets", SecureKeyStore.get(context).dumpKeys());
        }

        return payload;
    }

    private JSONArray dumpConversations() throws JSONException {
        JSONArray arr = new JSONArray();
        Cursor c = AiDatabase.get(context).getReadableDatabase().query("conversations", null, null, null, null, null, null);
        while (c != null && c.moveToNext()) {
            JSONObject conv = new JSONObject();
            String id = c.getString(c.getColumnIndexOrThrow("id"));
            conv.put("id", id);
            conv.put("scId", c.getString(c.getColumnIndexOrThrow("scId")));
            conv.put("title", c.getString(c.getColumnIndexOrThrow("title")));
            conv.put("modelId", c.getString(c.getColumnIndexOrThrow("modelId")));
            conv.put("provider", c.getString(c.getColumnIndexOrThrow("provider")));
            conv.put("createdAt", c.getLong(c.getColumnIndexOrThrow("createdAt")));
            conv.put("updatedAt", c.getLong(c.getColumnIndexOrThrow("updatedAt")));
            
            JSONArray msgs = new JSONArray();
            Cursor mc = storage.listMessages(id);
            while (mc.moveToNext()) {
                JSONObject msg = new JSONObject();
                msg.put("id", mc.getString(mc.getColumnIndexOrThrow("id")));
                msg.put("role", mc.getString(mc.getColumnIndexOrThrow("role")));
                msg.put("content", mc.getString(mc.getColumnIndexOrThrow("content")));
                msg.put("toolCallsJson", mc.getString(mc.getColumnIndexOrThrow("toolCallsJson")));
                msg.put("toolResultsJson", mc.getString(mc.getColumnIndexOrThrow("toolResultsJson")));
                msg.put("createdAt", mc.getLong(mc.getColumnIndexOrThrow("createdAt")));
                msgs.put(msg);
            }
            mc.close();
            conv.put("messages", msgs);
            arr.put(conv);
        }
        if (c != null) c.close();
        return arr;
    }

    private JSONArray dumpMemories() throws JSONException {
        JSONArray arr = new JSONArray();
        for (MemoryEntry entry : MemoryStore.get(context).list()) {
            arr.put(entry.toJson());
        }
        return arr;
    }

    private JSONArray dumpPrompts() throws JSONException {
        JSONArray arr = new JSONArray();
        for (PromptTemplate t : PromptTemplateStore.get(context).loadAll()) {
            arr.put(new JSONObject(t.serialize()));
        }
        return arr;
    }

    private JSONObject dumpSettings() throws JSONException {
        JSONObject obj = new JSONObject();
        Cursor c = AiDatabase.get(context).getReadableDatabase().query("kv", null, null, null, null, null, null);
        while (c != null && c.moveToNext()) {
            obj.put(c.getString(c.getColumnIndexOrThrow("key")), c.getString(c.getColumnIndexOrThrow("value")));
        }
        if (c != null) c.close();
        return obj;
    }

    // --- Import ---

    public void importAsync(Uri uri, ImportCallback callback) {
        executor.execute(() -> {
            try {
                String json = readUri(uri);
                JSONObject payload = new JSONObject(json);
                if (!"taj-export".equals(payload.optString("format"))) {
                    callback.onResult(false, null, "Invalid file format.");
                    return;
                }

                int convs = restoreConversations(payload.optJSONArray("conversations"));
                int mems = restoreMemories(payload.optJSONArray("memories"));
                int prompts = restorePrompts(payload.optJSONArray("prompts"));
                restoreSettings(payload.optJSONObject("settings"));
                if (payload.has("secrets")) {
                    SecureKeyStore.get(context).restoreKeys(payload.getString("secrets"));
                }

                String details = String.format(Locale.US, "Imported: %d convs, %d memories, %d prompts", convs, mems, prompts);
                callback.onResult(true, details, null);
            } catch (Exception e) {
                Log.e(TAG, "Import failed", e);
                callback.onResult(false, null, e.getMessage());
            }
        });
    }

    private int restoreConversations(JSONArray arr) throws JSONException {
        if (arr == null) return 0;
        int count = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            ContentValues cv = new ContentValues();
            cv.put("id", obj.getString("id"));
            cv.put("scId", obj.getString("scId"));
            cv.put("title", obj.getString("title"));
            cv.put("modelId", obj.getString("modelId"));
            cv.put("provider", obj.getString("provider"));
            cv.put("createdAt", obj.getLong("createdAt"));
            cv.put("updatedAt", obj.getLong("updatedAt"));
            storage.insertConversation(cv);

            JSONArray msgs = obj.optJSONArray("messages");
            if (msgs != null) {
                for (int j = 0; j < msgs.length(); j++) {
                    JSONObject m = msgs.getJSONObject(j);
                    ContentValues mv = new ContentValues();
                    mv.put("id", m.getString("id"));
                    mv.put("conversationId", obj.getString("id"));
                    mv.put("role", m.getString("role"));
                    mv.put("content", m.getString("content"));
                    mv.put("toolCallsJson", m.optString("toolCallsJson"));
                    mv.put("toolResultsJson", m.optString("toolResultsJson"));
                    mv.put("createdAt", m.getLong("createdAt"));
                    storage.insertMessage(mv);
                }
            }
            count++;
        }
        return count;
    }

    private int restoreMemories(JSONArray arr) throws JSONException {
        if (arr == null) return 0;
        int count = 0;
        MemoryStore store = MemoryStore.get(context);
        for (int i = 0; i < arr.length(); i++) {
            MemoryEntry entry = MemoryEntry.fromJson(arr.getJSONObject(i));
            store.update(entry); // upsert
            count++;
        }
        return count;
    }

    private int restorePrompts(JSONArray arr) throws JSONException {
        if (arr == null) return 0;
        int count = 0;
        PromptTemplateStore store = PromptTemplateStore.get(context);
        for (int i = 0; i < arr.length(); i++) {
            PromptTemplate t = PromptTemplate.deserialize(arr.getJSONObject(i).toString());
            store.addTemplate(t);
            count++;
        }
        return count;
    }

    private void restoreSettings(JSONObject obj) throws JSONException {
        if (obj == null) return;
        java.util.Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            storage.kvPut(key, obj.getString(key));
        }
    }

    // --- Location Resolution ---
    
    public String getResolvedLocationText() {
        DataControlSettings dc = DataControlSettings.get(context);
        String treeUri = dc.getBackupTreeUri();
        if (!treeUri.isEmpty()) {
            try {
                Uri uri = Uri.parse(treeUri);
                return DocumentsContract.getTreeDocumentId(uri);
            } catch (Exception e) {
                return "Custom Folder (SAF)";
            }
        }
        return "Downloads/TAJ/Backup";
    }

    // --- Auto Backup Logic ---

    public void runAutoBackupIfDue() {
        DataControlSettings dc = DataControlSettings.get(context);
        if (!dc.isAutoBackupEnabled()) return;

        long now = System.currentTimeMillis();
        long last = dc.getLastBackupAt();
        long freqMs = dc.getBackupFreqDays() * 24L * 60L * 60L * 1000L;

        if (now - last >= freqMs) {
            exportAsync(dc.getBackupContent(), (ok, path, error) -> {
                if (ok) {
                    dc.setLastBackupAt(System.currentTimeMillis());
                    if (dc.isAutoDeleteEnabled()) {
                        pruneOldBackups(dc.getRetentionDays());
                    }
                }
                rescheduleAutoBackup();
            });
        } else {
            rescheduleAutoBackup();
        }
    }

    public void pruneOldBackups(int retentionDays) {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File taj = new File(downloads, "TAJ/Backup");
        if (!taj.exists()) return;

        long cutoff = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L);
        File[] files = taj.listFiles((dir, name) -> name.startsWith(EXPORT_PREFIX) && name.endsWith(EXPORT_EXT));
        if (files != null) {
            for (File f : files) {
                if (f.lastModified() < cutoff) {
                    f.delete();
                }
            }
        }
    }

    public void rescheduleAutoBackup() {
        DataControlSettings dc = DataControlSettings.get(context);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AutomationReceiver.class);
        intent.setAction("pro.sketchware.ai.BACKUP_FIRE");
        
        PendingIntent pi = PendingIntent.getBroadcast(context, 888, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        if (!dc.isAutoBackupEnabled()) {
            am.cancel(pi);
            return;
        }

        long next = System.currentTimeMillis() + (dc.getBackupFreqDays() * 24L * 60L * 60L * 1000L);
        // Reuse Automation slot's exact-alarm pattern
        if (dc.isAutoExactAlarmsEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            }
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        }
        Log.i(TAG, "Backup rescheduled for: " + new Date(next));
    }

    private String readUri(Uri uri) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
