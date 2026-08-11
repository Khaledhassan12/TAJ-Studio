package pro.sketchware.ai.data.importers;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import pro.sketchware.ai.data.AiStorage;

/**
 * [WHAT] Importer for Claude export .zip files.
 * [WHY] Enables migration from Claude to TAJ Studio.
 * [HOW] Parses conversations.json inside the zip; recreates conversations/messages in TAJ DB.
 */
public class ClaudeImporter {

    private static final String TAG = "ClaudeImporter";

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
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", Locale.US);
        
        for (int i = 0; i < arr.length(); i++) {
            JSONObject chat = arr.getJSONObject(i);
            String title = chat.optString("name", "Imported Claude Chat");
            long ts = System.currentTimeMillis();
            try {
                ts = df.parse(chat.getString("created_at")).getTime();
            } catch (Exception ignored) {}

            String convId = UUID.randomUUID().toString();
            ContentValues cv = new ContentValues();
            cv.put("id", convId);
            cv.put("scId", "imported_claude");
            cv.put("title", title);
            cv.put("modelId", "claude");
            cv.put("provider", "anthropic");
            cv.put("createdAt", ts);
            cv.put("updatedAt", ts);
            storage.insertConversation(cv);

            JSONArray messages = chat.optJSONArray("chat_messages");
            if (messages != null) {
                for (int j = 0; j < messages.length(); j++) {
                    JSONObject m = messages.getJSONObject(j);
                    String sender = m.optString("sender", "");
                    String role = sender.equals("assistant") ? "assistant" : "user";
                    
                    // Claude export message content is an array of objects
                    JSONArray contentArr = m.optJSONArray("content");
                    if (contentArr == null || contentArr.length() == 0) continue;
                    
                    StringBuilder contentSb = new StringBuilder();
                    for (int k = 0; k < contentArr.length(); k++) {
                        JSONObject contentPart = contentArr.getJSONObject(k);
                        if ("text".equals(contentPart.optString("type"))) {
                            contentSb.append(contentPart.optString("text", ""));
                        }
                    }
                    
                    long msgTs = ts;
                    try {
                        msgTs = df.parse(m.getString("created_at")).getTime();
                    } catch (Exception ignored) {}

                    ContentValues mv = new ContentValues();
                    mv.put("id", UUID.randomUUID().toString());
                    mv.put("conversationId", convId);
                    mv.put("role", role);
                    mv.put("content", contentSb.toString());
                    mv.put("createdAt", msgTs);
                    storage.insertMessage(mv);
                }
            }
            imported++;
        }
        return imported;
    }
}
