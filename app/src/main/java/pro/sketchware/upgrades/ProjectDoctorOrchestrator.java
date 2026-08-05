package pro.sketchware.upgrades;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.wq;
import pro.sketchware.upgrades.model.DoctorReport;
import pro.sketchware.upgrades.model.Finding;
import pro.sketchware.upgrades.scan.BuildArtifactCleaner;
import pro.sketchware.upgrades.scan.DuplicateHashFinder;
import pro.sketchware.upgrades.scan.EmptyArtifactDetector;
import pro.sketchware.upgrades.scan.ImportCleaner;
import pro.sketchware.upgrades.scan.LibraryHealthDetector;
import pro.sketchware.upgrades.scan.ReferenceIndexBuilder;
import pro.sketchware.upgrades.scan.UnusedResourceDetector;
import pro.sketchware.upgrades.scan.UnusedValuesDetector;

/**
 * WHAT: ProjectDoctorOrchestrator - Manages the execution of various project "health" detectors.
 * (عربي) منسق طبيب المشروع - يدير تنفيذ مختلف مكتشفات "صحة" المشروع.
 */
public class ProjectDoctorOrchestrator {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface DoctorCallback {
        void onScanStarted();
        void onScanFinished(DoctorReport report);
    }

    public ProjectDoctorOrchestrator(Context context) {
        // Context preserved for future logic
    }

    public void runHealthCheck(String scId, DoctorCallback callback) {
        if (callback != null) callback.onScanStarted();
        executor.execute(() -> {
            long startTime = System.currentTimeMillis();
            String projectPath = wq.b(scId);
            String buildCachePath = wq.d(scId);
            
            // Real path discovery
            List<String> javaDirs = new ArrayList<>();
            List<String> resDirs = new ArrayList<>();
            List<String> assetsDirs = new ArrayList<>();
            
            discoverPaths(new File(projectPath), javaDirs, resDirs, assetsDirs);

            ReferenceIndexBuilder index = new ReferenceIndexBuilder();
            index.buildIndex(projectPath, javaDirs, resDirs);

            List<Finding> allFindings = new ArrayList<>();

            // 1. Resources & Values
            UnusedResourceDetector resDetector = new UnusedResourceDetector();
            UnusedValuesDetector valDetector = new UnusedValuesDetector();
            for (String resPath : resDirs) {
                allFindings.addAll(resDetector.scan(resPath, index));
                allFindings.addAll(valDetector.scan(resPath, index));
            }

            // 2. Duplicates
            allFindings.addAll(new DuplicateHashFinder().scan(resDirs, assetsDirs));

            // 3. Artifacts & Cleaning
            allFindings.addAll(new EmptyArtifactDetector().scan(projectPath));
            allFindings.addAll(new BuildArtifactCleaner().scan(buildCachePath));

            // 4. Code logic
            ImportCleaner importCleaner = new ImportCleaner();
            for (String javaPath : javaDirs) {
                allFindings.addAll(importCleaner.scan(javaPath));
            }

            // 5. Libraries
            allFindings.addAll(new LibraryHealthDetector().scan(scId));
            
            DoctorReport report = new DoctorReport(scId, allFindings, System.currentTimeMillis() - startTime);

            mainHandler.post(() -> {
                if (callback != null) callback.onScanFinished(report);
            });
        });
    }

    private void discoverPaths(File dir, List<String> javaDirs, List<String> resDirs, List<String> assetsDirs) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String name = file.getName();
            // G6: Skip backups and mysc (though mysc is usually outside data root, check just in case)
            if (name.equals(".upgrade_backup") || name.contains("mysc")) continue;

            if (file.isDirectory()) {
                if (name.equals("java")) {
                    javaDirs.add(file.getAbsolutePath());
                } else if (name.equals("assets")) {
                    assetsDirs.add(file.getAbsolutePath());
                } else if (isResourceFolder(name)) {
                    // If it's a direct resource folder (like drawable-xhdpi), its parent might be the res root
                    // But usually in Sketchware, 'resource' or 'res' folder contains these.
                    // The rule G1 says: collect resDirs if dir name matches drawable*/mipmap*/etc.
                    // This implies the PARENT of these folders is what detectors expect as resPath.
                    File parent = file.getParentFile();
                    if (parent != null && !resDirs.contains(parent.getAbsolutePath())) {
                        resDirs.add(parent.getAbsolutePath());
                    }
                }
                discoverPaths(file, javaDirs, resDirs, assetsDirs);
            }
        }
    }

    private boolean isResourceFolder(String name) {
        return name.startsWith("drawable") || name.startsWith("mipmap") || 
               name.equals("raw") || name.equals("anim") || 
               name.equals("animator") || name.equals("menu") || 
               name.equals("font") || name.equals("values") || 
               name.equals("layout") || name.equals("xml");
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
