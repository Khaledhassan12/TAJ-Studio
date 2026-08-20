package pro.sketchware.ai.ui;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

public final class AIActivityMonitor {

    public enum State { IDLE, MODEL_STREAMING, TOOL_RUNNING, WAITING }

    private static volatile State state = State.IDLE;
    private static final List<OnStateChangeListener> listeners = new ArrayList<>();
    private static final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private static Runnable watchdogRunnable;
    private static Runnable onTimeoutAction;

    public interface OnStateChangeListener {
        void onStateChanged(State newState);
    }

    public static synchronized void setState(State newState) {
        if (state == newState) return;
        state = newState;
        resetWatchdog();
        for (OnStateChangeListener listener : new ArrayList<>(listeners)) {
            listener.onStateChanged(newState);
        }
    }

    public static State getState() {
        return state;
    }

    public static boolean isBusy() {
        return state != State.IDLE;
    }

    public static synchronized void addListener(OnStateChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            listener.onStateChanged(state);
        }
    }

    public static synchronized void removeListener(OnStateChangeListener listener) {
        listeners.remove(listener);
    }

    public static void setOnTimeoutAction(Runnable action) {
        onTimeoutAction = action;
    }

    private static void resetWatchdog() {
        watchdogHandler.removeCallbacks(watchdogRunnable);
        if (state == State.IDLE) return;

        watchdogRunnable = () -> {
            if (state != State.IDLE) {
                if (onTimeoutAction != null) onTimeoutAction.run();
                setState(State.IDLE);
            }
        };
        watchdogHandler.postDelayed(watchdogRunnable, 120000);
    }

    public static void poke() {
        resetWatchdog();
    }
}
