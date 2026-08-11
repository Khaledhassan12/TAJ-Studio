package pro.sketchware.ai.automation;

import android.content.Context;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.data.AiStorage;

/**
 * [WHAT] SSOT + single writer for Tasks (P2-AU, D17).
 * [WHY] Persisted as JSON array in AiStorage kv "auto_tasks"; every
 * mutation goes through this class (R5).
 */
public class TaskStore {

    public static final String KEY_TASKS = "auto_tasks";

    private static TaskStore instance;
    private final AiStorage storage;

    private TaskStore(Context context) {
        this.storage = AiStorage.get(context);
    }

    public static synchronized TaskStore get(Context context) {
        if (instance == null) {
            instance = new TaskStore(context.getApplicationContext());
        }
        return instance;
    }

    public synchronized List<TaskEntity> list() {
        List<TaskEntity> out = new ArrayList<>();
        String json = storage.kvGet(KEY_TASKS);
        if (json == null || json.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                out.add(TaskEntity.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {
            // Corrupt entry never crashes the app; honest empty list instead.
        }
        return out;
    }

    public synchronized TaskEntity findById(String id) {
        if (id == null) return null;
        for (TaskEntity t : list()) {
            if (id.equals(t.id)) return t;
        }
        return null;
    }

    public synchronized TaskEntity findByName(String name) {
        if (name == null) return null;
        for (TaskEntity t : list()) {
            if (name.equalsIgnoreCase(t.name)) return t;
        }
        return null;
    }

    public synchronized void upsert(TaskEntity task) {
        List<TaskEntity> all = list();
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(task.id)) {
                all.set(i, task);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(task);
        save(all);
    }

    public synchronized void delete(String id) {
        List<TaskEntity> all = list();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).id.equals(id)) {
                all.remove(i);
            }
        }
        save(all);
    }

    private void save(List<TaskEntity> all) {
        try {
            JSONArray arr = new JSONArray();
            for (TaskEntity t : all) {
                arr.put(t.toJson());
            }
            storage.kvPut(KEY_TASKS, arr.toString());
        } catch (Exception ignored) {
            // Serialization of our own entities cannot fail; never crash.
        }
    }
}
