package pro.sketchware.marketplace.utils;

import android.os.Environment;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import dev.aldi.sayuti.editor.manage.LocalLibrary;
import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import pro.sketchware.marketplace.models.MarketplaceLibrary;

/**
 * مساعد المتجر - مطابقة ذكية للمجلدات وقناة إشعارات محدثة.
 * Marketplace Helper - Smart folder matching and fresh notification channel.
 *
 * WHAT: Name-shape agnostic installation matching.
 * HOW: Using substring matching with boundaries (. - _ v) instead of fixed folder names in cache and disk checks.
 * WHY: Marketplace folder names vary by resolver; exact matching fails on non-standard naming conventions.
 */
public class MarketplaceHelper {

    private static final String LOCAL_LIBS_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/.sketchware/libs/local_libs/";
    private static Set<String> installedCache = null;

    /**
     * فحص متزامن وخفيف للتثبيت عبر الكاش أو قائمة المجلدات بأسلوب name-shape agnostic.
     * Synchronous and name-shape agnostic installation check via cache.
     */
    public static synchronized boolean isInstalledSync(MarketplaceLibrary library) {
        if (installedCache == null) {
            refreshCache();
        }
        
        String artifact = artifactOf(library.getCoordinate());
        String id = library.getId();
        
        // WHAT: Use name-shape agnostic matching to find the artifact in the cache
        // HOW: Iterating over cache and checking for substring patterns with boundaries
        return nameShapeAgnosticMatcher(artifact) || nameShapeAgnosticMatcher(id);
    }

    /**
     * مطابقة ذكية تتجاهل شكل اسم المجلد (إصدار، بادئة، إلخ).
     * Smart matcher that ignores folder name shape (version, prefix, etc).
     */
    private static boolean nameShapeAgnosticMatcher(String artifact) {
        if (artifact == null || artifact.isEmpty()) return false;
        if (installedCache == null) refreshCache();
        
        String a = artifact.toLowerCase();
        for (String folder : installedCache) {
            // HOW: Robust containment - folder equals artifact or starts with it followed by separator (- _ . v)
            if (folder.equals(a)) return true;
            if (folder.startsWith(a + "-") || folder.startsWith(a + "_")
                    || folder.startsWith(a + ".") || folder.startsWith(a + "v")) return true;
            
            // Also if name contains artifact surrounded by boundaries (e.g. com.x.glide_4)
            if (folder.contains("." + a + "-") || folder.contains("." + a + "_")) return true;
        }
        return false;
    }

    /**
     * يتحقق من وجود المجلد الفعلي على القرص حالياً بالبحث في القائمة الحقيقية.
     * Checks if the actual folder exists on disk by searching the real list.
     */
    public static boolean isActuallyOnDisk(MarketplaceLibrary library) {
        String artifact = artifactOf(library.getCoordinate());
        String id = library.getId();
        
        File root = new File(LOCAL_LIBS_PATH);
        if (!root.exists() || !root.isDirectory()) return false;
        
        File[] kids = root.listFiles();
        if (kids != null) {
            for (File f : kids) {
                if (f.isDirectory()) {
                    String name = f.getName().toLowerCase();
                    if (matchesArtifact(name, artifact, id)) return true;
                }
            }
        }
        return false;
    }

    /**
     * دالة مساعدة للمطابقة المباشرة على أسماء المجلدات.
     */
    private static boolean matchesArtifact(String folderName, String artifact, String id) {
        String f = folderName.toLowerCase();
        String a = artifact.toLowerCase();
        String i = id != null ? id.toLowerCase() : "";
        
        if (f.equals(a) || (!i.isEmpty() && f.equals(i))) return true;
        
        // Patterns for artifact
        if (f.startsWith(a + "-") || f.startsWith(a + "_") || f.startsWith(a + ".") || f.startsWith(a + "v")) return true;
        if (f.contains("." + a + "-") || f.contains("." + a + "_")) return true;
        
        // Patterns for ID
        if (!i.isEmpty()) {
            if (f.startsWith(i + "-") || f.startsWith(i + "_") || f.startsWith(i + ".") || f.startsWith(i + "v")) return true;
        }
        
        return false;
    }

    public static synchronized void refreshCache() {
        installedCache = new HashSet<>();
        File root = new File(LOCAL_LIBS_PATH);
        if (root.exists() && root.isDirectory()) {
            File[] files = root.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        installedCache.add(f.getName().toLowerCase());
                    }
                }
            }
        }
    }

    private static String artifactOf(String coordinate) {
        if (coordinate == null || !coordinate.contains(":")) return coordinate != null ? coordinate.toLowerCase() : "";
        String[] p = coordinate.split(":");
        return p.length >= 2 ? p[1].toLowerCase() : coordinate.toLowerCase();
    }
}
