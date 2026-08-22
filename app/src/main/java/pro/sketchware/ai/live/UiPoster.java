package pro.sketchware.ai.live;

import android.os.Handler;
import android.os.Looper;

public final class UiPoster {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void post(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            MAIN.post(r);
        }
    }

    public static void postDelayed(Runnable r, long ms) {
        MAIN.postDelayed(r, ms);
    }

    public static void remove(Runnable r) {
        MAIN.removeCallbacks(r);
    }

    private UiPoster() {}
}
