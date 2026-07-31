package pro.sketchware.github;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.File;

import pro.sketchware.R;

public class GitHubUploadService extends Service {

    public static final String ACTION_START_UPLOAD = "pro.sketchware.github.action.START_UPLOAD";
    public static final String EXTRA_PROJECT_TITLE = "extra_project_title";
    public static final String EXTRA_PROJECT_ROOT = "extra_project_root";

    private static final String CHANNEL_PROGRESS = "github_upload_progress";
    private static final String CHANNEL_DONE = "github_upload_done";
    private static final int NOTIFICATION_ID = 1001;
    private static final int DONE_ID = 1002;

    private NotificationManager notificationManager;
    private String projectTitle;
    private Bitmap currentLargeIcon;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannels();
        currentLargeIcon = getGitHubBitmap();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START_UPLOAD.equals(intent.getAction())) {
            projectTitle = intent.getStringExtra(EXTRA_PROJECT_TITLE);
            String rootPath = intent.getStringExtra(EXTRA_PROJECT_ROOT);

            if (projectTitle == null || rootPath == null) {
                stopSelf();
                return START_NOT_STICKY;
            }

            startForeground(NOTIFICATION_ID, buildProgressNotification(0, 0, projectTitle));
            startUpload(new File(rootPath));
        }
        return START_NOT_STICKY;
    }

    private void startUpload(File projectRoot) {
        GitHubManager manager = GitHubManager.getInstance(this);
        String rootPath = projectRoot.getAbsolutePath();
        
        // Try to load avatar for large icon
        manager.loadAvatarBitmap(manager.getUserAvatar(), new GitHubManager.AvatarBitmapCallback() {
            @Override
            public void onBitmap(Bitmap bitmap) {
                currentLargeIcon = bitmap;
                notificationManager.notify(NOTIFICATION_ID, buildProgressNotification(0, 0, projectTitle));
            }

            @Override
            public void onFailed() {
                // Keep default github icon
            }
        });

        manager.uploadProject(projectTitle, projectRoot, new GitHubManager.UploadCallback() {
            @Override
            public void onProgress(int done, int total, String currentPath) {
                notificationManager.notify(NOTIFICATION_ID, buildProgressNotification(done, total, currentPath));
            }

            @Override
            public void onSuccess(String repoHtmlUrl, String details) {
                // نؤرخ نجاح الرفع هنا في السجل المحلي لربطه بالأيقونة وتوفير الوصول السريع
                // We record the success here in the local log to link it with the icon and provide quick access.
                int fileCount = 0;
                if (details.contains("files=")) {
                    try {
                        String sub = details.substring(details.indexOf("files=") + 6);
                        fileCount = Integer.parseInt(sub.split(" ")[0]);
                    } catch (Exception ignored) {}
                }
                
                manager.recordSuccessfulUpload(
                        GitHubManager.extractProjectId(rootPath),
                        projectTitle,
                        repoHtmlUrl,
                        fileCount,
                        details
                );

                notificationManager.notify(DONE_ID, buildSuccessNotification(repoHtmlUrl, details));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                } else {
                    stopForeground(true);
                }
                stopSelf();
            }

            @Override
            public void onError(String error, String details) {
                notificationManager.notify(DONE_ID, buildErrorNotification(error, details));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                } else {
                    stopForeground(true);
                }
                stopSelf();
            }
        });
    }

    private android.app.Notification buildProgressNotification(int done, int total, String content) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_PROGRESS)
                .setSmallIcon(R.drawable.ic_github_brand)
                .setLargeIcon(currentLargeIcon)
                .setContentTitle(getString(R.string.github_upload_in_progress_title))
                .setContentText(getString(R.string.github_upload_in_progress_body, projectTitle))
                .setSubText(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);

        if (total > 0) {
            builder.setProgress(total, done, false);
        } else {
            builder.setProgress(0, 0, true);
        }

        return builder.build();
    }

    private android.app.Notification buildSuccessNotification(String url, String details) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_github_brand)
                .setLargeIcon(currentLargeIcon)
                .setContentTitle(getString(R.string.github_upload_success_title))
                .setContentText(getString(R.string.github_upload_success_body, projectTitle))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(details + "\n" + url))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_mtrl_arrow_right, getString(R.string.github_upload_view_on_github), pendingIntent)
                .build();
    }

    private android.app.Notification buildErrorNotification(String error, String details) {
        return new NotificationCompat.Builder(this, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_github_brand)
                .setLargeIcon(currentLargeIcon)
                .setContentTitle(getString(R.string.github_upload_failed_title))
                .setContentText(getString(R.string.github_upload_failed_body, projectTitle))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(details + "\n" + error))
                .setAutoCancel(true)
                .build();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel progressChannel = new NotificationChannel(
                    CHANNEL_PROGRESS,
                    getString(R.string.github_upload_channel_progress_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            
            NotificationChannel doneChannel = new NotificationChannel(
                    CHANNEL_DONE,
                    getString(R.string.github_upload_channel_done_name),
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            notificationManager.createNotificationChannel(progressChannel);
            notificationManager.createNotificationChannel(doneChannel);
        }
    }

    private Bitmap getGitHubBitmap() {
        Drawable drawable = ContextCompat.getDrawable(this, R.drawable.ic_github_brand);
        Bitmap bitmap;
        if (drawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) drawable).getBitmap();
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), 
                    drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }
        return GitHubManager.getCircleBitmap(bitmap);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
