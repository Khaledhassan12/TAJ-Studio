package pro.sketchware.ai.automation;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Date;

/**
 * [WHAT] Alarm scheduling helper for Tasks & Loops (P2-AU, D17).
 * [WHY] The exactness mode is re-read from AutomationSettings at EVERY
 * schedule call: exact => setExactAndAllowWhileIdle, else the battery-friendly
 * setAndAllowWhileIdle (inexact). On SDK>=S an exact request without the
 * system "Alarms & reminders" grant falls back to inexact honestly (logged).
 * [HOW] PendingIntents broadcast to AutomationReceiver with a stable
 * requestCode derived from (action + id), so cancel/reschedule replace the
 * same slot. Logcat tag "AlarmScheduler" is the T4 proof line.
 */
public class AlarmScheduler {

    public static final String TAG = "AlarmScheduler";

    public static final String ACTION_TASK_FIRE = "pro.sketchware.ai.automation.TASK_FIRE";
    public static final String ACTION_LOOP_FIRE = "pro.sketchware.ai.automation.LOOP_FIRE";
    public static final String EXTRA_ID = "automation_id";

    /** Schedules one fire; exactness chosen from SSOT at THIS call (R16). */
    public static void schedule(Context context, String action, String id, long atMillis) {
        Context appCtx = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) appCtx.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w(TAG, "schedule: AlarmManager unavailable, cannot schedule " + action + " " + id);
            return;
        }

        boolean useExact = AutomationSettings.get(appCtx).isExactAlarms();
        if (useExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "schedule: exact alarms requested but system permission NOT granted —"
                    + " honest fallback to INEXACT for " + action + " id=" + id);
            useExact = false;
        }

        PendingIntent pi = buildPendingIntent(appCtx, action, id, false);
        if (useExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi);
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi);
        }
        Log.i(TAG, "schedule: " + action + " id=" + id + " at=" + new Date(atMillis)
                + " mode=" + (useExact ? "EXACT" : "INEXACT"));
    }

    /** Cancels a pending fire (if any) for the given action + id. */
    public static void cancel(Context context, String action, String id) {
        Context appCtx = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) appCtx.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        PendingIntent pi = buildPendingIntent(appCtx, action, id, true);
        if (pi != null) {
            alarmManager.cancel(pi);
            pi.cancel();
        }
        Log.i(TAG, "cancel: " + action + " id=" + id);
    }

    private static PendingIntent buildPendingIntent(Context appCtx, String action, String id, boolean noCreate) {
        Intent intent = new Intent(appCtx, AutomationReceiver.class)
                .setAction(action)
                .putExtra(EXTRA_ID, id);
        int flags = PendingIntent.FLAG_IMMUTABLE
                | (noCreate ? PendingIntent.FLAG_NO_CREATE : PendingIntent.FLAG_UPDATE_CURRENT);
        return PendingIntent.getBroadcast(appCtx, requestCode(action, id), intent, flags);
    }

    /** Stable per-(action,id) code so reschedules replace the same alarm slot. */
    private static int requestCode(String action, String id) {
        return (action + ":" + id).hashCode();
    }
}
