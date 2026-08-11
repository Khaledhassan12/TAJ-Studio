package pro.sketchware.ai.automation;

import android.content.Context;
import android.util.Log;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * [WHAT] Start/stop + fire handling for conversation Loops (P2-AU, D17).
 * [WHY] A running loop re-sends its prompt to its conversation every
 * intervalMinutes. A concurrent-run guard prevents overlapping executions;
 * persistence survives app kills via the SketchApplication reschedule hook.
 */
public class LoopRunner {

    public static final String TAG = "LoopRunner";

    /** Concurrent-run guard: ids with a headless run currently in flight. */
    private static final Set<String> inFlight = Collections.synchronizedSet(new HashSet<>());

    /** Starts (or restarts) a loop: persist running=true + schedule first fire. */
    public static void start(Context context, LoopEntity loop) {
        Context appCtx = context.getApplicationContext();
        loop.running = true;
        LoopStore.get(appCtx).upsert(loop);
        long at = System.currentTimeMillis() + loop.intervalMinutes * 60_000L;
        AlarmScheduler.schedule(appCtx, AlarmScheduler.ACTION_LOOP_FIRE, loop.id, at);
        Log.i(TAG, "start: loop '" + loop.name + "' every " + loop.intervalMinutes + " min");
    }

    /** Stops a loop: persist running=false + cancel the pending alarm. */
    public static void stop(Context context, LoopEntity loop) {
        Context appCtx = context.getApplicationContext();
        loop.running = false;
        LoopStore.get(appCtx).upsert(loop);
        AlarmScheduler.cancel(appCtx, AlarmScheduler.ACTION_LOOP_FIRE, loop.id);
        Log.i(TAG, "stop: loop '" + loop.name + "'");
    }

    /** Alarm fire entry point (called from AutomationReceiver off-main). */
    public static void onAlarmFire(Context context, String loopId) {
        Context appCtx = context.getApplicationContext();
        LoopStore store = LoopStore.get(appCtx);
        LoopEntity loop = store.findById(loopId);
        if (loop == null || !loop.running) {
            Log.i(TAG, "fire: loop missing or stopped (id=" + loopId + ") — skipped honestly");
            return;
        }
        if (!inFlight.add(loopId)) {
            Log.w(TAG, "fire: loop '" + loop.name + "' still running — skipping this fire (concurrent-run guard)");
            reschedule(appCtx, loop);
            return;
        }
        Log.i(TAG, "fire: loop '" + loop.name + "' executing headless in conversation " + loop.conversationId);
        HeadlessRunner.run(appCtx, loop.scId, loop.conversationId, loop.prompt, null,
                (ok, summary) -> {
                    inFlight.remove(loopId);
                    Log.i(TAG, "execution summary: loop '" + loop.name + "' ok=" + ok + ", " + summary);
                    LoopEntity fresh = store.findById(loopId);
                    if (fresh != null && fresh.running) {
                        reschedule(appCtx, fresh);
                    } else {
                        Log.i(TAG, "loop '" + loop.name + "' was stopped during run — not rescheduled");
                    }
                });
    }

    private static void reschedule(Context appCtx, LoopEntity loop) {
        long at = System.currentTimeMillis() + loop.intervalMinutes * 60_000L;
        AlarmScheduler.schedule(appCtx, AlarmScheduler.ACTION_LOOP_FIRE, loop.id, at);
    }

    /** App-start hook (SketchApplication): re-arm every RUNNING loop. */
    public static void rescheduleAll(Context context) {
        Context appCtx = context.getApplicationContext();
        List<LoopEntity> loops = LoopStore.get(appCtx).list();
        long now = System.currentTimeMillis();
        int armed = 0;
        for (LoopEntity loop : loops) {
            if (!loop.running) continue;
            AlarmScheduler.schedule(appCtx, AlarmScheduler.ACTION_LOOP_FIRE, loop.id,
                    now + loop.intervalMinutes * 60_000L);
            armed++;
        }
        Log.i(TAG, "rescheduleAll: " + armed + " loop(s) re-armed at " + new Date(now));
    }
}
