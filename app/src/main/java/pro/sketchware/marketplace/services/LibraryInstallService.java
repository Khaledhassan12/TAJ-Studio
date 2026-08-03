package pro.sketchware.marketplace.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.cosmic.ide.dependency.resolver.api.Artifact;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import mod.hey.studios.build.BuildSettings;
import mod.jbk.build.BuiltInLibraries;
import mod.pranav.dependency.resolver.DependencyResolver;
import pro.sketchware.R;

/**
 * خدمة تثبيت المكتبات - نمط القناتين لضمان الظهور على كافة الأجهزة.
 * Library install service - Two-channel pattern for guaranteed visibility.
 *
 * WHAT: Progress channel (HIGH, Silent) and Done channel (DEFAULT).
 * HOW: Mirroring GitHubUploadService to bypass MIUI notification suppression.
 * WHY: Ensures users see live progress and results reliably on MIUI/HyperOS.
 */
public class LibraryInstallService extends Service {

    private static final String TAG = "LibInstallService";
    public static final String EXTRA_COORDINATES = "extra_coordinates";
    public static final String ACTION_INSTALL = "action_install";
    public static final String ACTION_STATUS_CHANGE = "pro.sketchware.marketplace.STATUS_CHANGE";
    public static final String ACTION_INSTALL_STARTED = "pro.sketchware.marketplace.INSTALL_STARTED";
    public static final String ACTION_INSTALL_FINISHED = "pro.sketchware.marketplace.INSTALL_FINISHED";
    public static final String ACTION_INSTALL_SUCCESS = "pro.sketchware.marketplace.INSTALL_SUCCESS";
    public static final String ACTION_INSTALL_FAILURE = "pro.sketchware.marketplace.INSTALL_FAILURE";
    public static final String ACTION_LIBRARY_INSTALLED = "pro.sketchware.marketplace.LIBRARY_INSTALLED";
    public static final String ACTION_LIBRARY_INSTALL_FAILED = "pro.sketchware.marketplace.LIBRARY_INSTALL_FAILED";
    public static final String ACTION_CANCEL = "pro.sketchware.marketplace.ACTION_CANCEL";
    public static final String EXTRA_COORDINATE = "extra_coordinate";
    public static final String EXTRA_ERROR = "extra_error";
    public static final String EXTRA_LIBRARY_NAME = "extra_library_name";
    
    private static final String CHANNEL_PROGRESS = "library_install_progress";
    private static final String CHANNEL_DONE     = "library_install_done";
    private static final int NOTIFICATION_ID = 3001;
    private static final int DONE_ID         = 3002;
    
    // P1: Use single thread executor to mirror the working downloader (ManageLocalLibraryActivity)
    // and avoid conflicts during jar extraction or dexing.
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private NotificationManager notificationManager;
    
