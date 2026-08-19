package pro.sketchware.activities.main.fragments.projects;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.LruCache;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WHAT: Single background decoder + big static cache + prefetch warm-up.
 * (عربي) مفكّك أيقونات بخيط خلفي واحد + كاش ثابت كبير + تسخين مسبق يلغي تجميد التمرير.
 */
public class AppIconLoader {
    public interface Callback { void onIcon(Bitmap bitmap); }

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(512);
    private static volatile AppIconLoader instance;

    private final Set<String> inFlight = new HashSet<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            r.run();
        }, "app-icon-loader");
        return t;
    });
    private final Handler main = new Handler(Looper.getMainLooper());

    public static AppIconLoader get() {
        if (instance == null) {
            synchronized (AppIconLoader.class) { if (instance == null) instance = new AppIconLoader(); }
        }
        return instance;
    }

    public Bitmap cached(String pkg) { return CACHE.get(pkg); }

    public void load(Context context, String pkg, int sizePx, Callback cb) {
        Bitmap hit = CACHE.get(pkg);
        if (hit != null) { cb.onIcon(hit); return; }
        final Context app = context.getApplicationContext();
        worker.execute(() -> {
            boolean shouldDecode;
            synchronized (inFlight) { shouldDecode = !inFlight.contains(pkg); inFlight.add(pkg); }
            Bitmap b = CACHE.get(pkg);
            if (b == null && shouldDecode) b = decode(app, pkg, sizePx);
            if (b != null) CACHE.put(pkg, b);
            synchronized (inFlight) { inFlight.remove(pkg); }
            final Bitmap out = b;
            main.post(() -> cb.onIcon(out));
        });
    }

    /** WHAT: prefetch - warm the cache for the whole list off-main (no UI posts).
     *  (عربي) تسخين الكاش لكل القائمة في الخلفية دون لمس الواجهة. */
    public void prefetch(Context context, List<String> pkgs, int sizePx) {
        final Context app = context.getApplicationContext();
        worker.execute(() -> {
            for (String pkg : pkgs) {
                if (CACHE.get(pkg) != null) continue;
                Bitmap b = decode(app, pkg, sizePx);
                if (b != null) CACHE.put(pkg, b);
            }
        });
    }

    /** Synchronous high-res fetch for apply. Runs on the apply executor, never on main. */
    public Bitmap fetchHighRes(Context context, String pkg, int sizePx) {
        return decode(context.getApplicationContext(), pkg, sizePx);
    }

    private Bitmap decode(Context app, String pkg, int sizePx) {
        try {
            Drawable d = app.getPackageManager().getApplicationIcon(pkg);
            return rasterize(d, sizePx);
        } catch (Exception e) { return null; }
    }

    private Bitmap rasterize(Drawable d, int sizePx) {
        if (d instanceof BitmapDrawable) {
            Bitmap src = ((BitmapDrawable) d).getBitmap();
            if (src != null) {
                if (src.getWidth() <= sizePx && src.getHeight() <= sizePx) return src;
                return Bitmap.createScaledBitmap(src, sizePx, sizePx, true);
            }
        }
        Bitmap b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, sizePx, sizePx);
        d.draw(c);
        return b;
    }
}
