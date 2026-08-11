package pro.sketchware.ai.automation;

import android.content.Context;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.data.AiStorage;

/**
 * [WHAT] SSOT + single writer for Loops (P2-AU, D17).
 * [WHY] Persisted as JSON array in AiStorage kv "auto_loops"; every
 * mutation goes through this class (R5).
 */
public class LoopStore {

    public static final String KEY_LOOPS = "auto_loops";

    private static LoopStore instance;
    private final AiStorage storage;

    private LoopStore(Context context) {
        this.storage = AiStorage.get(context);
    }

    public static synchronized LoopStore get(Context context) {
        if (instance == null) {
            instance = new LoopStore(context.getApplicationContext());
        }
        return instance;
    }

    public synchronized List<LoopEntity> list() {
        List<LoopEntity> out = new ArrayList<>();
        String json = storage.kvGet(KEY_LOOPS);
        if (json == null || json.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                out.add(LoopEntity.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {
            // Corrupt entry never crashes the app; honest empty list instead.
        }
        return out;
    }

    public synchronized LoopEntity findById(String id) {
        if (id == null) return null;
        for (LoopEntity l : list()) {
            if (id.equals(l.id)) return l;
        }
        return null;
    }

    public synchronized LoopEntity findByName(String name) {
        if (name == null) return null;
        for (LoopEntity l : list()) {
            if (name.equalsIgnoreCase(l.name)) return l;
        }
        return null;
    }

    public synchronized void upsert(LoopEntity loop) {
        List<LoopEntity> all = list();
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(loop.id)) {
                all.set(i, loop);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(loop);
        save(all);
    }

    public synchronized void delete(String id) {
        List<LoopEntity> all = list();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).id.equals(id)) {
                all.remove(i);
            }
        }
        save(all);
    }

    private void save(List<LoopEntity> all) {
        try {
            JSONArray arr = new JSONArray();
            for (LoopEntity l : all) {
                arr.put(l.toJson());
            }
            storage.kvPut(KEY_LOOPS, arr.toString());
        } catch (Exception ignored) {
            // Serialization of our own entities cannot fail; never crash.
        }
    }
}
