package pro.sketchware.ai.bus;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;

/**
 * [WHAT] Central event bus for AI-related events.
 * [WHY] Decouples UI from background model management (R8).
 * [HOW] Deliver events on the main thread via listeners.
 */
public class AiEventHub {

    public enum Event {
        SEARCHING, SEARCH_DONE, DOWNLOAD_PROGRESS, DOWNLOAD_SUCCESS, DOWNLOAD_FAILED, MODELS_CHANGED, ERROR
    }

    public static class Entry {
        public Event event;
        public Object payload;
        public long timestamp;

        public Entry(Event event, Object payload) {
            this.event = event;
            this.payload = payload;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public interface Listener {
        void onAiEvent(Entry entry);
    }

    private static AiEventHub instance;
    private final List<Listener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static synchronized AiEventHub get() {
        if (instance == null) instance = new AiEventHub();
        return instance;
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public void publish(Event event, Object payload) {
        final Entry entry = new Entry(event, payload);
        mainHandler.post(() -> {
            for (Listener l : listeners) l.onAiEvent(entry);
        });
    }
}
