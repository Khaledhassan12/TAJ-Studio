package pro.sketchware.ai.ui;

import android.os.Handler;
import android.os.Looper;

/**
 * Central threading funnel for TAG Assistant.
 * Ensures all UI mutations happen on the main thread safely.
 */
public final class UiPoster {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void post(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            MAIN.post(r);
        }
    }

    private UiPoster() {}
}
