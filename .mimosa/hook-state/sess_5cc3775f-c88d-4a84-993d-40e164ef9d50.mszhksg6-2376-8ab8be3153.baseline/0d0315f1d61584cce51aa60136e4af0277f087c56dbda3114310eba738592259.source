package pro.sketchware.marketplace.services;

import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * [R5 State Law] Single Source of Truth for library installation states.
 * (عربي) مصدر الحقيقة الوحيد لحالات تثبيت المكتبات.
 */
public final class InstallStateHub {
    public enum State { IDLE, QUEUED, DOWNLOADING, EXTRACTING, DEXING, SUCCESS, FAILED }

    public static final class Entry {
        public final State state;
        public final int progress;
        public final String message;
        public final long timestamp;

        Entry(State s, int p, String m) {
            state = s;
            progress = p;
            message = m;
            timestamp = System.currentTimeMillis();
        }
    }

    public interface Listener {
        void onStateChanged(String coordinate, Entry entry);
    }

    private static final InstallStateHub INSTANCE = new InstallStateHub();

    public static InstallStateHub get() {
        return INSTANCE;
    }

    public static InstallStateHub getInstance() {
        return INSTANCE;
    }

    private final Map<String, Entry> map = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    private InstallStateHub() {}

    public void update(String coordinate, State state, int progress, String message) {
        Entry e = new Entry(state, progress, message);
        map.put(coordinate, e);
        for (Listener l : listeners) {
            main.post(() -> l.onStateChanged(coordinate, e));
        }
    }

    public Entry get(String coordinate) {
        return map.get(coordinate);
    }

    /**
     * WHAT: activeEntryForArtifact - Finds any active task for a specific artifact.
     * WHY: Prevents B1 desync where one version is installed but another is downloading.
     * (عربي) البحث عن أي مهمة نشطة للأرتيفاكت؛ يمنع تضارب الشارات بين الإصدارات المختلفة.
     */
    public Entry activeEntryForArtifact(String artifact) {
        if (artifact == null || artifact.isEmpty()) return null;
        for (Map.Entry<String, Entry> e : map.entrySet()) {
            if (e.getKey().contains(":" + artifact + ":")) {
                if (e.getValue().state != State.IDLE && e.getValue().state != State.FAILED) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }
}
