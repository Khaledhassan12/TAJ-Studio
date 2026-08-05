package pro.sketchware.upgrades;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.wq;
import pro.sketchware.upgrades.model.Finding;
import pro.sketchware.utility.FileUtil;

/**
 * WHAT: SafeFixApplier - Applies fixes for findings with mandatory backups.
 * WHY: Modification of project files is risky; creating a snapshot in .upgrade_backup ensures users can always recover.
 * (عربي) مطبق الإصلاحات الآمنة - يطبق إصلاحات الاكتشافات مع نسخ احتياطي إلزامي.
 * تعديل ملفات المشروع محفوف بالمخاطر؛ إنشاء لقطة في .upgrade_backup يضمن للمستخدمين إمكانية الاستعادة دائماً.
 */
public class SafeFixApplier {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface FixCallback {
        void onFixStarted();
        void onFixFinished(boolean success, List<String> appliedFixes);
    }

    public void applyFixes(String scId, List<Finding> selectedFindings, FixCallback callback) {
        if (callback != null) callback.onFixStarted();
        executor.execute(() -> {
            List<String> appliedFixes = new ArrayList<>();
            try {
                String timestamp = String.valueOf(System.currentTimeMillis());
                // G1: Data Root is wq.b(scId)
                String dataRoot = wq.b(scId);
                String backupRoot = dataRoot + "/.upgrade_backup/" + timestamp;

                for (Finding f : selectedFindings) {
                    if (!f.autoFixable) continue;

                    // Mandatory Backup
                    for (String path : f.paths) {
                        File original = new File(path);
                        if (original.exists()) {
                            String relativePath = path.replace(dataRoot, "");
                            File backupFile = new File(backupRoot, relativePath);
                            // G6: Ensure backup directory exists
                            if (backupFile.getParentFile() != null) {
                                backupFile.getParentFile().mkdirs();
                            }
                            FileUtil.copyFile(path, backupFile.getAbsolutePath());
                        }
                    }

                    // Execution
                    if (applyFindingFix(f, dataRoot)) {
                        appliedFixes.add(f.fixDescription + " (" + f.title + ")");
                    }
                }

                mainHandler.post(() -> {
                    if (callback != null) callback.onFixFinished(true, appliedFixes);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onFixFinished(false, new ArrayList<>());
                });
            }
        });
    }

    private boolean applyFindingFix(Finding f, String dataRoot) {
        switch (f.category) {
            case UNUSED_RESOURCE:
            case DUPLICATE_FILE:
            case EMPTY_ARTIFACT:
                for (String path : f.paths) {
                    FileUtil.deleteFile(path);
                }
                return true;
            
            case BUILD_ARTIFACT:
                for (String path : f.paths) {
                    // G6: Use FileUtil.deleteFile for directories too
                    FileUtil.deleteFile(path);
                }
                return true;

            case UNUSED_IMPORT:
                return applyImportCleanup(f);

            default:
                return false;
        }
    }

    private boolean applyImportCleanup(Finding f) {
        // G6: Implementation for UNUSED_IMPORT
        for (String path : f.paths) {
            File file = new File(path);
            if (!file.exists()) continue;

            String content = FileUtil.readFile(path);
            String[] lines = content.split("\n");
            
            // Extract unused imports from finding detail if possible, 
            // but ImportCleaner's checkFile is simple: Count == 1.
            // We'll re-run the check here for precision.
            StringBuilder newContent = new StringBuilder();
            Pattern importPattern = Pattern.compile("import\\s+([a-zA-Z0-9_.]+);");
            
            for (String line : lines) {
                Matcher m = importPattern.matcher(line.trim());
                if (m.find()) {
                    String fullImport = m.group(1);
                    if (fullImport.endsWith("*")) {
                        newContent.append(line).append("\n");
                        continue;
                    }
                    String simpleName = fullImport.substring(fullImport.lastIndexOf('.') + 1);
                    Pattern usagePattern = Pattern.compile("\\b" + simpleName + "\\b");
                    Matcher usageMatcher = usagePattern.matcher(content);
                    int count = 0;
                    while (usageMatcher.find()) count++;
                    
                    if (count > 1) {
                        newContent.append(line).append("\n");
                    }
                } else {
                    newContent.append(line).append("\n");
                }
            }
            FileUtil.writeFile(path, newContent.toString().trim());
        }
        return true;
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
