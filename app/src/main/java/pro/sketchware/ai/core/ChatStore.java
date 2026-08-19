package pro.sketchware.ai.core;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists chat sessions as JSON files in app-private storage.
 * Organized by project sc_id to keep context separate.
 */
public final class ChatStore {

    private static final String TAG = "ChatStore";
    private final File baseDir;

    public ChatStore(Context context, String sc_id) {
        baseDir = new File(context.getFilesDir(), "ai_chats/" + sc_id);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    public static class Message {
        public String role; // user, assistant, system, tool, error
        public String text;
        public long time;
        public String toolState; // JSON for tool chips or raw error body
        public String action; // Action button text
        public String reasoning;
        public int reasoningSeconds;

        public Message(String role, String text) {
            this.role = role;
            this.text = text;
            this.time = System.currentTimeMillis();
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("role", role);
                json.put("text", text);
                json.put("time", time);
                if (toolState != null) json.put("toolState", toolState);
                if (action != null) json.put("action", action);
                if (reasoning != null) json.put("reasoning", reasoning);
                if (reasoningSeconds > 0) json.put("reasoningSeconds", reasoningSeconds);
            } catch (JSONException ignored) {}
            return json;
        }

        public static Message fromJson(JSONObject json) {
            Message m = new Message(json.optString("role"), json.optString("text"));
            m.time = json.optLong("time");
            m.toolState = json.optString("toolState", null);
            m.action = json.optString("action", null);
            m.reasoning = json.optString("reasoning", null);
            m.reasoningSeconds = json.optInt("reasoningSeconds", 0);
            return m;
        }
    }

    public static class Session {
        public String id;
        public long createdAt;
        public List<Message> messages = new ArrayList<>();

        public Session() {
            id = String.valueOf(System.currentTimeMillis());
            createdAt = System.currentTimeMillis();
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("createdAt", createdAt);
                JSONArray msgs = new JSONArray();
                for (Message m : messages) msgs.put(m.toJson());
                json.put("messages", msgs);
            } catch (JSONException ignored) {}
            return json;
        }

        public static Session fromJson(JSONObject json) {
            Session s = new Session();
            s.id = json.optString("id");
            s.createdAt = json.optLong("createdAt");
            JSONArray msgs = json.optJSONArray("messages");
            if (msgs != null) {
                for (int i = 0; i < msgs.length(); i++) {
                    JSONObject m = msgs.optJSONObject(i);
                    if (m != null) s.messages.add(Message.fromJson(m));
                }
            }
            return s;
        }
    }

    public void saveSession(Session session) {
        File file = new File(baseDir, session.id + ".json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(session.toJson().toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.e(TAG, "Failed to save session", e);
        }
    }

    public List<Session> loadAllSessions() {
        List<Session> sessions = new ArrayList<>();
        File[] files = baseDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                try (FileInputStream in = new FileInputStream(f)) {
                    byte[] data = new byte[(int) f.length()];
                    in.read(data);
                    sessions.add(Session.fromJson(new JSONObject(new String(data, StandardCharsets.UTF_8))));
                } catch (Exception e) {
                    Log.w(TAG, "Corrupt session file: " + f.getName());
                }
            }
        }
        sessions.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        return sessions;
    }

    public void deleteSession(String id) {
        File file = new File(baseDir, id + ".json");
        if (file.exists()) file.delete();
    }

    public static Session branchSession(Session source, int upToIndexInclusive) {
        Session branch = new Session();
        branch.id = System.currentTimeMillis() + "_branch";
        if (source.messages.size() > 0) {
            String firstText = source.messages.get(0).text;
            if (firstText.length() > 30) firstText = firstText.substring(0, 30) + "...";
            // title logic would go in metadata, but for now we just label the session.
        }
        for (int i = 0; i <= upToIndexInclusive && i < source.messages.size(); i++) {
            Message m = source.messages.get(i);
            Message copy = new Message(m.role, m.text);
            copy.time = m.time;
            copy.toolState = m.toolState;
            copy.action = m.action;
            copy.reasoning = m.reasoning;
            copy.reasoningSeconds = m.reasoningSeconds;
            branch.messages.add(copy);
        }
        return branch;
    }
}
