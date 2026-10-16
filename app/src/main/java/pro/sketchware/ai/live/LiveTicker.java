package pro.sketchware.ai.live;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared main-thread ticker for lightweight live UI counters.
 *
 * The ticker runs only while at least one listener is registered. A generation token prevents
 * a callback already in progress from scheduling another loop after the last listener is removed
 * and a later listener starts a fresh ticker generation.
 */
public final class LiveTicker {

    public interface TickListener {
        void onTick(long elapsedMs);
    }

    private static final List<TickListener> listeners = new ArrayList<>();
    private static long startTime;
    private static long generation;
    private static boolean scheduled;

    private static final Runnable tickTask = new Runnable() {
        @Override
        public void run() {
            final long tickGeneration;
            final List<TickListener> snapshot;
            synchronized (LiveTicker.class) {
                if (listeners.isEmpty() || !scheduled) {
                    scheduled = false;
                    return;
                }
                tickGeneration = generation;
                snapshot = new ArrayList<>(listeners);
            }

            long now = SystemClock.elapsedRealtime();
            for (TickListener listener : snapshot) {
                try {
                    listener.onTick(now - startTime);
                } catch (RuntimeException ignored) {
                    // An individual recycled/broken view must not kill the ticker for other rows.
                }
            }

            synchronized (LiveTicker.class) {
                if (listeners.isEmpty()) {
                    scheduled = false;
                } else if (scheduled && generation == tickGeneration) {
                    UiPoster.postDelayed(this, 1000);
                }
            }
        }
    };

    public static void add(TickListener listener) {
        if (listener == null) return;
        boolean start = false;
        synchronized (LiveTicker.class) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
            if (!scheduled && !listeners.isEmpty()) {
                scheduled = true;
                generation++;
                startTime = SystemClock.elapsedRealtime();
                start = true;
            }
        }
        if (start) UiPoster.post(tickTask);
    }

    public static void remove(TickListener listener) {
        boolean stop = false;
        synchronized (LiveTicker.class) {
            listeners.remove(listener);
            if (listeners.isEmpty() && scheduled) {
                scheduled = false;
                generation++;
                stop = true;
            }
        }
        if (stop) UiPoster.remove(tickTask);
    }

    private LiveTicker() {}
}
