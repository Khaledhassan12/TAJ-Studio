package pro.sketchware.marketplace.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.cosmic.ide.dependency.resolver.api.Artifact;

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
 * خدمة تثبيت المكتبات - تم إصلاح معالجة الأخطاء وإضافة إشعارات تفصيلية وتحديث الفهرس المحلي.
 * Library install service - fixed error handling, added detailed notifications, and local index update.
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
    public static final String EXTRA_COORDINATE = "extra_coordinate";
    public static final String EXTRA_ERROR = "extra_error";
    
    private static final String CHANNEL_ID = "library_install_channel";
    private static final int NOTIFICATION_ID = 3001;
    private static final int MAX_CONCURRENT_INSTALLS = 3;

    private NotificationManager notificationManager;
    private final ExecutorService executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_INSTALLS);
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT_INSTALLS);
    
    private final List<String> pendingCoords = new ArrayList<>();
    private int totalCount = 0;
    private int completedCount = 0;
    private int failedCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_INSTALL.equals(intent.getAction())) {
            List<String> coordinates = intent.getStringArrayListExtra(EXTRA_COORDINATES);
            if (coordinates != null) {
                if (totalCount == 0) {
                    sendBroadcast(new Intent(ACTION_INSTALL_STARTED));
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

    private void startInstallationTask(String coordinate) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Starting install for: " + coordinate);
                semaphore.acquire();
                
                // T1: Immediate notification update when task starts
                updateNotification();

                String[] parts = coordinate.split(":");
                if (parts.length != 3) {
                    handleFailure(coordinate, "Invalid Maven format");
                    return;
                }

                BuildSettings buildSettings = new BuildSettings("system");
                DependencyResolver resolver = new DependencyResolver(parts[0], parts[1], parts[2], false, buildSettings);
                
                resolver.resolveDependency(new DependencyResolver.DependencyResolverCallback() {
                    @Override
                    public void onTaskCompleted(@NonNull List<String> dependencies) {
                        Log.d(TAG, "Successfully installed: " + coordinate);
                        handleSuccess(coordinate);
                        sendBroadcast(new Intent(ACTION_INSTALL_SUCCESS)
                                .putExtra(EXTRA_COORDINATE, coordinate));
                    }

                    @Override
                    public void onDownloadError(@NonNull Artifact dep, @NonNull Throwable e) {
                        Log.e(TAG, "Download error for " + coordinate, e);
                        handleFailure(coordinate, e.getMessage());
                        sendBroadcast(new Intent(ACTION_INSTALL_FAILURE)
                                .putExtra(EXTRA_COORDINATE, coordinate)
                                .putExtra(EXTRA_ERROR, e.getMessage()));
                    }

                    @Override
                    public void onArtifactNotFound(@NonNull Artifact dep) {
                        Log.e(TAG, "Artifact not found: " + coordinate);
                        handleFailure(coordinate, "Artifact not found");
                        sendBroadcast(new Intent(ACTION_INSTALL_FAILURE)
                                .putExtra(EXTRA_COORDINATE, coordinate)
                                .putExtra(EXTRA_ERROR, "Artifact not found"));
                    }
                    
                    @Override
                    public void dexingFailed(@NonNull Artifact dep, @NonNull Exception e) {
                        Log.e(TAG, "Dexing failed for " + coordinate, e);
                        handleFailure(coordinate, "Dexing failed: " + e.getMessage());
                        sendBroadcast(new Intent(ACTION_INSTALL_FAILURE)
                                .putExtra(EXTRA_COORDINATE, coordinate)
                                .putExtra(EXTRA_ERROR, "Dexing failed"));
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Fatal error installing " + coordinate, e);
                handleFailure(coordinate, e.getMessage());
            } finally {
                semaphore.release();
            }
        });
    }

    private synchronized void handleSuccess(String coordinate) {
        pendingCoords.remove(coordinate);
        completedCount++;
        broadcastUpdate();
        checkCompletion();
    }

    private synchronized void handleFailure(String coordinate, String reason) {
        Log.e(TAG, "Install failed for " + coordinate + ": " + reason);
        pendingCoords.remove(coordinate);
        failedCount++;
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
            stopForeground(false);
            stopSelf();
        } else {
            updateNotification();
        }
    }

    private void updateNotification() {
        String msg = getString(R.string.lib_install_progress, completedCount, totalCount);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mtrl_download)
                .setContentTitle(getString(R.string.lib_installing))
                .setContentText(msg)
                .setProgress(totalCount, completedCount, false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true); // Don't beep every update

        startForeground(NOTIFICATION_ID, builder.build());
    }

    private void showFinalNotification() {
        String message;
        int iconRes;
        
        if (failedCount == 0) {
            message = getString(R.string.lib_install_all_success);
            iconRes = R.drawable.ic_mtrl_done;
        } else {
            message = getString(R.string.lib_install_fail_count, failedCount);
            iconRes = R.drawable.ic_mtrl_warning;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(iconRes)
                .setContentTitle(getString(R.string.lib_install_complete))
                .setContentText(message)
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Library Installation",
                    NotificationManager.IMPORTANCE_LOW
            );
            notificationManager.createNotificationChannel(channel);
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
