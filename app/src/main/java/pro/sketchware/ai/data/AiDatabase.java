package pro.sketchware.ai.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * [WHAT] Central SQLite database for the AI Manager feature.
 * [WHY] Stores AI models, download states, conversations, and agent history locally without Room.
 * [HOW] Extends SQLiteOpenHelper with a locked schema (v1).
 *
 * [العربية]
 * قاعدة بيانات SQLite المركزية لميزة AI Manager.
 * تقوم بتخزين موديلات الذكاء الاصطناعي وحالات التنزيل والمحادثات وتاريخ الوكيل محلياً.
 */
public class AiDatabase extends SQLiteOpenHelper {

    private static final String TAG = "AiDatabase";
    private static final String DATABASE_NAME = "taj_ai.db";
    private static final int DATABASE_VERSION = 1;

    private static AiDatabase instance;

    public static synchronized AiDatabase get(Context context) {
        if (instance == null) {
            instance = new AiDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private AiDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "Creating AI tables...");
        db.beginTransaction();
        try {
            // models: local and cloud entries
            db.execSQL("CREATE TABLE IF NOT EXISTS models (" +
                    "id TEXT PRIMARY KEY, " +
                    "kind TEXT, " +
                    "provider TEXT, " +
                    "name TEXT, " +
                    "metadataJson TEXT, " +
                    "filePath TEXT, " +
                    "installedAt INTEGER, " +
                    "lastUsedAt INTEGER)");

            // downloads: tracking active downloads
            db.execSQL("CREATE TABLE IF NOT EXISTS downloads (" +
                    "id TEXT PRIMARY KEY, " +
                    "modelId TEXT, " +
                    "state TEXT, " +
                    "progress INTEGER, " +
                    "totalBytes INTEGER, " +
                    "bytesDownloaded INTEGER, " +
                    "errorMsg TEXT, " +
                    "startedAt INTEGER, " +
                    "updatedAt INTEGER)");

            // conversations: per-project AI chats
            db.execSQL("CREATE TABLE IF NOT EXISTS conversations (" +
                    "id TEXT PRIMARY KEY, " +
                    "scId TEXT, " +
                    "title TEXT, " +
                    "modelId TEXT, " +
                    "provider TEXT, " +
                    "createdAt INTEGER, " +
                    "updatedAt INTEGER)");

            // messages: chat history
            db.execSQL("CREATE TABLE IF NOT EXISTS messages (" +
                    "id TEXT PRIMARY KEY, " +
                    "conversationId TEXT, " +
                    "role TEXT, " +
                    "content TEXT, " +
                    "toolCallsJson TEXT, " +
                    "toolResultsJson TEXT, " +
                    "createdAt INTEGER)");

            // agent_steps: granular agent loop tracking
            db.execSQL("CREATE TABLE IF NOT EXISTS agent_steps (" +
                    "id TEXT PRIMARY KEY, " +
                    "conversationId TEXT, " +
                    "messageId TEXT, " +
                    "action TEXT, " +
                    "payloadJson TEXT, " +
                    "createdAt INTEGER)");

            // kv: general purpose key-value storage for AI settings
            db.execSQL("CREATE TABLE IF NOT EXISTS kv (" +
                    "key TEXT PRIMARY KEY, " +
                    "value TEXT, " +
                    "updatedAt INTEGER)");

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrading AI database from " + oldVersion + " to " + newVersion);
        // Migration hook for future versions
    }
}
