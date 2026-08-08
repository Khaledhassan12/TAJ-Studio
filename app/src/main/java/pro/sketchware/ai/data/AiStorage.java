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

    private static AiStorage instance;
    private final AiDatabase dbHelper;

    public static synchronized AiStorage get(Context context) {
        if (instance == null) {
            instance = new AiStorage(context.getApplicationContext());
        }
        return instance;
    }

    private AiStorage(Context context) {
        this.dbHelper = AiDatabase.get(context);
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
}
