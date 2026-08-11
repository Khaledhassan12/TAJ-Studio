package pro.sketchware.ai.automation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * [WHAT] Alarm broadcast entry for Tasks & Loops (P2-AU, D17).
 * [WHY] Manifest-registered, not exported. Extends the broadcast lifetime
 * with goAsync() and routes the fire to TaskManager / LoopRunner on a worker
 * thread — headless runs must not block the main thread.
 */
public class AutomationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        final String action = intent.getAction();
        final String id = intent.getStringExtra(AlarmScheduler.EXTRA_ID);
        if (id == null || id.isEmpty()) return;

        final PendingResult pending = goAsync();
        final Context appCtx = context.getApplicationContext();
        new Thread(() -> {
            try {
                if (AlarmScheduler.ACTION_TASK_FIRE.equals(action)) {
                    TaskManager.onAlarmFire(appCtx, id);
                } else if (AlarmScheduler.ACTION_LOOP_FIRE.equals(action)) {
                    LoopRunner.onAlarmFire(appCtx, id);
                }
            } catch (Exception e) {
                Log.e("AutomationReceiver", "fire handling failed for " + action + " " + id, e);
            } finally {
                pending.finish();
            }
        }, "automation-fire").start();
    }
}
