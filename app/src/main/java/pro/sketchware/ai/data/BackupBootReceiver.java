package pro.sketchware.ai.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * [WHAT] Reschedules auto-backups on system boot.
 * [WHY] Ensures continuity of automated data control.
 */
public class BackupBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i("BackupBootReceiver", "System boot detected, rescheduling backups.");
            TajBackupManager.get(context).rescheduleAutoBackup();
        }
    }
}
