package pro.sketchware.ai.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * [WHAT] Synchronous DAO layer for AiDatabase.
 * [WHY] Provides a clean API for AI data operations while keeping logic separate from the DB helper.
 * [HOW] Wrapper over SQLiteDatabase with insert/update/query methods.
 *
 * [العربية]
 * طبقة DAO متزامنة لقاعدة بيانات الذكاء الاصطناعي.
 * توفر واجهة برمجية نظيفة للعمليات على البيانات مع فصل المنطق عن مساعد قاعدة البيانات.
 */
public class AiStorage {

    public static final String KEY_TITLE_GEN_ENABLED = "title_gen_enabled";
    public static final String KEY_TITLE_GEN_MODEL = "ai_title_model";
    public static final String KEY_TITLE_GEN_PROMPT = "title_gen_prompt";
    public static final String KEY_TITLE_GEN_NOTIFICATIONS = "title_gen_notifications";

    public static final String KEY_TRANSCRIPTION_ENABLED = "transcription_enabled";
    public static final String KEY_TRANSCRIPTION_MODEL = "transcription_model";
    public static final String KEY_TRANSCRIPTION_ENABLED_MODELS = "transcription_enabled_models";
    public static final String KEY_TRANSCRIPTION_PROMPT = "transcription_prompt";
    public static final String KEY_TRANSCRIPTION_BATCH_SIZE = "transcription_batch_size";

    private static AiStorage instance;
    private final Context context;
    private final AiDatabase dbHelper;

    public static synchronized AiStorage get(Context context) {
        if (instance == null) {
            instance = new AiStorage(context.getApplicationContext());
        }
        return instance;
    }

    private AiStorage(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = AiDatabase.get(context);
    }

    public Context getContext() {
        return context;
    }

    public boolean isTitleGenEnabled() {
        String val = kvGet(KEY_TITLE_GEN_ENABLED);
        return val != null && Boolean.parseBoolean(val);
    }

    public void setTitleGenEnabled(boolean enabled) {
        kvPut(KEY_TITLE_GEN_ENABLED, String.valueOf(enabled));
    }

    public String getTitleGenModel() {
        return kvGet(KEY_TITLE_GEN_MODEL);
    }

    public void setTitleGenModel(String modelId) {
        kvPut(KEY_TITLE_GEN_MODEL, modelId);
    }

    public String getTitleGenPrompt() {
        String val = kvGet(KEY_TITLE_GEN_PROMPT);
        return val != null ? val : "You are a title generator. Output only a short title in the same language as the conversation.";
    }

    public void setTitleGenPrompt(String prompt) {
        kvPut(KEY_TITLE_GEN_PROMPT, prompt);
    }

    public boolean isTitleGenNotificationsEnabled() {
        String val = kvGet(KEY_TITLE_GEN_NOTIFICATIONS);
        return val != null && Boolean.parseBoolean(val);
    }

    public void setTitleGenNotificationsEnabled(boolean enabled) {
        kvPut(KEY_TITLE_GEN_NOTIFICATIONS, String.valueOf(enabled));
    }

    public boolean isTranscriptionEnabled() {
        return Boolean.parseBoolean(kvGet(KEY_TRANSCRIPTION_ENABLED));
    }

    public void setTranscriptionEnabled(boolean enabled) {
        kvPut(KEY_TRANSCRIPTION_ENABLED, String.valueOf(enabled));
    }

    public String getTranscriptionModel() {
        return kvGet(KEY_TRANSCRIPTION_MODEL);
    }

    public void setTranscriptionModel(String modelId) {
        kvPut(KEY_TRANSCRIPTION_MODEL, modelId);
    }

    public String getTranscriptionEnabledModelsJson() {
        String val = kvGet(KEY_TRANSCRIPTION_ENABLED_MODELS);
        return val != null ? val : "[]";
    }

    public void setTranscriptionEnabledModelsJson(String json) {
        kvPut(KEY_TRANSCRIPTION_ENABLED_MODELS, json);
    }

