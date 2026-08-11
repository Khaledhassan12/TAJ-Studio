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

    public static final String KEY_IMG_GEN_ENABLED = "img_gen_enabled";
    public static final String KEY_IMG_GEN_MODEL = "img_gen_model";
    public static final String KEY_IMG_GEN_SIZE_W = "img_gen_size_w";
    public static final String KEY_IMG_GEN_SIZE_H = "img_gen_size_h";

    public static final String KEY_WEB_SEARCH_ENABLED = "ws_enabled";
    public static final String KEY_WEB_SEARCH_PROVIDER = "ws_provider";
    public static final String KEY_WEB_SEARCH_KEYS = "ws_keys";
    public static final String KEY_WEB_SEARCH_SEARXNG_URL = "ws_searxng_url";
    public static final String KEY_WEB_SEARCH_NUM_RESULTS = "ws_num_results";

    public static final String KEY_AUTO_TASKS_LOOPS = "auto_tasks_loops";
    public static final String KEY_AUTO_EXACT_ALARMS = "auto_exact_alarms";

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

    // --- Image Generation (P2-IG) ---

    public boolean isImageGenEnabled() {
        String val = kvGet(KEY_IMG_GEN_ENABLED);
        return val != null && Boolean.parseBoolean(val);
    }

    public void setImageGenEnabled(boolean enabled) {
        kvPut(KEY_IMG_GEN_ENABLED, String.valueOf(enabled));
    }

    public String getImageGenModel() {
        return kvGet(KEY_IMG_GEN_MODEL);
    }

    public void setImageGenModel(String modelId) {
        kvPut(KEY_IMG_GEN_MODEL, modelId);
    }

    public int getImageGenSizeW() {
        String val = kvGet(KEY_IMG_GEN_SIZE_W);
        try {
            return val != null ? Integer.parseInt(val) : 1024;
        } catch (NumberFormatException e) {
            return 1024;
        }
    }

    public void setImageGenSizeW(int width) {
        kvPut(KEY_IMG_GEN_SIZE_W, String.valueOf(width));
    }

    public int getImageGenSizeH() {
        String val = kvGet(KEY_IMG_GEN_SIZE_H);
        try {
            return val != null ? Integer.parseInt(val) : 1024;
        } catch (NumberFormatException e) {
            return 1024;
        }
    }

    public void setImageGenSizeH(int height) {
        kvPut(KEY_IMG_GEN_SIZE_H, String.valueOf(height));
    }

    // --- Web Search (P2-WS) ---

    public boolean isWebSearchEnabled() {
        String val = kvGet(KEY_WEB_SEARCH_ENABLED);
        return val != null && Boolean.parseBoolean(val);
    }

    public void setWebSearchEnabled(boolean enabled) {
        kvPut(KEY_WEB_SEARCH_ENABLED, String.valueOf(enabled));
    }

    public String getWebSearchProvider() {
        return kvGet(KEY_WEB_SEARCH_PROVIDER);
    }

    public void setWebSearchProvider(String provider) {
        kvPut(KEY_WEB_SEARCH_PROVIDER, provider);
    }

    public String getWebSearchKeysJson() {
        return kvGet(KEY_WEB_SEARCH_KEYS);
    }

    public void setWebSearchKeysJson(String json) {
        kvPut(KEY_WEB_SEARCH_KEYS, json);
    }

    public String getWebSearchSearxngUrl() {
        return kvGet(KEY_WEB_SEARCH_SEARXNG_URL);
    }

    public void setWebSearchSearxngUrl(String url) {
        kvPut(KEY_WEB_SEARCH_SEARXNG_URL, url);
    }

    public int getWebSearchNumResults() {
        String val = kvGet(KEY_WEB_SEARCH_NUM_RESULTS);
        try {
            return val != null ? Integer.parseInt(val) : 5;
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    public void setWebSearchNumResults(int num) {
        kvPut(KEY_WEB_SEARCH_NUM_RESULTS, String.valueOf(num));
    }

    // --- Automation (P2-AU) ---

    public boolean isAutoTasksLoopsEnabled() {
        String val = kvGet(KEY_AUTO_TASKS_LOOPS);
        return val != null && Boolean.parseBoolean(val);
    }

    public void setAutoTasksLoopsEnabled(boolean enabled) {
        kvPut(KEY_AUTO_TASKS_LOOPS, String.valueOf(enabled));
    }

    public boolean isAutoExactAlarmsEnabled() {
        String val = kvGet(KEY_AUTO_EXACT_ALARMS);
        return val != null && Boolean.parseBoolean(val);
    }

    public void setAutoExactAlarmsEnabled(boolean enabled) {
        kvPut(KEY_AUTO_EXACT_ALARMS, String.valueOf(enabled));
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

    /** P2-CS2: single-message lookup for semantic search hit enrichment. */
    public Cursor findMessageById(String messageId) {
        return dbHelper.getReadableDatabase().query("messages", null, "id = ?", new String[]{messageId}, null, null, null);
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

    // --- Embeddings (P2-CS) ---

    public void insertEmbedding(ContentValues values) {
        dbHelper.getWritableDatabase().insertWithOnConflict("conversation_embeddings", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** P2-CS2: all vectors stored for one project under one embedding model. */
    public Cursor listEmbeddings(String scId, String modelRef) {
        return dbHelper.getReadableDatabase().query("conversation_embeddings", null,
                "scId = ? AND modelRef = ?", new String[]{scId, modelRef}, null, null, "ts ASC");
    }

    /** P2-CS2: cascade delete — removing a model removes its index vectors. */
    public int deleteEmbeddingsByModelRef(String modelRef) {
        return dbHelper.getWritableDatabase().delete("conversation_embeddings", "modelRef = ?", new String[]{modelRef});
    }
}
