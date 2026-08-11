package pro.sketchware.ai.automation;

import android.content.Context;
import android.util.Log;

import java.util.Date;
import java.util.List;

/**
 * [WHAT] Scheduling + fire handling for Tasks (P2-AU, D17).
 * [WHY] Cron tasks reschedule their next occurrence after every fire;
 * one-shot tasks run once and are disabled honestly. Fires execute headlessly
 * in the creating conversation and log an execution summary.
 */
public class TaskManager {

    public static final String TAG = "TaskManager";

    /** Schedules the next fire of a task via AlarmScheduler (exactness per call). */
    public static void schedule(Context context, TaskEntity task) {
        long at = nextFireTime(task, System.currentTimeMillis());
        if (at <= 0) {
            Log.w(TAG, "schedule: no future fire for task '" + task.name + "' — nothing scheduled");
            return;
        }
        AlarmScheduler.schedule(context, AlarmScheduler.ACTION_TASK_FIRE, task.id, at);
    }

    /** @return next fire millis strictly after now, or -1 when none exists. */
    public static long nextFireTime(TaskEntity task, long now) {
        if (task.isCron()) {
            try {
                return new CronExpression(task.cronExpr).nextFire(now);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "nextFireTime: invalid cron for task '" + task.name + "': " + e.getMessage());
                return -1;
            }
        }
        return task.runAt > now ? task.runAt : -1;
    }

    /** Alarm fire entry point (called from AutomationReceiver off-main). */
    public static void onAlarmFire(Context context, String taskId) {
        Context appCtx = context.getApplicationContext();
        TaskStore store = TaskStore.get(appCtx);
        TaskEntity task = store.findById(taskId);
        if (task == null || !task.enabled) {
            Log.i(TAG, "fire: task missing or disabled (id=" + taskId + ") — skipped honestly");
            return;
        }
        Log.i(TAG, "fire: task '" + task.name + "' executing headless in conversation " + task.conversationId);
        HeadlessRunner.run(appCtx, task.scId, task.conversationId, task.prompt, task.modelId,
                (ok, summary) -> {
                    Log.i(TAG, "execution summary: task '" + task.name + "' ok=" + ok + ", " + summary);
                    if (task.isCron()) {
                        schedule(appCtx, task); // next occurrence
                    } else {
                        TaskEntity fresh = store.findById(taskId);
                        if (fresh != null) {
                            fresh.enabled = false; // one-shot completed
                            store.upsert(fresh);
                        }
                        Log.i(TAG, "one-shot task '" + task.name + "' completed and disabled");
                    }
                });
    }

    /** Cancels any pending alarm for the task. */
    public static void cancel(Context context, String taskId) {
        AlarmScheduler.cancel(context, AlarmScheduler.ACTION_TASK_FIRE, taskId);
    }

    /**
     * App-start hook (SketchApplication): re-arm every persisted task.
     * Past one-shots are disabled honestly with a log line, never fired late.
     */
    public static void rescheduleAll(Context context) {
        Context appCtx = context.getApplicationContext();
        TaskStore store = TaskStore.get(appCtx);
        List<TaskEntity> tasks = store.list();
        long now = System.currentTimeMillis();
        int armed = 0;
        for (TaskEntity task : tasks) {
            if (!task.enabled) continue;
            long at = nextFireTime(task, now);
            if (at <= 0) {
                if (!task.isCron()) {
                    task.enabled = false;
                    store.upsert(task);
                    Log.i(TAG, "rescheduleAll: one-shot task '" + task.name
                            + "' is in the past — disabled honestly");
                } else {
                    Log.w(TAG, "rescheduleAll: cron task '" + task.name + "' has no future fire");
                }
                continue;
            }
            AlarmScheduler.schedule(appCtx, AlarmScheduler.ACTION_TASK_FIRE, task.id, at);
            armed++;
        }
        Log.i(TAG, "rescheduleAll: " + armed + " task(s) re-armed at " + new Date(now));
    }
}