    public String getTranscriptionPrompt() {
        String val = kvGet(KEY_TRANSCRIPTION_PROMPT);
        return val != null ? val : "Please describe this image in detail. Include all visible text, data, charts, layout, and visual elements. Preserve the original language of any text shown.";
    }

    public void setTranscriptionPrompt(String prompt) {
        kvPut(KEY_TRANSCRIPTION_PROMPT, prompt);
    }

    public int getTranscriptionBatchSize() {
        String val = kvGet(KEY_TRANSCRIPTION_BATCH_SIZE);
        try {
            return val != null ? Integer.parseInt(val) : 3;
        } catch (NumberFormatException e) {
            return 3;
        }
    }

    public void setTranscriptionBatchSize(int size) {
        kvPut(KEY_TRANSCRIPTION_BATCH_SIZE, String.valueOf(size));
    }

    // --- KV Storage ---

    public String kvGet(String key) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("kv", new String[]{"value"}, "\"key\" = ?", new String[]{key}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        }
        return null;
    }

    public void kvPut(String key, String value) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("key", key);
        cv.put("value", value);
        cv.put("updatedAt", System.currentTimeMillis());
        db.insertWithOnConflict("kv", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // --- Models ---

    public void insertModel(ContentValues values) {
        dbHelper.getWritableDatabase().insertWithOnConflict("models", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void updateModel(String id, ContentValues values) {
        dbHelper.getWritableDatabase().update("models", values, "id = ?", new String[]{id});
    }

    public Cursor listModels() {
        return dbHelper.getReadableDatabase().query("models", null, null, null, null, null, "lastUsedAt DESC");
    }

    public Cursor findModel(String id) {
        return dbHelper.getReadableDatabase().query("models", null, "id = ?", new String[]{id}, null, null, null);
    }

    public void deleteModel(String id) {
        dbHelper.getWritableDatabase().delete("models", "id = ?", new String[]{id});
    }

    // --- Downloads ---

    public void insertDownload(ContentValues values) {
        dbHelper.getWritableDatabase().insertWithOnConflict("downloads", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void updateDownload(String id, ContentValues values) {
        dbHelper.getWritableDatabase().update("downloads", values, "id = ?", new String[]{id});
    }

    public Cursor findActiveDownload() {
        return dbHelper.getReadableDatabase().query("downloads", null, "state = ?", new String[]{"DOWNLOADING"}, null, null, null);
    }

    // --- Conversations ---

    public void insertConversation(ContentValues values) {
        dbHelper.getWritableDatabase().insertWithOnConflict("conversations", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void updateConversation(String id, ContentValues values) {
        dbHelper.getWritableDatabase().update("conversations", values, "id = ?", new String[]{id});
    }

    public boolean updateTitleIfPlaceholder(String id, String newTitle, String placeholder) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", newTitle);
        cv.put("updatedAt", System.currentTimeMillis());
        int rows = db.update("conversations", cv, "id = ? AND title = ?", new String[]{id, placeholder});
        return rows > 0;
    }

    public Cursor listConversations(String scId) {
        return dbHelper.getReadableDatabase().query("conversations", null, "scId = ?", new String[]{scId}, null, null, "updatedAt DESC");
    }

    // --- Messages ---

    public void insertMessage(ContentValues values) {
        dbHelper.getWritableDatabase().insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void updateMessage(String id, ContentValues values) {
        dbHelper.getWritableDatabase().update("messages", values, "id = ?", new String[]{id});
    }

    public Cursor listMessages(String conversationId) {
        return dbHelper.getReadableDatabase().query("messages", null, "conversationId = ?", new String[]{conversationId}, null, null, "createdAt ASC");
    }

    // --- Agent Steps ---

    public void insertAgentStep(ContentValues values) {
        dbHelper.getWritableDatabase().insertWithOnConflict("agent_steps", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Cursor listAgentSteps(String conversationId) {
        return dbHelper.getReadableDatabase().query("agent_steps", null, "conversationId = ?", new String[]{conversationId}, null, null, "createdAt ASC");
    }

    public Cursor listAgentStepsByRecent(int limit) {
        return dbHelper.getReadableDatabase().query("agent_steps", null, null, null, null, null, "createdAt DESC", String.valueOf(limit));
    }
}
