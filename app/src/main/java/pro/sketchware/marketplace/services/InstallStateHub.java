package pro.sketchware.marketplace.services;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [R5] InstallStateHub - مصدر الحقيقة الوحيد لحالة تثبيت المكتبات.
 * InstallStateHub - Single Source of Truth for library installation states.
 */
public class InstallStateHub {

    public enum State { IDLE, QUEUED, DOWNLOADING, EXTRACTING, DEXING, SUCCESS, FAILED }

    public static class Entry {
        public final State state;
        public final int progress;
        public final String message;
        public final long timestamp;

        public Entry(State state, int progress, String message) {
            this.state = state;
            this.progress = progress;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public interface Listener {
        void onStateChanged(String coordinate, Entry entry);
    }

    private static InstallStateHub instance;
    private final ConcurrentHashMap<String, Entry> states = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private InstallStateHub() {}

    public static synchronized InstallStateHub getInstance() {
        if (instance == null) instance = new InstallStateHub();
        return instance;
    }

    /**
     * تحديث الحالة وبث التغيير لكافة المستمعين.
     * [R5-2] الكاتب الوحيد للحالة (يُستدعى من الخدمة).
     */
    public void update(String coordinate, State state, int progress, String message) {
        Entry entry = new Entry(state, progress, message);
        states.put(coordinate, entry);
        
        mainHandler.post(() -> {
            synchronized (listeners) {
                for (Listener listener : listeners) {
                    listener.onStateChanged(coordinate, entry);
                }
            }
        });
    }

    public Entry get(String coordinate) {
        return states.get(coordinate);
    }

    public void addListener(Listener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }
    
    public void clear(String coordinate) {
        states.remove(coordinate);
    }
}
