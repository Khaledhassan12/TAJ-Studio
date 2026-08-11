package pro.sketchware.ai.data.importers;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import pro.sketchware.ai.data.AiStorage;

/**
 * [WHAT] Importer for ChatGPT export .zip files.
 * [WHY] Enables migration from ChatGPT to TAJ Studio.
 * [HOW] Parses conversations.json inside the zip; recreates conversations/messages in TAJ DB.
 */
public class ChatGptImporter {

    private static final String TAG = "ChatGptImporter";

    public interface Callback {
        void onResult(boolean ok, int count, String error);
    }

    public static void importAsync(Context context, Uri zipUri, Callback callback) {
        new Thread(() -> {
            try {
                int count = performImport(context, zipUri);
                callback.onResult(true, count, null);
            } catch (Exception e) {
                Log.e(TAG, "Import failed", e);
                callback.onResult(false, 0, e.getMessage());
            }
        }).start();
    }

    private static int performImport(Context context, Uri zipUri) throws Exception {
        AiStorage storage = AiStorage.get(context);
        String json = null;

        try (InputStream is = context.getContentResolver().openInputStream(zipUri);
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("conversations.json".equals(entry.getName())) {
                    StringBuilder sb = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    json = sb.toString();
                    break;
                }
            }
        }

        if (json == null) throw new Exception("conversations.json not found in zip.");

        JSONArray arr = new JSONArray(json);
        int imported = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject chat = arr.getJSONObject(i);
            String title = chat.optString("title", "Imported Chat");
            long ts = (long) (chat.optDouble("create_time", System.currentTimeMillis() / 1000.0) * 1000);
            
            String convId = UUID.randomUUID().toString();
            ContentValues cv = new ContentValues();
            cv.put("id", convId);
            cv.put("scId", "imported_chatgpt");
            cv.put("title", title);
            cv.put("modelId", "chatgpt");
            cv.put("provider", "openai");
            cv.put("createdAt", ts);
            cv.put("updatedAt", ts);
            storage.insertConversation(cv);

            JSONObject mapping = chat.optJSONObject("mapping");
            if (mapping != null) {
                // Simplified walk: just take messages in order of create_time
                java.util.Iterator<String> keys = mapping.keys();
                while (keys.hasNext()) {
                    JSONObject node = mapping.getJSONObject(keys.next());
                    JSONObject msgObj = node.optJSONObject("message");
                    if (msgObj == null) continue;
                    
                    String role = msgObj.optString("author", "").equals("assistant") ? "assistant" : "user";
                    JSONObject contentObj = msgObj.optJSONObject("content");
                    if (contentObj == null || !"text".equals(contentObj.optString("content_type"))) continue;
                    
                    JSONArray parts = contentObj.optJSONArray("parts");
                    if (parts == null || parts.length() == 0) continue;
                    
                    String text = parts.getString(0);
                    long msgTs = (long) (msgObj.optDouble("create_time", ts / 1000.0) * 1000);
                    
                    ContentValues mv = new ContentValues();
                    mv.put("id", UUID.randomUUID().toString());
                    mv.put("conversationId", convId);
                    mv.put("role", role);
                    mv.put("content", text);
                    mv.put("createdAt", msgTs);
                    storage.insertMessage(mv);
                }
            }
            imported++;
        }
        return imported;
    }
}