    private final List<String> pendingCoords = new ArrayList<>();
    private final List<String> activeArtifacts = new ArrayList<>();
    private final List<String> failedNames = new ArrayList<>();
    private final android.os.Handler timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private int totalCount = 0;
    private int completedCount = 0;
    private int failedCount = 0;
    private long startTime = 0;
    private String currentLibName = "";
    private String lastSuccessName = "";

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            cancelAndCleanupPending();
            return START_NOT_STICKY;
        }

        NotificationCompat.Builder skeleton = new NotificationCompat.Builder(this, CHANNEL_PROGRESS)
                .setSmallIcon(R.drawable.ic_mtrl_download)
                .setContentTitle("Installing library…")
                .setContentText("Preparing…")
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis())
                .addAction(R.drawable.ic_mtrl_close, "Cancel", getCancelIntent())
                .setPriority(NotificationCompat.PRIORITY_LOW);
        
        boolean foregroundOk = false;
        
        // WHAT: Declared foreground type with manual notify fallback.
        // WHY: MIUI may downgrade importance or block foreground starts silently.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, skeleton.build(), 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, skeleton.build());
            }
            foregroundOk = true;
        } catch (Throwable t) {
            Log.e(TAG, "startForeground threw: " + t);
        }

        if (!foregroundOk) {
            // foregroundFallbackNotify: Ensure progress is visible even if foreground start fails.
            notificationManager.notify(NOTIFICATION_ID, skeleton.build());
            Log.w(TAG, "DIAG using notify() fallback for skeleton");
        }

        if (intent != null && ACTION_INSTALL.equals(intent.getAction())) {
            List<String> coordinates = intent.getStringArrayListExtra(EXTRA_COORDINATES);
            if (coordinates != null) {
                if (totalCount == 0) {
                    sendBroadcast(new Intent(ACTION_INSTALL_STARTED));
                    failedNames.clear();
                    startTime = System.currentTimeMillis();
                    pro.sketchware.marketplace.utils.MarketplaceHelper.refreshCache();
                }
                totalCount += coordinates.size();
                for (String coord : coordinates) {
                    if (!pendingCoords.contains(coord)) {
                        pendingCoords.add(coord);
                        startInstallationTask(coord);
                    }
                }
                updateNotification();
            }
        }
        return START_NOT_STICKY;
    }



    private android.app.PendingIntent getCancelIntent() {
        Intent intent = new Intent(this, LibraryInstallService.class);
        intent.setAction(ACTION_CANCEL);
        int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
        }
        
        return android.app.PendingIntent.getService(this, NOTIFICATION_ID, intent, flags);
    }

    private synchronized void cancelAndCleanupPending() {
        // WHAT: Cancel current operations and cleanup partial folders.
        // HOW: Shutting down executor and deleting active artifacts from disk.
        // WHY: Ensures a clean state and stops resource usage immediately.
        executorService.shutdownNow();
        
        synchronized (activeArtifacts) {
            for (String artifact : activeArtifacts) {
                deleteFolderSafely(artifact);
            }
            activeArtifacts.clear();
        }
        
        pendingCoords.clear();
        pro.sketchware.marketplace.utils.MarketplaceHelper.refreshCache();
        notificationManager.cancel(NOTIFICATION_ID);
        notificationManager.cancel(DONE_ID);
        stopForeground(true);
        stopSelf();
    }

    private void deleteFolderSafely(String artifact) {
        File root = new File(Environment.getExternalStorageDirectory(), "/.sketchware/libs/local_libs/");
        if (!root.exists()) return;
        
        File[] kids = root.listFiles();
        if (kids != null) {
            String a = artifact.toLowerCase();
            for (File f : kids) {
                if (f.isDirectory()) {
                    String name = f.getName().toLowerCase();
                    // Robust matching for deletion
                    if (name.equals(a) || name.startsWith(a + "-") || name.startsWith(a + "_") 
                            || name.startsWith(a + ".") || name.startsWith(a + "v")) {
                        pro.sketchware.utility.FileUtil.deleteFile(f.getAbsolutePath());
                        Log.d(TAG, "Deleted partial folder: " + f.getName());
                    }
                }
            }
        }
    }

    private void startInstallationTask(String coordinate) {
        executorService.execute(() -> {
            boolean[] completed = {false};
            String artifactId = coordinate.contains(":") ? coordinate.split(":")[1] : coordinate;
            
            synchronized (activeArtifacts) {
                activeArtifacts.add(artifactId);
                currentLibName = artifactId;
            }
            updateNotification();
            
            // G3: Already Installed Guard - treat re-download of existing library as silent success.
            if (pro.sketchware.marketplace.utils.MarketplaceHelper.isActuallyOnDisk(coordinate)) {
                Log.d(TAG, "Library already installed: " + coordinate);
                handleSuccess(coordinate);
                return;
            }

            // WHAT: generousInstallTimeout - Increased to 5 minutes to accommodate slow connections and heavy AndroidX dependencies.
            // WHY: Previous 60s timeout caused false failures for larger library sets.
            timeoutHandler.postDelayed(() -> {
                if (!completed[0]) {
                    Log.e(TAG, "Timeout for " + coordinate);
                    // WHAT: Forced cache refresh for double-verification.
                    // WHY: Cache must be fresh before checking disk in case I/O finished but wasn't indexed.
                    pro.sketchware.marketplace.utils.MarketplaceHelper.refreshCache();
                    // Double check before failing
                    if (pro.sketchware.marketplace.utils.MarketplaceHelper.isActuallyOnDisk(coordinate)) {
                        handleSuccess(coordinate);
                    } else {
                        handleFailure(coordinate, "Network timeout");
                    }
                }
            }, 300000);

            try {
                Log.d(TAG, "Mirroring working downloader for: " + coordinate);
                updateNotification();

                // G1: Mirroring ManageLocalLibraryActivity requirements (Extracting Jars)
                BuiltInLibraries.maybeExtractAndroidJar((message, progress) -> {});
                BuiltInLibraries.maybeExtractCoreLambdaStubsJar();

                String[] parts = coordinate.split(":");
                if (parts.length != 3) {
                    completed[0] = true;
                    handleFailure(coordinate, "Invalid Maven format");
                    return;
                }

                BuildSettings buildSettings = new BuildSettings("system");
                DependencyResolver resolver = new DependencyResolver(parts[0], parts[1], parts[2], false, buildSettings);
                
                resolver.resolveDependency(new DependencyResolver.DependencyResolverCallback() {
                    @Override
                    public void onTaskCompleted(@NonNull List<String> dependencies) {
                        if (completed[0]) {
                            // WHAT: lateSuccessReconciler - Correct UI state if downloader finished after timeout.
                            // WHY: Prevents contradiction between "Failed" notification and "Installed" badge.
                            pro.sketchware.marketplace.utils.MarketplaceHelper.refreshCache();
                            if (pro.sketchware.marketplace.utils.MarketplaceHelper.isActuallyOnDisk(coordinate)) {
                                Intent successIntent = new Intent(ACTION_LIBRARY_INSTALLED);
                                successIntent.putExtra(EXTRA_LIBRARY_NAME, parts[1]);
                                sendBroadcast(successIntent);
                            }
                            return;
                        }
                        completed[0] = true;
                        
                        // WHAT: Forced cache refresh before double-verification callback.
                        // WHY: DependencyResolver finishes I/O; cache must be invalidated to see new folder.
                        pro.sketchware.marketplace.utils.MarketplaceHelper.refreshCache();

                        // G4: Double Verified Badge - التأكد من وجود المجلد فعلاً قبل إرسال النجاح.
                        if (pro.sketchware.marketplace.utils.MarketplaceHelper.isActuallyOnDisk(coordinate)) {
                            Log.d(TAG, "Successfully installed and verified: " + coordinate);
                            handleSuccess(coordinate);
                            
                            Intent successIntent = new Intent(ACTION_LIBRARY_INSTALLED);
                            successIntent.putExtra(EXTRA_LIBRARY_NAME, parts[1]);
                            sendBroadcast(successIntent);
                        } else {
                            Log.e(TAG, "Callback said success but folder missing: " + coordinate);
                            handleFailure(coordinate, "Verification failed (Folder missing)");
                        }
                    }

                    @Override
                    public void onDownloadError(@NonNull Artifact dep, @NonNull Throwable e) {
                        if (completed[0]) {
                            // lateSuccessReconciler for late errors/success check
                            pro.sketchware.marketplace.utils.MarketplaceHelper.refreshCache();
                            if (pro.sketchware.marketplace.utils.MarketplaceHelper.isActuallyOnDisk(coordinate)) {
                                Intent successIntent = new Intent(ACTION_LIBRARY_INSTALLED);
                                successIntent.putExtra(EXTRA_LIBRARY_NAME, parts[1]);
                                sendBroadcast(successIntent);
                            }
                            return;
                        }
                        completed[0] = true;
                        
                        // WHAT: Refresh cache on error callback to check if it partially or fully finished.
                        pro.sketchware.marketplace.utils.MarketplaceHelper.refreshCache();

                        // Check if it actually succeeded despite the error
                        if (pro.sketchware.marketplace.utils.MarketplaceHelper.isActuallyOnDisk(coordinate)) {
                            handleSuccess(coordinate);
                        } else {
                            handleFailure(coordinate, e.getMessage());
                            Intent failIntent = new Intent(ACTION_LIBRARY_INSTALL_FAILED);
                            failIntent.putExtra(EXTRA_LIBRARY_NAME, parts[1]);
                            failIntent.putExtra(EXTRA_ERROR, e.getMessage());
                            sendBroadcast(failIntent);
                        }
                    }

                    @Override
                    public void onArtifactNotFound(@NonNull Artifact dep) {
                        if (completed[0]) return;
                        completed[0] = true;
                        handleFailure(coordinate, "Artifact not found");
                    }
                    
                    @Override
                    public void dexingFailed(@NonNull Artifact dep, @NonNull Exception e) {
                        if (completed[0]) {
                            pro.sketchware.marketplace.utils.MarketplaceHelper.refreshCache();
                            if (pro.sketchware.marketplace.utils.MarketplaceHelper.isActuallyOnDisk(coordinate)) {
                                Intent successIntent = new Intent(ACTION_LIBRARY_INSTALLED);
                                successIntent.putExtra(EXTRA_LIBRARY_NAME, parts[1]);
                                sendBroadcast(successIntent);
                            }
                            return;
                        }
                        completed[0] = true;
                        
                        // WHAT: Refresh cache on dexing failure to check if folder exists.
                        pro.sketchware.marketplace.utils.MarketplaceHelper.refreshCache();

                        if (pro.sketchware.marketplace.utils.MarketplaceHelper.isActuallyOnDisk(coordinate)) {
                            handleSuccess(coordinate);
                        } else {
                            handleFailure(coordinate, "Dexing failed: " + e.getMessage());
                        }
                    }
                });
            } catch (Exception e) {
                if (!completed[0]) {
                    completed[0] = true;
                    handleFailure(coordinate, e.getMessage());
                }
            }
        });
    }

    private synchronized void handleSuccess(String coordinate) {
        String artifactId = coordinate.contains(":") ? coordinate.split(":")[1] : coordinate;
        synchronized (activeArtifacts) {
            activeArtifacts.remove(artifactId);
        }
        lastSuccessName = artifactId;
        pendingCoords.remove(coordinate);
        completedCount++;
        pro.sketchware.marketplace.utils.MarketplaceHelper.refreshCache();
        broadcastUpdate();
        checkCompletion();
    }

    private synchronized void handleFailure(String coordinate, String reason) {
        Log.e(TAG, "Install failed for " + coordinate + ": " + reason);
        // WHAT: Invalidate cache on failure to ensure UI consistency.
        pro.sketchware.marketplace.utils.MarketplaceHelper.refreshCache();
        String artifactId = coordinate.contains(":") ? coordinate.split(":")[1] : coordinate;
        synchronized (activeArtifacts) {
            activeArtifacts.remove(artifactId);
        }
        pendingCoords.remove(coordinate);
        String name = coordinate.contains(":") ? coordinate.split(":")[1] : coordinate;
        if (!failedNames.contains(name)) {
            failedNames.add(name);
        }
        failedCount++;
        pro.sketchware.marketplace.utils.MarketplaceHelper.refreshCache();
        broadcastUpdate();
        checkCompletion();
    }

    private void broadcastUpdate() {
        Intent intent = new Intent(ACTION_STATUS_CHANGE);
        sendBroadcast(intent);
    }

    private void checkCompletion() {
        if (completedCount + failedCount >= totalCount) {
            // T2: Use persistent final notification so it doesn't disappear silently
            showFinalNotification();
            sendBroadcast(new Intent(ACTION_INSTALL_FINISHED));
            
            // T3: Reset but keep service alive just enough for the final notify
            totalCount = 0;
            completedCount = 0;
            failedCount = 0;
            currentLibName = "";
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            stopSelf();
        } else {
            updateNotification();
        }
    }

    private void updateNotification() {
        // progressChannel: Using a LOW importance channel for silent progress updates.
        // HOW: Dynamic titles and text based on total count and current artifact.
        String title = totalCount == 1 ? "Installing " + currentLibName : "Installing libraries";
        String text = totalCount == 1 ? currentLibName : (completedCount + "/" + totalCount);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_PROGRESS)
                .setSmallIcon(R.drawable.ic_mtrl_download)
                .setContentTitle(title)
                .setContentText(text)
                .setProgress(totalCount, completedCount, false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setUsesChronometer(true)
                .setWhen(startTime)
                .addAction(R.drawable.ic_mtrl_close, "Cancel", getCancelIntent());

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * doneChannel: Displays the final notification using a separate DONE_ID.
     * WHY: Prevents notification collision and ensures results persist in drawer.
     */
    private void showFinalNotification() {
        NotificationCompat.Builder builder;
        if (failedCount == 0) {
            String name = lastSuccessName != null && !lastSuccessName.isEmpty() ? lastSuccessName : "Library";
            builder = new NotificationCompat.Builder(this, CHANNEL_DONE)
                    .setSmallIcon(R.drawable.ic_mtrl_done)
                    .setContentTitle(name + " is Successfully Installed")
                    .setContentText("All libraries installed successfully")
                    .setAutoCancel(true)
                    .setColor(0xFF00FFBC);
        } else {
            StringBuilder sb = new StringBuilder("Failed libraries:\n");
            for (String name : failedNames) {
                sb.append("• ").append(name).append("\n");
            }
            builder = new NotificationCompat.Builder(this, CHANNEL_DONE)
                    .setSmallIcon(R.drawable.ic_mtrl_warning)
                    .setContentTitle("Installation partially failed")
                    .setContentText(failedNames.get(0) + (failedNames.size() > 1 ? " and others" : ""))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(sb.toString()))
                    .setAutoCancel(true);
        }

        // Add content intent to return to marketplace
        android.app.PendingIntent contentIntent = android.app.PendingIntent.getActivity(this, 0,
                new Intent(this, pro.sketchware.marketplace.activities.LibraryMarketplaceActivity.class),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? android.app.PendingIntent.FLAG_IMMUTABLE : 0));
        builder.setContentIntent(contentIntent);

        notificationManager.notify(DONE_ID, builder.build());
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // progressChannel: LOW importance for silent background progress.
            NotificationChannel progress = new NotificationChannel(
                    CHANNEL_PROGRESS,
                    "Library Install Progress",
                    NotificationManager.IMPORTANCE_LOW
            );
            progress.setShowBadge(false);
            progress.enableVibration(false);
            progress.setSound(null, null);

            // doneChannel: DEFAULT importance for persistent completion results.
            NotificationChannel done = new NotificationChannel(
                    CHANNEL_DONE,
                    "Library Install Result",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            done.setShowBadge(true);
            
            // Cleanup legacy channels
            for (String old : new String[]{"library_install_channel", "library_install_channel_v2", "library_install_channel_v3", "library_install_progress_v4", "library_install_done_v4"}) {
                notificationManager.deleteNotificationChannel(old);
            }
            
            notificationManager.createNotificationChannel(progress);
            notificationManager.createNotificationChannel(done);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdownNow();
    }
}
