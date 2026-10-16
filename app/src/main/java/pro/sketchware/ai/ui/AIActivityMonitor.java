package pro.sketchware.ai.ui;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import pro.sketchware.ai.agent.AgentOrchestrator;
import java.util.List;
import pro.sketchware.ai.agent.SessionState;

public final class AIActivityMonitor {

    private static volatile SessionState state = SessionState.IDLE;
    private static final List<OnStateChangeListener> listeners = new ArrayList<>();
    private static final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private static Runnable watchdogRunnable;
    private static volatile Runnable onTimeoutAction;

    public interface OnStateChangeListener {
        void onStateChanged(SessionState newState);
    }

    public static synchronized void setState(SessionState newState) {
        if (state == newState) return;
        state = newState;
        resetWatchdog();
        for (OnStateChangeListener listener : new ArrayList<>(listeners)) {
            listener.onStateChanged(newState);
        }
    }

    public static SessionState getState() {
        return state;
    }

    public static boolean isBusy() {
        return state != SessionState.IDLE;
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
        if (watchdogRunnable != null) {
            watchdogHandler.removeCallbacks(watchdogRunnable);
            watchdogRunnable = null;
        }
        if (state == SessionState.IDLE) return;

        watchdogRunnable = () -> {
            if (state != SessionState.IDLE) {
                if (onTimeoutAction != null) onTimeoutAction.run();
                setState(SessionState.IDLE);
            }
        };
        // Keep the monitor deadline aligned with the agent's hard task deadline.
        watchdogHandler.postDelayed(watchdogRunnable, AgentOrchestrator.MAX_TASK_DURATION_MS);
    }

    public static synchronized void poke() {
        resetWatchdog();
    }
}
