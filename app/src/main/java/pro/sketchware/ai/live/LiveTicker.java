package pro.sketchware.ai.live;

import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;

public final class LiveTicker {

    public interface TickListener {
        void onTick(long elapsedMs);
    }

    private static final List<TickListener> listeners = new ArrayList<>();
    private static long startTime = 0;
    private static final Runnable tickTask = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.elapsedRealtime();
            for (TickListener l : new ArrayList<>(listeners)) {
                l.onTick(now - startTime);
            }
            UiPoster.postDelayed(this, 1000);
        }
    };

    public static synchronized void add(TickListener l) {
        if (listeners.isEmpty()) {
            startTime = SystemClock.elapsedRealtime();
            UiPoster.post(tickTask);
        }
        if (!listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public static synchronized void remove(TickListener l) {
        listeners.remove(l);
        if (listeners.isEmpty()) {
            UiPoster.remove(tickTask);
        }
    }

    private LiveTicker() {}
}
