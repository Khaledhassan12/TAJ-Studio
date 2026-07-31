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
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;

/**
 * خدمة خلفية لإدارة عملية الـ Commit بشكل مستقل عن الواجهة.
 * تضمن استمرار الرفع حتى لو أغلق المستخدم التطبيق، مع عرض إشعار تقدّم حيّ.
 * Foreground service to manage the Commit process independently of the UI.
 * Ensures the upload continues even if the app is closed, with a live progress notification.
 */
public class GitCommitService extends Service {

    public static final String ACTION_COMMIT = "pro.sketchware.github.action.COMMIT";
    public static final String EXTRA_RECORD_JSON = "extra_record_json";
    public static final String EXTRA_MESSAGE = "extra_message";
    public static final String EXTRA_README = "extra_readme";
    public static final String EXTRA_ATTACHED_PATHS = "extra_attached_paths";

    private static final String CHANNEL_PROGRESS = "git_commit_progress";
    private static final String CHANNEL_DONE = "git_commit_done";
    private static final int PROGRESS_ID = 2001;
    private static final int DONE_ID = 2002;

    private NotificationManager notificationManager;
    private Bitmap githubBitmap;
    private String repoName = "Repository";

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannels();
        githubBitmap = getGitHubBitmap();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_COMMIT.equals(intent.getAction())) {
            // نُظهر الإشعار الهيكلي فوراً كأول إجراء لضمان عدم تأخر ظهور الإشعار في مركز التنبيهات.
            // Raise the skeleton notification immediately as the first action to prevent any perceived UI delay.
            startForeground(PROGRESS_ID, buildProgressNotification(0, 0, getString(R.string.git_commit_preparing)));

            // ننقل كل العمل الثقيل (تحليل JSON وقراءة الملفات) إلى خيط خلفي.
            // Move all heavy work (JSON parsing and file I/O) to a background thread.
            new Thread(() -> handleCommitWork(intent)).start();
        }
        return START_NOT_STICKY;
    }

    private void handleCommitWork(Intent intent) {
        String recordJson = intent.getStringExtra(EXTRA_RECORD_JSON);
        String message = intent.getStringExtra(EXTRA_MESSAGE);
        String readme = intent.getStringExtra(EXTRA_README);
        String[] paths = intent.getStringArrayExtra(EXTRA_ATTACHED_PATHS);

        if (recordJson == null || message == null) {
            cleanupAndStop();
            return;
        }

        GitHubManager manager = GitHubManager.getInstance(this);
        GitHubManager.GitUploadRecord record = new com.google.gson.Gson().fromJson(recordJson, GitHubManager.GitUploadRecord.class);
        repoName = record.repoHtmlUrl.substring(record.repoHtmlUrl.lastIndexOf("/") + 1);

        List<File> attachments = new ArrayList<>();
        if (paths != null) {
            for (String p : paths) {
                File f = new File(p);
                if (f.exists()) attachments.add(f);
            }
        }

        // تحديث الإشعار باسم المستودع الفعلي بعد استخراجه في الخلفية
        // Update the notification with the actual repo name after extracting it in the background.
        notificationManager.notify(PROGRESS_ID, buildProgressNotification(0, 0, getString(R.string.git_commit_preparing)));

        manager.createCommit(record, message, readme, attachments, new GitHubManager.CommitCallback() {
            @Override
            public void onProgress(int done, int total, String currentPath) {
                notificationManager.notify(PROGRESS_ID, buildProgressNotification(done, total, currentPath));
            }

            @Override
            public void onSuccess(String commitUrl) {
                notificationManager.notify(DONE_ID, buildSuccessNotification(commitUrl));
                cleanupAndStop();
            }

            @Override
            public void onError(String error, String details) {
                notificationManager.notify(DONE_ID, buildErrorNotification(error, details));
                cleanupAndStop();
            }
        });
    }

    private void cleanupAndStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    private android.app.Notification buildProgressNotification(int done, int total, String content) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_PROGRESS)
                .setSmallIcon(R.drawable.ic_github_brand)
                .setLargeIcon(githubBitmap)
                .setContentTitle(getString(R.string.git_commit_progress_title))
                .setContentText(repoName)
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

    private android.app.Notification buildSuccessNotification(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_github_brand)
                .setLargeIcon(githubBitmap)
                .setContentTitle(getString(R.string.git_commit_success_title))
                .setContentText(getString(R.string.git_commit_success_body, repoName))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(url))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .addAction(R.drawable.ic_mtrl_web, getString(R.string.git_commit_view_commit), pi)
                .build();
    }

    private android.app.Notification buildErrorNotification(String error, String details) {
        return new NotificationCompat.Builder(this, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_github_brand)
                .setLargeIcon(githubBitmap)
                .setContentTitle(getString(R.string.git_commit_failed_title))
                .setContentText(getString(R.string.git_commit_failed_body, repoName))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(error + "\n" + details))
                .setAutoCancel(true)
                .build();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel p = new NotificationChannel(CHANNEL_PROGRESS, 
                    getString(R.string.github_upload_channel_progress_name), NotificationManager.IMPORTANCE_LOW);
            NotificationChannel d = new NotificationChannel(CHANNEL_DONE, 
                    getString(R.string.github_upload_channel_done_name), NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(p);
            notificationManager.createNotificationChannel(d);
        }
    }

    private Bitmap getGitHubBitmap() {
        Drawable d = ContextCompat.getDrawable(this, R.drawable.ic_github_brand);
        if (d instanceof BitmapDrawable) return ((BitmapDrawable) d).getBitmap();
        Bitmap b = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, c.getWidth(), c.getHeight());
        d.draw(c);
        return GitHubManager.getCircleBitmap(b);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
