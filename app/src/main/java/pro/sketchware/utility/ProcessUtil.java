package pro.sketchware.utility;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import java.util.List;

public class ProcessUtil {
    public static String getProcessName(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName();
        }
        int pid = android.os.Process.myPid();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            List<ActivityManager.RunningAppProcessInfo> infos = manager.getRunningAppProcesses();
            if (infos != null) {
                for (ActivityManager.RunningAppProcessInfo info : infos) {
                    if (info.pid == pid) {
                        return info.processName;
                    }
                }
            }
        }
        return null;
    }
}
