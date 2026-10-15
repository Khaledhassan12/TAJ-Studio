package pro.sketchware.upgrades;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.GB;
import a.a.a.iC;
import a.a.a.jC;
import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;
import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.R;
import pro.sketchware.upgrades.scan.ProjectDiagnosticsScanner;

/**
 * Background scanner that analyzes projects for potential upgrades.
 * It deeply inspects project metadata and configurations to determine health scores.
 * (عربي) فاحص خلفي يقوم بتحليل المشاريع بحثاً عن ترقيات محتملة.
 * يقوم بفحص عميق لميتاداتا وإعدادات المشروع لتحديد درجات الصحة (Health Scores).
 */
public class ProjectUpgradeScanner {

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ScanCallback {
        void onScanStarted();
        void onScanFinished(List<UpgradeReport> reports);
    }

    public ProjectUpgradeScanner(Context context) {
        this.context = context.getApplicationContext();
    }

    public void scan(ScanCallback callback) {
        if (callback != null) callback.onScanStarted();
        executor.execute(() -> {
            List<UpgradeReport> reports = new ArrayList<>();
            ArrayList<HashMap<String, Object>> projects;
            try {
                projects = lC.a();
            } catch (Exception projectListError) {
                reports.add(createScanFailureReport(projectListError));
                mainHandler.post(() -> {
                    if (callback != null) callback.onScanFinished(reports);
                });
                return;
            }

            for (HashMap<String, Object> project : projects) {
                if (Thread.currentThread().isInterrupted()) break;
                String scId = null;
                String appName = "Unknown project";
                String pkgName = "";
                try {
                    scId = yB.c(project, "sc_id");
                    appName = yB.c(project, "my_app_name");
                    pkgName = yB.c(project, "my_sc_pkg_name");
                    if (appName == null || appName.trim().isEmpty()) appName = "Unnamed project";
                    if (pkgName == null) pkgName = "";
                    if (scId == null || scId.trim().isEmpty()) {
                        throw new IllegalArgumentException("Missing project ID");
                    }
                
                File projectFolder = new File(wq.c(scId));
                long lastMod = projectFolder.lastModified();
                String age = calculateAge(lastMod);
                boolean hasCustomIcon = yB.a(project, "custom_icon");

                UpgradeReport report = new UpgradeReport(scId, appName, pkgName, age, lastMod, hasCustomIcon);

                // 1. Check Sketchware Version
                int currentVer = yB.b(project, "sketchware_ver");
                int latestVer = GB.d(context);
                report.storedVer = currentVer;
                report.latestVer = latestVer;
                
                if (currentVer < latestVer) {
                    report.items.add(new UpgradeItem(
                            UpgradeItem.Type.GRADLE_CONFIG,
                            UpgradeItem.Status.UPGRADABLE,
                            "Metadata Version",
                            "Update project metadata version from " + currentVer + " to " + latestVer
                    ));
                } else {
                    report.items.add(new UpgradeItem(
                            UpgradeItem.Type.GRADLE_CONFIG,
                            UpgradeItem.Status.UP_TO_DATE,
                            "Metadata Version",
                            "Already using latest metadata version (" + currentVer + ")"
                    ));
                }

                // 2. Check AndroidX Status
                boolean isAndroidX = jC.c(scId).c().useYn.equals("Y");
                report.androidxOn = isAndroidX;
                
                if (!isAndroidX) {
                    report.items.add(new UpgradeItem(
                            UpgradeItem.Type.ANDROIDX,
                            UpgradeItem.Status.UPGRADABLE,
                            "AndroidX Support",
                            "Enable AndroidX and Appcompat libraries for modern components support"
                    ));
                } else {
                    report.items.add(new UpgradeItem(
                            UpgradeItem.Type.ANDROIDX,
                            UpgradeItem.Status.UP_TO_DATE,
                            "AndroidX Support",
                            "AndroidX is already enabled"
                    ));
                }

                // 3. Check Min SDK
                ProjectSettings settings = new ProjectSettings(scId);
                int minSdk = settings.getMinSdkVersion();
                report.minSdk = minSdk;
                
                if (minSdk < 21) {
                    report.items.add(new UpgradeItem(
                            UpgradeItem.Type.GRADLE_CONFIG,
                            UpgradeItem.Status.UPGRADABLE,
                            "Minimum SDK",
                            "Upgrade minSdk from " + minSdk + " to 21 for better compatibility"
                    ));
                }

                // 4. Libraries count
                iC libConfig = jC.c(scId);
                int libsCount = 0;
                if (libConfig.c != null && "Y".equals(libConfig.c.useYn)) libsCount++;
                if (libConfig.d != null && "Y".equals(libConfig.d.useYn)) libsCount++;
                if (libConfig.e != null && "Y".equals(libConfig.e.useYn)) libsCount++;
                if (libConfig.f != null && "Y".equals(libConfig.f.useYn)) libsCount++;
                report.libsCount = libsCount;

                // Run bounded read-only diagnostics for every discovered project, not only
                // after the user opens a project's detailed report.
                try {
                    String diagnosticPath = wq.b(scId);
                    File diagnosticRoot = diagnosticPath == null ? null : new File(diagnosticPath);
                    if (diagnosticRoot != null && diagnosticRoot.isDirectory()) {
                        List<pro.sketchware.upgrades.model.Finding> diagnostics =
                                new ProjectDiagnosticsScanner().scan(diagnosticPath);
                        if (!diagnostics.isEmpty()) {
                            report.diagnosticCount = diagnostics.size();
                            StringBuilder summary = new StringBuilder();
                            int shown = Math.min(3, diagnostics.size());
                            for (int i = 0; i < shown; i++) {
                                if (i > 0) summary.append(", ");
                                summary.append(diagnostics.get(i).title);
                            }
                            if (diagnostics.size() > shown) summary.append(", and more");
                            report.items.add(new UpgradeItem(
                                    UpgradeItem.Type.PROJECT_HEALTH,
                                    UpgradeItem.Status.RECOMMENDED_MANUAL,
                                    "Project Doctor: " + diagnostics.size() + " finding(s)",
                                    summary.toString() + ". Open the project report for details; no files were changed."));
                        }
                    }
                } catch (Exception diagnosticError) {
                    report.items.add(new UpgradeItem(
                            UpgradeItem.Type.PROJECT_HEALTH,
                            UpgradeItem.Status.RECOMMENDED_MANUAL,
                            "Project Doctor could not finish",
                            "Some diagnostics could not be collected (" +
                                    diagnosticError.getClass().getSimpleName() +
                                    "). Open the project report and review the project path."));
                }

                reports.add(report);
                } catch (Exception projectError) {
                    String safeId = scId == null || scId.trim().isEmpty()
                            ? "unknown-" + Integer.toHexString(project == null ? 0 : project.hashCode())
                            : scId;
                    UpgradeReport failedReport = new UpgradeReport(safeId, appName, pkgName, "unknown", 0L, false);
                    failedReport.items.add(new UpgradeItem(
                            UpgradeItem.Type.PROJECT_HEALTH,
                            UpgradeItem.Status.RECOMMENDED_MANUAL,
                            "Project scan could not finish",
                            "This project was kept in the results, but one or more checks failed (" +
                                    projectError.getClass().getSimpleName() +
                                    "). Review its metadata and open the detailed report."));
                    reports.add(failedReport);
                }
            }

            mainHandler.post(() -> {
                if (callback != null) callback.onScanFinished(reports);
            });
        });
    }

    private UpgradeReport createScanFailureReport(Exception error) {
        UpgradeReport report = new UpgradeReport("project-list-error", "Project list unavailable",
                "", "unknown", 0L, false);
        report.items.add(new UpgradeItem(
                UpgradeItem.Type.PROJECT_HEALTH,
                UpgradeItem.Status.RECOMMENDED_MANUAL,
                "Could not enumerate projects",
                "Sketchware's project index could not be read (" + error.getClass().getSimpleName() +
                        "). No project files were changed."));
        return report;
    }

    private String calculateAge(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long days = diff / (1000 * 60 * 60 * 24);
        if (days < 7) return context.getString(R.string.age_days, (int) days);
        if (days < 30) return context.getString(R.string.age_weeks, (int) (days / 7));
        if (days < 365) return context.getString(R.string.age_months, (int) (days / 30));
        return context.getString(R.string.age_years, (int) (days / 365));
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
