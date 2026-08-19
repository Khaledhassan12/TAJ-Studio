package pro.sketchware.activities.main.fragments.projects;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WHAT: Queries installed-app list ONCE on a single background thread, cached statically.
 * (عربي) يجلب قائمة التطبيقات مرة واحدة على خيط خلفي واحد ويخزنها ثابتاً.
 */
public class InstalledAppsRepository {
    public static class App {
        public final String label;
        public final String packageName;
        App(String l, String p) { label = l; packageName = p; }
    }
    public interface Callback { void onApps(List<App> apps); }

    private static List<App> CACHED;
    private static final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            r.run();
        }, "apps-repo");
        return t;
    });
    private static final Handler main = new Handler(Looper.getMainLooper());

    public static List<App> cached() { return CACHED; }

    public static void load(Context context, Callback cb) {
        if (CACHED != null && !CACHED.isEmpty()) { cb.onApps(CACHED); return; }
        final Context app = context.getApplicationContext();
        worker.execute(() -> {
            List<App> apps = query(app);
            CACHED = apps;
            main.post(() -> cb.onApps(apps));
        });
    }

    private static List<App> query(Context app) {
        PackageManager pm = app.getPackageManager();
        List<App> out = new ArrayList<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if (pm.getLaunchIntentForPackage(ai.packageName) != null) {
                out.add(new App(pm.getApplicationLabel(ai).toString(), ai.packageName));
            }
        }
        out.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }
}
