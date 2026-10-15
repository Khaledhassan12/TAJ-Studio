package pro.sketchware.upgrades;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
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
import pro.sketchware.upgrades.scan.ProjectDiagnosticsScanner;
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
        if (callback != null) mainHandler.post(callback::onScanStarted);
        executor.execute(() -> {
            long startTime = System.currentTimeMillis();
            String projectPath = wq.b(scId);
            String buildCachePath = wq.d(scId);
            
            // Real path discovery
            List<String> javaDirs = new ArrayList<>();
            List<String> resDirs = new ArrayList<>();
            List<String> assetsDirs = new ArrayList<>();
            
            discoverPaths(new File(projectPath), javaDirs, resDirs, assetsDirs, new HashSet<>(), new int[]{0}, 0);

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

            // 6. Conservative XML/SVG, lifecycle, debug-marker and Gradle diagnostics.
            // Findings are advisory only and are never auto-fixed.
            allFindings.addAll(new ProjectDiagnosticsScanner().scan(projectPath));
            
            DoctorReport report = new DoctorReport(scId, allFindings, System.currentTimeMillis() - startTime);

            mainHandler.post(() -> {
                if (callback != null) callback.onScanFinished(report);
            });
        });
    }

    private void discoverPaths(File dir, List<String> javaDirs, List<String> resDirs,
                               List<String> assetsDirs, Set<String> visited, int[] visitedDirs, int depth) {
        if (dir == null || depth > 32 || visitedDirs[0] >= 5000) return;
        String canonical;
        try {
            canonical = dir.getCanonicalPath();
            if (!visited.add(canonical)) return;
        } catch (Exception ignored) {
            return;
        }
        visitedDirs[0]++;
        String dirName = dir.getName().toLowerCase(java.util.Locale.ROOT);
        if (dirName.equals(".upgrade_backup") || dirName.equals(".git") ||
                dirName.equals(".gradle") || dirName.equals("build") ||
                dirName.equals("outputs") || dirName.equals("generated") ||
                dirName.equals("node_modules") || dirName.equals(".cxx") ||
                dirName.equals("captures") || dirName.equals("builds")) return;

        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (visitedDirs[0] >= 5000) break;
            if (!file.isDirectory()) continue;
            String name = file.getName();
            if (name.equals("java")) {
                if (!javaDirs.contains(file.getAbsolutePath())) javaDirs.add(file.getAbsolutePath());
            } else if (name.equals("assets")) {
                if (!assetsDirs.contains(file.getAbsolutePath())) assetsDirs.add(file.getAbsolutePath());
            } else if (isResourceFolder(name)) {
                File parent = file.getParentFile();
                if (parent != null && !resDirs.contains(parent.getAbsolutePath())) {
                    resDirs.add(parent.getAbsolutePath());
                }
            }
            discoverPaths(file, javaDirs, resDirs, assetsDirs, visited, visitedDirs, depth + 1);
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
