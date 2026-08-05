package pro.sketchware.upgrades.scan;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.upgrades.model.Finding;

/**
 * WHAT: BuildArtifactCleaner - Detects temporary build folders that can be safely cleaned.
 * (عربي) منظف مخرجات البناء - يكتشف مجلدات البناء المؤقتة التي يمكن تنظيفها بأمان.
 */
public class BuildArtifactCleaner {

    public List<Finding> scan(String buildCachePath) {
        List<Finding> findings = new ArrayList<>();
        File buildCacheDir = new File(buildCachePath);
        
        // G5: check if build cache exists and is not empty
        if (buildCacheDir.exists() && buildCacheDir.isDirectory()) {
            File[] files = buildCacheDir.listFiles();
            if (files != null && files.length > 0) {
                findings.add(new Finding(
                    "regenerable_cache",
                    Finding.Category.BUILD_ARTIFACT,
                    "Regenerable build cache",
                    "Temporary files in " + buildCacheDir.getName() + " that can be safely deleted.",
                    List.of(buildCacheDir.getAbsolutePath()),
                    true,
                    "Clean temporary build files"
                ));
            }
        }
        return findings;
    }
}
