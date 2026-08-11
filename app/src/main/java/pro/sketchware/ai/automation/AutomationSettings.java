package pro.sketchware.ai.automation;

import android.content.Context;

import pro.sketchware.ai.data.AiStorage;

/**
 * [WHAT] SSOT for Automation slot settings (P2-AU, D17).
 * [WHY] Two independent gates backed by AiStorage kv (both default false):
 * - auto_tasks_loops: registers the create_task/list_tasks/delete_task/
 *   start_loop/stop_loop agent tools (ToolRegistry.syncAutomation).
 * - auto_exact_alarms: chosen at EVERY schedule call by AlarmScheduler
 *   (exact => setExactAndAllowWhileIdle, else setAndAllowWhileIdle).
 * [HOW] Single writers: setTasksLoopsEnabled / setExactAlarms. The ONLY
 * callers are the Automation UI and the exact-alarm permission flow (R5/R16).
 */
public class AutomationSettings {

    private static AutomationSettings instance;
    private final AiStorage storage;

    private AutomationSettings(Context context) {
        this.storage = AiStorage.get(context);
    }

    public static synchronized AutomationSettings get(Context context) {
        if (instance == null) {
            instance = new AutomationSettings(context.getApplicationContext());
        }
        return instance;
    }

    // --- Tasks & Loops tool gate (def false) ---

    public boolean isTasksLoopsEnabled() {
        return storage.isAutoTasksLoopsEnabled();
    }

    /** Single writer (R5/R16): callers = Automation UI + permission flow only. */
    public void setTasksLoopsEnabled(boolean enabled) {
        storage.setAutoTasksLoopsEnabled(enabled);
    }

    // --- Exact alarms gate (def false) ---

    public boolean isExactAlarms() {
        return storage.isAutoExactAlarmsEnabled();
    }

    /** Single writer (R5/R16): callers = Automation UI + permission flow only. */
    public void setExactAlarms(boolean enabled) {
        storage.setAutoExactAlarmsEnabled(enabled);
    }
}
