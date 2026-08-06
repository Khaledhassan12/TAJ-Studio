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

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }
}
