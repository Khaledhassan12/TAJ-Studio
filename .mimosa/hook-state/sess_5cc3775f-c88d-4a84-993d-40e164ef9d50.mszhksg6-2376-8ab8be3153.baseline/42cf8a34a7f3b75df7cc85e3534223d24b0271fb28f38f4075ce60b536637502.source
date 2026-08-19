package pro.sketchware.upgrades;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.GB;
import a.a.a.jC;
import a.a.a.lC;
import a.a.a.wq;
import com.besome.sketch.beans.ProjectLibraryBean;
import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.utility.FileUtil;

/**
 * Applies safe project upgrades with backup management.
 * It ensures that every destructive change is preceded by a snapshot of the original files.
 * (عربي) يطبق ترقيات المشاريع الآمنة مع إدارة النسخ الاحتياطي.
 * يضمن أن كل تغيير مدمر يسبقه لقطة (Backup) للملفات الأصلية.
 */
public class SafeUpgradeApplier {

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface UpgradeCallback {
        void onUpgradeStarted();
        void onUpgradeFinished(boolean success, String message);
    }

    public SafeUpgradeApplier(Context context) {
        this.context = context.getApplicationContext();
    }

    public void applyUpgrades(UpgradeReport report, UpgradeCallback callback) {
        if (callback != null) callback.onUpgradeStarted();
        executor.execute(() -> {
            try {
                createBackup(report.scId);

                HashMap<String, Object> projectMap = lC.b(report.scId);
                if (projectMap == null) throw new Exception("Failed to load project metadata");

                boolean metadataChanged = false;
                ProjectSettings settings = new ProjectSettings(report.scId);

                for (UpgradeItem item : report.items) {
                    if (item.status != UpgradeItem.Status.UPGRADABLE) continue;

                    switch (item.type) {
                        case GRADLE_CONFIG:
                            if (item.title.equals("Metadata Version")) {
                                projectMap.put("sketchware_ver", GB.d(context));
                                metadataChanged = true;
                            } else if (item.title.equals("Minimum SDK")) {
                                settings.setValue(ProjectSettings.SETTING_MINIMUM_SDK_VERSION, "21");
                            }
                            break;

                        case ANDROIDX:
                            ProjectLibraryBean compat = jC.c(report.scId).c();
                            if (compat == null) {
                                compat = new ProjectLibraryBean(ProjectLibraryBean.PROJECT_LIB_TYPE_COMPAT);
                            }
                            compat.useYn = "Y";
                            jC.c(report.scId).b(compat);
                            jC.c(report.scId).k(); // Save iC settings
                            break;
                    }
                }

                if (metadataChanged) {
                    lC.b(report.scId, projectMap);
                }

                mainHandler.post(() -> {
                    if (callback != null) callback.onUpgradeFinished(true, "Project upgraded successfully");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onUpgradeFinished(false, "Upgrade failed: " + e.getMessage());
                });
            }
        });
    }

    private void createBackup(String scId) {
        String projectPath = wq.c(scId);
        String backupPath = projectPath + File.separator + ".upgrade_backup";
        File backupDir = new File(backupPath);
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        long timestamp = System.currentTimeMillis();
        // Backup 'project' file
        String metadataFile = projectPath + File.separator + "project";
        if (new File(metadataFile).exists()) {
            FileUtil.copyFile(metadataFile, backupPath + File.separator + "project_" + timestamp);
        }

        // Backup 'project_config' (if exists)
        ProjectSettings settings = new ProjectSettings(scId);
        String configPath = settings.getPath();
        if (new File(configPath).exists()) {
            FileUtil.copyFile(configPath, backupPath + File.separator + "project_config_" + timestamp);
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
