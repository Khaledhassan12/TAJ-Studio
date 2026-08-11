package pro.sketchware;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;

import com.besome.sketch.tools.CollectErrorActivity;

import pro.sketchware.utility.theme.ThemeManager;

public class SketchApplication extends Application {
    private static Context mApplicationContext;

    public static Context getContext() {
        return mApplicationContext;
    }

    @Override
    public void onCreate() {
        mApplicationContext = getApplicationContext();
        String processName = pro.sketchware.utility.ProcessUtil.getProcessName(this);
        if (processName != null && processName.endsWith(":ai_runtime")) {
            Log.d("SketchApplication", "Isolated process :ai_runtime detected. Skipping heavy init.");
            super.onCreate();
            return;
        }

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {
                Intent intent = new Intent(getApplicationContext(), CollectErrorActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.putExtra("error", Log.getStackTraceString(throwable));
                startActivity(intent);
                Process.killProcess(Process.myPid());
                System.exit(1);
            }
        });
        super.onCreate();
        ThemeManager.applyTheme(this, ThemeManager.getCurrentTheme(this));

        // P2-AU (D17): re-arm persisted Tasks & Loops after app kill/restart.
        // Never let automation rescheduling crash app startup.
        try {
            pro.sketchware.ai.automation.TaskManager.rescheduleAll(this);
            pro.sketchware.ai.automation.LoopRunner.rescheduleAll(this);
            pro.sketchware.ai.data.TajBackupManager.get(this).rescheduleAutoBackup();
        } catch (Exception e) {
            Log.e("SketchApplication", "automation/backup reschedule failed at app start", e);
        }
    }
}

