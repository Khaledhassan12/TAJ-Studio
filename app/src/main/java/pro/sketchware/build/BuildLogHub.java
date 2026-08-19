package pro.sketchware.build;

import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BuildLogHub {

    public enum Phase {
        IDLE,
        RUNNING,
        SUCCESS,
        FAILURE,
        CANCELED
    }

    public static class LogEntry {
        public final String message;
        public final long timestamp;

        public LogEntry(String message) {
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public interface BuildListener {
        void onBuildEvent(Phase phase, String message, List<LogEntry> history);
    }

    private static BuildLogHub instance;

    private final List<LogEntry> history = new ArrayList<>();
    private final List<WeakReference<BuildListener>> listeners = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Phase currentPhase = Phase.IDLE;
    private String lastMessage = "";

    private BuildLogHub() {
    }

    public static synchronized BuildLogHub getInstance() {
        if (instance == null) {
            instance = new BuildLogHub();
        }
        return instance;
    }

    public synchronized void newSession() {
        history.clear();
        currentPhase = Phase.RUNNING;
        lastMessage = "Build started";
        notifyListeners();
    }

    public synchronized void publish(String message) {
        history.add(new LogEntry(message));
        lastMessage = message;
        notifyListeners();
    }

    public synchronized void publish(Phase phase, String message) {
        if (phase != null) {
            currentPhase = phase;
        }
        history.add(new LogEntry(message));
        lastMessage = message;
        notifyListeners();
    }

    public void addListener(BuildListener listener) {
        removeListener(listener);
        listeners.add(new WeakReference<>(listener));
        // Deliver current state immediately
        synchronized (this) {
            listener.onBuildEvent(currentPhase, lastMessage, new ArrayList<>(history));
        }
    }

    public void removeListener(BuildListener listener) {
        for (WeakReference<BuildListener> ref : listeners) {
            BuildListener l = ref.get();
            if (l == null || l == listener) {
                listeners.remove(ref);
            }
        }
    }

    private void notifyListeners() {
        final Phase phase = currentPhase;
        final String message = lastMessage;
        final List<LogEntry> snapshot = new ArrayList<>(history);

        mainHandler.post(() -> {
            for (WeakReference<BuildListener> ref : listeners) {
                BuildListener listener = ref.get();
                if (listener != null) {
                    listener.onBuildEvent(phase, message, snapshot);
                } else {
                    listeners.remove(ref);
                }
            }
        });
    }

    public synchronized Phase getCurrentPhase() {
        return currentPhase;
    }

    public synchronized List<LogEntry> getHistory() {
        return new ArrayList<>(history);
    }
}
