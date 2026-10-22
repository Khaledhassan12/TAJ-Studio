package pro.sketchware.ai.core;

import android.content.Context;
import android.util.Log;
import android.util.AtomicFile;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Persists chat sessions as JSON files in app-private storage.
 * Organized by project sc_id to keep context separate.
 */
public final class ChatStore {

    private static final String TAG = "ChatStore";
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ai-chat-persistence");
        thread.setDaemon(true);
        return thread;
    });
    private final File baseDir;

    public ChatStore(Context context, String sc_id) {
        baseDir = new File(context.getFilesDir(), "ai_chats/" + sc_id);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    public static class Message {
        public String role; // user, assistant, system, tool, error, suggestion, thinking, thought, tools_card
        public String text;
        public long time;
        public String toolState; // JSON for tool chips or raw error body
        public String action; // Action button text
        public String reasoning;
        public int reasoningSeconds;
        public boolean favorite;
        public List<ToolEvent> toolEvents;

        public Message(String role, String text) {
            this.role = role;
            this.text = sanitize(text);
            this.time = System.currentTimeMillis();
        }

        private static String sanitize(String t) {
            if (t == null) return "";
            // Strip leading "null" strings often emitted by buggy engines
            String s = t.replaceAll("^(null)+", "");
            // If the whole thing was "null", return empty
            if (s.isEmpty() && t.startsWith("null")) return "";
            return s;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("role", role);
                json.put("text", text == null ? "" : text);
                json.put("time", time);
                if (favorite) json.put("favorite", true);
                if (toolState != null) json.put("toolState", toolState);
                if (action != null) json.put("action", action);
                if (reasoning != null) json.put("reasoning", reasoning);
                if (reasoningSeconds > 0) json.put("reasoningSeconds", reasoningSeconds);
                if (toolEvents != null && !toolEvents.isEmpty()) {
                    JSONArray arr = new JSONArray();
                    for (ToolEvent te : toolEvents) arr.put(te.toJson());
                    json.put("toolEvents", arr);
                }
            } catch (JSONException ignored) {}
            return json;
        }

        public static Message fromJson(JSONObject json) {
            String role = json.optString("role", "");
            String text = json.optString("text", "");
            
            Message m = new Message(role, text);
            // Re-sanitize text just in case legacy data is loaded
            m.text = sanitize(m.text);
            m.time = json.optLong("time");
            m.favorite = json.optBoolean("favorite", false);
            m.toolState = json.optString("toolState", null);

            // History sanitation: never load rate limit card in WAITING/RESUMED state
            if ("rate_limit".equals(role)) {
                if ("WAITING".equals(m.toolState) || "RESUMED".equals(m.toolState)) {
                    m.toolState = "COLLAPSED";
                }
            }
            m.action = json.optString("action", null);
            m.reasoning = json.optString("reasoning", null);
            m.reasoningSeconds = json.optInt("reasoningSeconds", 0);
            
            JSONArray teArr = json.optJSONArray("toolEvents");
            if (teArr != null) {
                m.toolEvents = new ArrayList<>();
                for (int i = 0; i < teArr.length(); i++) {
                    JSONObject teObj = teArr.optJSONObject(i);
                    if (teObj != null) {
                        ToolEvent event = ToolEvent.fromJson(teObj);
                        if ("RUNNING".equals(event.status)) {
                            event.status = "ERROR";
                            event.finishedAt = System.currentTimeMillis();
                            event.resultSummary = "Execution interrupted before the session was restored";
                        }
                        m.toolEvents.add(event);
                    }
                }
            }
            return m;
        }
    }

    public static class ToolEvent {
        public String name;
        public String status; // RUNNING, OK, ERROR
        public String domain; // JAVA, RES, ASSET, BLOCK, MANIFEST
        public long startedAt;
        public long finishedAt;
        public String resultSummary;
        /** Exact tool-call arguments captured from the provider-neutral ToolCall. */
        public String inputJson;
        /** Bounded executor result summary; never model-authored narration. */
        public String outputText;

        public ToolEvent(String name) {
            this(name, null);
        }

        public ToolEvent(String name, String domain) {
            this.name = name;
            this.domain = domain;
            this.status = "RUNNING";
            this.startedAt = System.currentTimeMillis();
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("name", name);
                json.put("status", status);
                if (domain != null) json.put("domain", domain);
                json.put("startedAt", startedAt);
                json.put("finishedAt", finishedAt);
                if (resultSummary != null) json.put("resultSummary", resultSummary);
                if (inputJson != null) json.put("inputJson", inputJson);
                if (outputText != null) json.put("outputText", outputText);
            } catch (JSONException ignored) {}
            return json;
        }

        public static ToolEvent fromJson(JSONObject json) {
            ToolEvent te = new ToolEvent(json.optString("name"), json.optString("domain", null));
            te.status = json.optString("status", "OK");
            te.startedAt = json.optLong("startedAt");
            te.finishedAt = json.optLong("finishedAt");
            te.resultSummary = json.optString("resultSummary", null);
            te.inputJson = json.optString("inputJson", null);
            te.outputText = json.optString("outputText", null);
            return te;
        }
    }

    public static class Session {
        public String id;
        public String title = "";
        public long createdAt;
        public long updatedAt;
        public List<Message> messages = new ArrayList<>();

        public Session() {
            long now = System.currentTimeMillis();
            id = String.valueOf(now);
            createdAt = now;
            updatedAt = now;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("title", title == null ? "" : title);
                json.put("createdAt", createdAt);
                json.put("updatedAt", updatedAt > 0 ? updatedAt : createdAt);
                JSONArray msgs = new JSONArray();
                for (Message m : messages) msgs.put(m.toJson());
                json.put("messages", msgs);
            } catch (JSONException ignored) {}
            return json;
        }

        public static Session fromJson(JSONObject json) {
            Session s = new Session();
            s.id = json.optString("id");
            s.title = json.optString("title", "");
            s.createdAt = json.optLong("createdAt", s.createdAt);
            s.updatedAt = json.optLong("updatedAt", s.createdAt);
            JSONArray msgs = json.optJSONArray("messages");
            if (msgs != null) {
                for (int i = 0; i < msgs.length(); i++) {
                    JSONObject m = msgs.optJSONObject(i);
                    if (m != null) s.messages.add(Message.fromJson(m));
                }
            }
            if (s.updatedAt <= 0) s.updatedAt = s.createdAt;
            for (Message message : s.messages) {
                if (message != null) s.updatedAt = Math.max(s.updatedAt, message.time);
                if ((s.title == null || s.title.trim().isEmpty()) && message != null
                        && "user".equals(message.role) && message.text != null && !message.text.trim().isEmpty()) {
                    s.title = normalizeTitle(message.text);
                }
            }
            return s;
        }
    }

    public void saveSession(Session session) {
        if (session == null || session.id == null || session.id.trim().isEmpty()) return;
        prepareSessionForSave(session);
        writeSession(session.id, session.toJson().toString());
    }

    /**
     * Capture a consistent transcript snapshot now, then perform disk I/O and fsync off the UI
     * thread. A single writer keeps repeated updates to the same session in order.
     */
    public void saveSessionAsync(Session session) {
        if (session == null || session.id == null || session.id.trim().isEmpty()) return;
        prepareSessionForSave(session);
        final String sessionId = session.id;
        final String snapshot = session.toJson().toString();
        SAVE_EXECUTOR.execute(() -> writeSession(sessionId, snapshot));
    }

    private void prepareSessionForSave(Session session) {
        session.updatedAt = Math.max(System.currentTimeMillis(), session.updatedAt);
        if (session.title == null || session.title.trim().isEmpty()) {
            for (Message message : session.messages) {
                if (message != null && "user".equals(message.role)
                        && message.text != null && !message.text.trim().isEmpty()) {
                    session.title = normalizeTitle(message.text);
                    break;
                }
            }
        }
    }

    private static String normalizeTitle(String text) {
        if (text == null) return "New chat";
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) return "New chat";
        return normalized.length() > 64 ? normalized.substring(0, 61).trim() + "..." : normalized;
    }

    private synchronized void writeSession(String sessionId, String serialized) {
        if (!baseDir.exists() && !baseDir.mkdirs() && !baseDir.isDirectory()) {
            Log.e(TAG, "Failed to create session directory: " + baseDir.getAbsolutePath());
            return;
        }
        File file = new File(baseDir, sessionId + ".json");
        AtomicFile atomicFile = new AtomicFile(file);
        FileOutputStream out = null;
        try {
            out = atomicFile.startWrite();
            out.write(serialized.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
            atomicFile.finishWrite(out);
        } catch (IOException e) {
            if (out != null) atomicFile.failWrite(out);
            Log.e(TAG, "Failed to save session " + sessionId, e);
        }
    }

    public List<Session> loadAllSessions() {
        List<Session> sessions = new ArrayList<>();
        File[] files = baseDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                try (FileInputStream in = new AtomicFile(f).openRead()) {
                    // AtomicFile.openRead() restores a previous complete backup if an
                    // interrupted write left the base file incomplete.
                    long fileLength = f.length();
                    if (fileLength > Integer.MAX_VALUE) {
                        Log.w(TAG, "Skipping oversized session file: " + f.getName());
                        continue;
                    }
                    byte[] data = new byte[(int) fileLength];
                    int offset = 0;
                    while (offset < data.length) {
                        int count = in.read(data, offset, data.length - offset);
                        if (count < 0) break;
                        offset += count;
                    }
                    if (offset != data.length) {
                        Log.w(TAG, "Skipping incomplete session file: " + f.getName());
                        continue;
                    }
                    sessions.add(Session.fromJson(new JSONObject(new String(data, StandardCharsets.UTF_8))));
                } catch (Exception e) {
                    Log.w(TAG, "Corrupt session file: " + f.getName());
                }
            }
        }
        sessions.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return sessions;
    }

    public void deleteSession(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]+")) return;
        File file = new File(baseDir, id + ".json");
        if (file.exists() && !file.delete()) Log.w(TAG, "Could not delete session " + id);
    }

    public static Session branchSession(Session source, int upToIndexInclusive) {
        Session branch = new Session();
        branch.id = System.currentTimeMillis() + "_branch";
        if (source.messages.size() > 0) {
            String firstText = source.messages.get(0).text;
            if (firstText.length() > 30) firstText = firstText.substring(0, 30) + "...";
            // title logic would go in metadata, but for now we just label the session.
        }
        branch.title = (source.title == null || source.title.trim().isEmpty() ? "Conversation" : source.title.trim()) + " — branch";
        branch.createdAt = System.currentTimeMillis();
        for (int i = 0; i <= upToIndexInclusive && i < source.messages.size(); i++) {
            Message m = source.messages.get(i);
            if (m == null) continue;
            Message copy = new Message(m.role, m.text);
            copy.time = m.time; copy.toolState = m.toolState; copy.action = m.action;
            copy.reasoning = m.reasoning; copy.reasoningSeconds = m.reasoningSeconds; copy.favorite = m.favorite;
            if (m.toolEvents != null) {
                copy.toolEvents = new ArrayList<>();
                for (ToolEvent event : m.toolEvents) {
                    if (event == null) continue;
                    ToolEvent ec = new ToolEvent(event.name, event.domain);
                    ec.status = event.status; ec.startedAt = event.startedAt; ec.finishedAt = event.finishedAt; ec.resultSummary = event.resultSummary;
                    copy.toolEvents.add(ec);
                }
            }
            branch.messages.add(copy);
        }
        return branch;
    }
}
