package pro.sketchware.github;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GitHubManager {

    private static final String PREF_NAME = "github_prefs_secure";
    private static final String KEY_TOKEN = "access_token";
    private static final String KEY_USER_LOGIN = "user_login";
    private static final String KEY_USER_AVATAR = "user_avatar";

    private static GitHubManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final OkHttpClient client;
    private final Gson gson;
    private final ExecutorService executor;
    private final Handler mainHandler;
    
    private final List<AuthStateListener> listeners = new ArrayList<>();

    public interface AuthStateListener {
        void onAuthStateChanged(boolean signedIn);
    }

    public interface SignInCallback {
        void onSuccess(String login);
        void onError(String error);
    }

    public interface UploadCallback {
        void onProgress(int done, int total, String currentPath);
        void onSuccess(String repoHtmlUrl, String details);
        void onError(String error, String details);
    }

    public interface CommitCallback {
        void onProgress(int done, int total, String currentPath);
        void onSuccess(String commitHtmlUrl);
        void onError(String error, String details);
    }

    public interface AvatarBitmapCallback {
        void onBitmap(Bitmap bitmap);
        void onFailed();
    }

    public static class UploadFile {
        public final String relativePath;
        public final File file;

        public UploadFile(String relativePath, File file) {
            this.relativePath = relativePath;
            this.file = file;
        }
    }

    private String cachedAvatarUrl;
    private Bitmap cachedAvatarBitmap;

    // اسم ملف التفضيلات الخاص بسجل الرفعات؛ نستخدم ملفاً منفصلاً لسهولة الإدارة
    // Preferences file name for upload records; kept separate for easier management.
    private static final String PREF_UPLOAD_LOG = "github_upload_log";
    private static final String KEY_RECORDS = "records";

    public static class GitUploadRecord {
        public String projectId;
        public String projectTitle;
        public String repoHtmlUrl;
        public String login;
        public String avatarUrl;
        public long uploadedAtMillis;
        public int fileCount;
        public String byDirSummary;

        // نحتاج هذا المنشئ الفارغ لعملية تحويل Gson بشكل صحيح
        // Empty constructor needed for proper Gson deserialization.
        public GitUploadRecord() {}
    }

    private GitHubManager(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient();
        this.gson = new Gson();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            this.prefs = EncryptedSharedPreferences.create(
                    PREF_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize secure preferences", e);
        }
    }

    public static synchronized GitHubManager getInstance(Context context) {
        if (instance == null) {
            instance = new GitHubManager(context);
        }
        return instance;
    }

    public void addListener(AuthStateListener listener) {
        listeners.add(listener);
    }

    public void removeListener(AuthStateListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        boolean signedIn = isSignedIn();
        mainHandler.post(() -> {
            for (AuthStateListener listener : listeners) {
                listener.onAuthStateChanged(signedIn);
            }
        });
    }

    public boolean isSignedIn() {
        return prefs.getString(KEY_TOKEN, null) != null;
    }

    public String getAccessToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public String getUserLogin() {
        return prefs.getString(KEY_USER_LOGIN, "Unknown");
    }

    public String getUserAvatar() {
        return prefs.getString(KEY_USER_AVATAR, null);
    }

    public void signOut() {
        prefs.edit().clear().apply();
        cachedAvatarUrl = null;
        if (cachedAvatarBitmap != null) {
            cachedAvatarBitmap.recycle();
            cachedAvatarBitmap = null;
        }
        notifyListeners();
    }

    /**
     * نسجل هنا كل عملية رفع ناجحة في سجل محلي؛ هذا يتيح لنا عرض الرفعات لاحقاً وربطها بالأيقونات المحلية.
     * We log every successful upload to a local record; this allows us to display history
     * and link uploads back to their local project icons.
     */
    public synchronized void recordSuccessfulUpload(String projectId, String projectTitle, 
                                                     String repoHtmlUrl, int fileCount, String byDirSummary) {
        String login = getUserLogin();
        String avatar = getUserAvatar();
        
        List<GitUploadRecord> records = getUploadRecords();
        
        GitUploadRecord newRecord = new GitUploadRecord();
        newRecord.projectId = projectId;
        newRecord.projectTitle = projectTitle;
        newRecord.repoHtmlUrl = repoHtmlUrl;
        newRecord.login = login;
        newRecord.avatarUrl = avatar;
        newRecord.uploadedAtMillis = System.currentTimeMillis();
        newRecord.fileCount = fileCount;
        newRecord.byDirSummary = byDirSummary;

        // نزيل أي سجل قديم لنفس المشروع في نفس المستودع لتجنب التكرار
        // Remove any existing record for the same project/repo to avoid duplicates.
        records.removeIf(r -> (projectId != null && projectId.equals(r.projectId)) 
                || repoHtmlUrl.equals(r.repoHtmlUrl));
        
        // نضع السجل الجديد في البداية (الأحدث أولاً)
        // Insert the new record at the beginning (newest first).
        records.add(0, newRecord);

        // نحدد السجل بـ 200 مدخلة كحد أقصى للحفاظ على الأداء
        // Limit history to 200 entries to maintain performance.
        if (records.size() > 200) {
            records = records.subList(0, 200);
        }

        SharedPreferences logPrefs = context.getSharedPreferences(PREF_UPLOAD_LOG, Context.MODE_PRIVATE);
        logPrefs.edit().putString(KEY_RECORDS, gson.toJson(records)).apply();
    }

    /**
     * نسترجع قائمة الرفعات السابقة؛ القائمة مرتبة زمنياً من الأحدث للأقدم.
     * Retrieves the list of past uploads, ordered chronologically (newest first).
     */
    public List<GitUploadRecord> getUploadRecords() {
        SharedPreferences logPrefs = context.getSharedPreferences(PREF_UPLOAD_LOG, Context.MODE_PRIVATE);
        String json = logPrefs.getString(KEY_RECORDS, "[]");
        try {
            GitUploadRecord[] array = gson.fromJson(json, GitUploadRecord[].class);
            List<GitUploadRecord> list = new ArrayList<>();
            if (array != null) {
                Collections.addAll(list, array);
            }
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * نستخدم هذا المساعد لاستخراج معرف المشروع (مثل 601) من مساره الكامل؛
     * نعتمد على أن المسار ينتهي عادة بـ /.sketchware/mysc/ID
     * Helper to extract project ID (e.g., 601) from its absolute path;
     * assumes the path typically ends with /.sketchware/mysc/ID.
     */
    public static String extractProjectId(String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) return null;
        File file = new File(absolutePath);
        return file.getName();
    }

    public void loadAvatarBitmap(String url, AvatarBitmapCallback callback) {
        if (url == null || url.isEmpty()) {
            mainHandler.post(callback::onFailed);
            return;
        }

        if (url.equals(cachedAvatarUrl) && cachedAvatarBitmap != null) {
            mainHandler.post(() -> callback.onBitmap(cachedAvatarBitmap));
            return;
        }

        executor.execute(() -> {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "TAJ-Studio")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    Bitmap raw = BitmapFactory.decodeStream(response.body().byteStream());
                    if (raw != null) {
                        Bitmap circular = getCircleBitmap(raw);
                        cachedAvatarUrl = url;
                        cachedAvatarBitmap = circular;
                        mainHandler.post(() -> callback.onBitmap(circular));
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
            mainHandler.post(callback::onFailed);
        });
    }

    public static Bitmap getCircleBitmap(Bitmap bitmap) {
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));

        float r = size / 2f;
        canvas.drawCircle(r, r, r, paint);
        return output;
    }

    public void refreshUserInfoOnce() {
        executor.execute(() -> {
            String token = getAccessToken();
            String avatar = getUserAvatar();
            if (token == null || (avatar != null && !avatar.isEmpty())) {
                return;
            }

            Request request = new Request.Builder()
                    .url("https://api.github.com/user")
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "TAJ-Studio")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    UserResponse user = gson.fromJson(response.body().string(), UserResponse.class);
                    prefs.edit()
                            .putString(KEY_USER_LOGIN, user.login)
                            .putString(KEY_USER_AVATAR, user.avatarUrl)
                            .apply();
                    notifyListeners();
                }
            } catch (IOException ignored) {
            }
        });
    }

    /**
     * ينشئ commit جديداً في المستودع باستخدام الملفات المحلية، رسالة مخصصة، وإرفاقات صور اختيارية.
     * نستخدم تحليل الرابط المباشر للمستودع لضمان الدقة وتجنب أخطاء 404 الناتجة عن تباين التسمية.    /**
     * ينشئ commit جديداً في المستودع باستخدام الملفات المحلية، رسالة مخصصة، وإرفاقات صور اختيارية.
     * نستخدم تحليل الرابط المباشر للمستودع لضمان الدقة وتجنب أخطاء 404 الناتجة عن تباين التسمية.
     * Creates a new commit in the repository with local files, custom message, and optional image attachments.
     * We analyze the repo's HTML URL directly to ensure accuracy and avoid 404s caused by naming mismatches.
     */
    public void createCommit(GitUploadRecord record, String message, String readmeContent, 
                             List<File> attachedFiles, CommitCallback callback) {
        executor.execute(() -> {
            String token = getAccessToken();
            StringBuilder stages = new StringBuilder();
            if (token == null) {
                mainHandler.post(() -> callback.onError("Not signed in.", "auth=fail"));
                return;
            }

            File projectRoot = new File(a.a.a.wq.d(record.projectId));
            if (!projectRoot.exists()) {
                mainHandler.post(() -> callback.onError("Local project files not found.", "fs=missing"));
                return;
            }

            try {
                // استخراج المالك والمستودع من الرابط المخزن بدلاً من التخمين من العنوان
                // Extract owner and repo from the stored URL instead of guessing from the title.
                String repoPath = record.repoHtmlUrl.replace("https://github.com/", "");
                String[] parts = repoPath.split("/");
                if (parts.length < 2) {
                    mainHandler.post(() -> callback.onError("Invalid repository URL format.", "url=invalid"));
                    return;
                }
                String owner = parts[0];
                String repo = parts[1];
                String repoUrl = "https://api.github.com/repos/" + owner + "/" + repo;

                mainHandler.post(() -> callback.onProgress(0, 0, "Fetching repo info…"));
                
                // جلب الفرع الافتراضي ديناميكياً لتجنب تثبيته على "main"
                // Fetch default branch dynamically instead of hardcoding to "main".
                String defaultBranch = "main";
                Request getRepoInfo = buildRequest(repoUrl).get().build();
                try (Response response = client.newCall(getRepoInfo).execute()) {
                    stages.append("repo_get=").append(response.code()).append(" ");
                    if (response.isSuccessful() && response.body() != null) {
                        JsonObject repoJson = gson.fromJson(response.body().string(), JsonObject.class);
                        if (repoJson.has("default_branch")) {
                            defaultBranch = repoJson.get("default_branch").getAsString();
                        }
                    }
                }

                // جلب آخر commit SHA للفرع الأساسي
                // Fetch latest commit SHA for the base branch.
                String baseSha = null;
                Request getRefRequest = buildRequest(repoUrl + "/git/ref/heads/" + defaultBranch).get().build();
                try (Response response = client.newCall(getRefRequest).execute()) {
                    stages.append("ref_get=").append(response.code()).append(" ");
                    if (response.isSuccessful() && response.body() != null) {
                        JsonObject refJson = gson.fromJson(response.body().string(), JsonObject.class);
                        baseSha = refJson.getAsJsonObject("object").get("sha").getAsString();
                    } else {
                        mainHandler.post(() -> callback.onError("Failed to fetch latest commit. Code: " + response.code(), stages.toString()));
                        return;
                    }
                }

                List<UploadFile> filesToUpload = collectUploadFiles(projectRoot);
                int totalBlobs = filesToUpload.size() + (attachedFiles != null ? attachedFiles.size() : 0);
                JsonArray treeArray = new JsonArray();
                int doneBlobs = 0;

                // 1. معالجة ملفات المشروع (نصية أم ثنائية)
                // 1. Process project files (text vs binary)
                for (UploadFile uploadFile : filesToUpload) {
                    final int progress = ++doneBlobs;
                    mainHandler.post(() -> callback.onProgress(progress, totalBlobs, uploadFile.relativePath));
                    
                    String contentB64 = isBinaryByExtension(uploadFile.file.getName()) ? 
                            encodeBinaryToBase64(uploadFile.file) : encodeFileToBase64(uploadFile.file);
                    
                    JsonObject blobBody = new JsonObject();
                    blobBody.addProperty("content", contentB64);
                    blobBody.addProperty("encoding", "base64");
                    
                    Request createBlob = buildRequest(repoUrl + "/git/blobs")
                            .post(RequestBody.create(blobBody.toString(), MediaType.get("application/json")))
                            .build();
                    try (Response response = client.newCall(createBlob).execute()) {
                        if (response.isSuccessful() && response.body() != null) {
                            String sha = gson.fromJson(response.body().string(), JsonObject.class).get("sha").getAsString();
                            JsonObject treeElement = new JsonObject();
                            treeElement.addProperty("path", uploadFile.relativePath);
                            treeElement.addProperty("mode", "100644");
                            treeElement.addProperty("type", "blob");
                            treeElement.addProperty("sha", sha);
                            treeArray.add(treeElement);
                        }
                    }
                }

                // 2. معالجة الإرفاقات (دائماً ثنائية خام) داخل مجلد خاص
                // 2. Process attachments (always raw binary) inside a dedicated folder.
                if (attachedFiles != null) {
                    for (File file : attachedFiles) {
                        final int progress = ++doneBlobs;
                        mainHandler.post(() -> callback.onProgress(progress, totalBlobs, "attachment: " + file.getName()));
                        
                        String b64 = encodeBinaryToBase64(file);
                        JsonObject blobBody = new JsonObject();
                        blobBody.addProperty("content", b64);
                        blobBody.addProperty("encoding", "base64");
                        
                        Request createBlob = buildRequest(repoUrl + "/git/blobs")
                                .post(RequestBody.create(blobBody.toString(), MediaType.get("application/json")))
                                .build();
                        try (Response response = client.newCall(createBlob).execute()) {
                            if (response.isSuccessful() && response.body() != null) {
                                String sha = gson.fromJson(response.body().string(), JsonObject.class).get("sha").getAsString();
                                JsonObject treeElement = new JsonObject();
                                treeElement.addProperty("path", "attachments/" + file.getName());
                                treeElement.addProperty("mode", "100644");
                                treeElement.addProperty("type", "blob");
                                treeElement.addProperty("sha", sha);
                                treeArray.add(treeElement);
                            }
                        }
                    }
                }

                // 3. معالجة الـ README المخصص
                // 3. Process custom README.
                if (readmeContent != null && !readmeContent.trim().isEmpty()) {
                    String b64 = Base64.encodeToString(readmeContent.getBytes(java.nio.charset.StandardCharsets.UTF_8), Base64.NO_WRAP);
                    JsonObject blobBody = new JsonObject();
                    blobBody.addProperty("content", b64);
                    blobBody.addProperty("encoding", "base64");
                    
                    Request createBlob = buildRequest(repoUrl + "/git/blobs")
                            .post(RequestBody.create(blobBody.toString(), MediaType.get("application/json")))
                            .build();
                    try (Response response = client.newCall(createBlob).execute()) {
                        if (response.isSuccessful() && response.body() != null) {
                            String sha = gson.fromJson(response.body().string(), JsonObject.class).get("sha").getAsString();
                            JsonObject treeElement = new JsonObject();
                            treeElement.addProperty("path", "README.md");
                            treeElement.addProperty("mode", "100644");
                            treeElement.addProperty("type", "blob");
                            treeElement.addProperty("sha", sha);
                            treeArray.add(treeElement);
                        }
                    }
                }

                mainHandler.post(() -> callback.onProgress(totalBlobs, totalBlobs, "Finalizing commit…"));
                
                // إنشاء الشجرة والـ commit وتحديث الـ Ref
                // Create tree, commit, and update Ref.
                JsonObject treeBody = new JsonObject();
                treeBody.add("tree", treeArray);
                String newTreeSha;
                Request createTree = buildRequest(repoUrl + "/git/trees")
                        .post(RequestBody.create(treeBody.toString(), MediaType.get("application/json")))
                        .build();
                try (Response response = client.newCall(createTree).execute()) {
                    stages.append("tree=").append(response.code()).append(" ");
                    if (response.isSuccessful() && response.body() != null) {
                        newTreeSha = gson.fromJson(response.body().string(), JsonObject.class).get("sha").getAsString();
                    } else {
                        mainHandler.post(() -> callback.onError("Tree creation failed.", stages.toString()));
                        return;
                    }
                }

                JsonObject commitBody = new JsonObject();
                commitBody.addProperty("message", message);
                commitBody.addProperty("tree", newTreeSha);
                JsonArray parents = new JsonArray();
                parents.add(baseSha);
                commitBody.add("parents", parents);
                
                String newCommitSha;
                Request createCommit = buildRequest(repoUrl + "/git/commits")
                        .post(RequestBody.create(commitBody.toString(), MediaType.get("application/json")))
                        .build();
                try (Response response = client.newCall(createCommit).execute()) {
                    stages.append("commit=").append(response.code()).append(" ");
                    if (response.isSuccessful() && response.body() != null) {
                        newCommitSha = gson.fromJson(response.body().string(), JsonObject.class).get("sha").getAsString();
                    } else {
                        mainHandler.post(() -> callback.onError("Commit failed.", stages.toString()));
                        return;
                    }
                }

                JsonObject refUpdateBody = new JsonObject();
                refUpdateBody.addProperty("sha", newCommitSha);
                refUpdateBody.addProperty("force", false);
                
                Request updateRef = buildRequest(repoUrl + "/git/refs/heads/" + defaultBranch)
                        .patch(RequestBody.create(refUpdateBody.toString(), MediaType.get("application/json")))
                        .build();
                try (Response response = client.newCall(updateRef).execute()) {
                    stages.append("ref_patch=").append(response.code()).append(" ");
                    if (response.isSuccessful()) {
                        recordSuccessfulUpload(record.projectId, record.projectTitle, record.repoHtmlUrl, 
                                filesToUpload.size(), "Commit push");
                        String commitUrl = "https://github.com/" + owner + "/" + repo + "/commit/" + newCommitSha;
                        mainHandler.post(() -> callback.onSuccess(commitUrl));
                    } else {
                        mainHandler.post(() -> callback.onError("Ref update failed.", stages.toString()));
                    }
                }

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.toString(), stages.toString()));
            }
        });
    }

    public void signInWithToken(String token, SignInCallback callback) {
        executor.execute(() -> {
            Request request = new Request.Builder()
                    .url("https://api.github.com/user")
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "TAJ-Studio")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    UserResponse user = gson.fromJson(response.body().string(), UserResponse.class);
                    prefs.edit()
                            .putString(KEY_TOKEN, token)
                            .putString(KEY_USER_LOGIN, user.login)
                            .putString(KEY_USER_AVATAR, user.avatarUrl)
                            .apply();
                    
                    notifyListeners();
                    mainHandler.post(() -> callback.onSuccess(user.login));
                } else if (response.code() == 401) {
                    mainHandler.post(() -> callback.onError("Invalid or expired token. Please check your token and scopes."));
                } else {
                    String msg = "GitHub responded with code " + response.code();
                    mainHandler.post(() -> callback.onError(msg));
                }
            } catch (IOException e) {
                mainHandler.post(() -> callback.onError("Network error. Check your connection."));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("An unexpected error occurred."));
            }
        });
    }

    public void uploadProject(String projectTitle, File projectRoot, UploadCallback callback) {
        executor.execute(() -> {
            String token = getAccessToken();
            String login = getUserLogin();
            StringBuilder stages = new StringBuilder();
            
            if (token == null || login.equals("Unknown")) {
                mainHandler.post(() -> callback.onError("Not signed in.", "auth=fail"));
                return;
            }

            if (projectRoot == null || !projectRoot.exists() || !projectRoot.isDirectory()) {
                mainHandler.post(() -> callback.onError("Project folder not found.", "fs=missing"));
                return;
            }

            try {
                mainHandler.post(() -> callback.onProgress(0, 0, "Preparing files…"));
                List<UploadFile> filesToUpload = collectUploadFiles(projectRoot);

                // Telemetry
                int javaCount = 0, resCount = 0, assetsCount = 0, rootCount = 0, otherCount = 0;
                for (UploadFile f : filesToUpload) {
                    String p = f.relativePath;
                    if (p.contains("/src/main/java/")) javaCount++;
                    else if (p.contains("/src/main/res/")) resCount++;
                    else if (p.contains("/src/main/assets/")) assetsCount++;
                    else if (!p.contains("/")) rootCount++;
                    else otherCount++;
                }
                String[] topList = projectRoot.list();
                stages.append("files=").append(filesToUpload.size())
                      .append(" top=").append(topList != null ? java.util.Arrays.toString(topList) : "[]")
                      .append(" byDir={java=").append(javaCount).append(",res=").append(resCount)
                      .append(",assets=").append(assetsCount).append(",root=").append(rootCount)
                      .append(",other=").append(otherCount).append("}\n");

                if (filesToUpload.isEmpty()) {
                    mainHandler.post(() -> callback.onError("No files to upload.", stages.toString()));
                    return;
                }

                String repoName = sanitizeRepoName(projectTitle);
                String repoUrl = "https://api.github.com/repos/" + login + "/" + repoName;
                
                mainHandler.post(() -> callback.onProgress(0, filesToUpload.size(), "Checking repository…"));
                
                String defaultBranch = "main";
                Request getRepoRequest = buildRequest(repoUrl).get().build();
                try (Response response = client.newCall(getRepoRequest).execute()) {
                    stages.append("repo_get=").append(response.code()).append(" ");
                    if (response.code() == 404) {
                        mainHandler.post(() -> callback.onProgress(0, filesToUpload.size(), "Creating repository…"));
                        JsonObject createRepoBody = new JsonObject();
                        createRepoBody.addProperty("name", repoName);
                        createRepoBody.addProperty("private", false);
                        createRepoBody.addProperty("auto_init", true); // تهيئة الـ backend بـ commit أولي => يمنع 409 على blob

                        Request createRepoRequest = buildRequest("https://api.github.com/user/repos")
                                .post(RequestBody.create(createRepoBody.toString(), MediaType.get("application/json")))
                                .build();
                        try (Response createResponse = client.newCall(createRepoRequest).execute()) {
                            String createBodyStr = createResponse.body() != null ? createResponse.body().string() : "";
                            stages.append("repo_post=").append(createResponse.code()).append(" ");
                            if (createResponse.isSuccessful() && !createBodyStr.isEmpty()) {
                                JsonObject created = gson.fromJson(createBodyStr, JsonObject.class);
                                if (created.has("default_branch") && !created.get("default_branch").isJsonNull()) {
                                    defaultBranch = created.get("default_branch").getAsString();
                                }
                            } else if (!createResponse.isSuccessful() && createResponse.code() != 422) {
                                String err = createBodyStr.length() > 300 ? createBodyStr.substring(0, 300) + "..." : createBodyStr;
                                mainHandler.post(() -> callback.onError("Repo creation failed: " + err, stages.toString()));
                                return;
                            }
                            // 422 => المستودع موجود فعلاً؛ سيُحسم فرعه عبر ref_get أدناه
                        }
                    } else if (response.isSuccessful() && response.body() != null) {
                        JsonObject repoJson = gson.fromJson(response.body().string(), JsonObject.class);
                        if (repoJson.has("default_branch") && !repoJson.get("default_branch").isJsonNull()) {
                            defaultBranch = repoJson.get("default_branch").getAsString();
                        }
                    }
                }
                stages.append("branch=").append(defaultBranch).append("\n");

                // جلب الفرع الأساسي مع إعادة محاولة على 409 (تهيئة backend عابرة بعد الإنشاء)
                String baseSha = null;
                String baseTreeSha = null;
                for (int attempt = 0; attempt < 4; attempt++) {
                    Request getRefRequest = buildRequest(repoUrl + "/git/ref/heads/" + defaultBranch).get().build();
                    try (Response response = client.newCall(getRefRequest).execute()) {
                        if (attempt == 0) stages.append("ref_get=").append(response.code()).append(" ");
                        if (response.isSuccessful() && response.body() != null) {
                            JsonObject refJson = gson.fromJson(response.body().string(), JsonObject.class);
                            baseSha = refJson.getAsJsonObject("object").get("sha").getAsString();
                            Request getCommitRequest = buildRequest(repoUrl + "/git/commits/" + baseSha).get().build();
                            try (Response commitResponse = client.newCall(getCommitRequest).execute()) {
                                if (commitResponse.isSuccessful() && commitResponse.body() != null) {
                                    JsonObject commitJson = gson.fromJson(commitResponse.body().string(), JsonObject.class);
                                    baseTreeSha = commitJson.getAsJsonObject("tree").get("sha").getAsString();
                                }
                            }
                            break; // نجح جلب الفرع
                        } else if (response.code() == 409) {
                            try { Thread.sleep(2000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        } else {
                            break; // 404 أو غيره => فرع غير موجود (repo فارغ)؛ نعالجه بالـ bootstrap
                        }
                    }
                }

                // إن بقي baseSha == null => repo موجود لكن فارغ تماماً (لا commit) => bootstrap بـ README عبر Contents API
                if (baseSha == null) {
                    stages.append("bootstrap=1 ");
                    JsonObject readmeBody = new JsonObject();
                    readmeBody.addProperty("message", "Initial commit");
                    readmeBody.addProperty("content",
                            Base64.encodeToString("# Project\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), Base64.NO_WRAP));
                    Request putReadme = buildRequest(repoUrl + "/contents/README.md")
                            .put(RequestBody.create(readmeBody.toString(), MediaType.get("application/json")))
                            .build();
                    try (Response response = client.newCall(putReadme).execute()) {
                        stages.append("bootstrap_code=").append(response.code()).append(" ");
                        if (response.isSuccessful() && response.body() != null) {
                            JsonObject putJson = gson.fromJson(response.body().string(), JsonObject.class);
                            if (putJson.has("commit") && putJson.getAsJsonObject("commit").has("sha")) {
                                baseSha = putJson.getAsJsonObject("commit").get("sha").getAsString();
                                Request gc = buildRequest(repoUrl + "/git/commits/" + baseSha).get().build();
                                try (Response cr = client.newCall(gc).execute()) {
                                    if (cr.isSuccessful() && cr.body() != null) {
                                        baseTreeSha = gson.fromJson(cr.body().string(), JsonObject.class)
                                                .getAsJsonObject("tree").get("sha").getAsString();
                                    }
                                }
                            }
                        }
                        // إن فشل bootstrap (مثلاً 422 لأن README موجود) نتابع؛ الـ backend قد يكون جاهزاً على أي حال
                    }
                }

                // إنشاء الـ blobs مع إعادة محاولة على 409
                JsonArray treeArray = new JsonArray();
                int blobsOk = 0;
                for (int i = 0; i < filesToUpload.size(); i++) {
                    UploadFile uploadFile = filesToUpload.get(i);
                    File file = uploadFile.file;
                    String relativePath = uploadFile.relativePath;
                    final int index = i + 1;
                    mainHandler.post(() -> callback.onProgress(index, filesToUpload.size(), relativePath));

                    // نستخدم التشفير المناسب حسب نوع الملف (نصي أم ثنائي) لمنع التلف
                    // Use appropriate encoding based on file type (text vs binary) to prevent corruption.
                    String contentBase64 = isBinaryByExtension(file.getName()) ? 
                            encodeBinaryToBase64(file) : encodeFileToBase64(file);
                    
                    JsonObject blobBody = new JsonObject();
                    blobBody.addProperty("content", contentBase64);
                    blobBody.addProperty("encoding", "base64");
                    Request createBlobRequest = buildRequest(repoUrl + "/git/blobs")
                            .post(RequestBody.create(blobBody.toString(), MediaType.get("application/json")))
                            .build();

                    Response response = null;
                    for (int attempt = 0; attempt < 3; attempt++) {
                        if (response != null) response.close();
                        response = client.newCall(createBlobRequest).execute();
                        if (response.code() != 409) break;
                        try { Thread.sleep(1500L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                    try (Response r = response) {
                        if (r != null && r.isSuccessful() && r.body() != null) {
                            JsonObject blobJson = gson.fromJson(r.body().string(), JsonObject.class);
                            JsonObject treeElement = new JsonObject();
                            treeElement.addProperty("path", relativePath);
                            treeElement.addProperty("mode", "100644");
                            treeElement.addProperty("type", "blob");
                            treeElement.addProperty("sha", blobJson.get("sha").getAsString());
                            treeArray.add(treeElement);
                            blobsOk++;
                        } else {
                            int code = r != null ? r.code() : -1;
                            stages.append("blob_err=").append(code).append(" path=").append(relativePath).append(" ");
                            String err = r != null ? getShortError(r) : "null response";
                            mainHandler.post(() -> callback.onError("File upload failed (" + relativePath + "): " + err, stages.toString()));
                            return;
                        }
                    }
                }
                stages.append("blobs=").append(blobsOk).append("/").append(filesToUpload.size()).append("\n");

                mainHandler.post(() -> callback.onProgress(filesToUpload.size(), filesToUpload.size(), "Finalizing commit…"));
                JsonObject treeBody = new JsonObject();
                treeBody.add("tree", treeArray);
                if (baseTreeSha != null) {
                    treeBody.addProperty("base_tree", baseTreeSha);
                }
                
                String newTreeSha;
                Request createTreeRequest = buildRequest(repoUrl + "/git/trees")
                        .post(RequestBody.create(treeBody.toString(), MediaType.get("application/json")))
                        .build();
                try (Response response = client.newCall(createTreeRequest).execute()) {
                    stages.append("tree=").append(response.code()).append(" ");
                    if (response.isSuccessful() && response.body() != null) {
                        newTreeSha = gson.fromJson(response.body().string(), JsonObject.class).get("sha").getAsString();
                    } else {
                        String err = getShortError(response);
                        mainHandler.post(() -> callback.onError("Tree creation failed: " + err, stages.toString()));
                        return;
                    }
                }

                JsonObject commitBody = new JsonObject();
                commitBody.addProperty("message", "Upload project: " + projectTitle);
                commitBody.addProperty("tree", newTreeSha);
                if (baseSha != null) {
                    JsonArray parents = new JsonArray();
                    parents.add(baseSha);
                    commitBody.add("parents", parents);
                }
                
                String newCommitSha;
                Request createCommitRequest = buildRequest(repoUrl + "/git/commits")
                        .post(RequestBody.create(commitBody.toString(), MediaType.get("application/json")))
                        .build();
                try (Response response = client.newCall(createCommitRequest).execute()) {
                    stages.append("commit=").append(response.code()).append("\n");
                    if (response.isSuccessful() && response.body() != null) {
                        newCommitSha = gson.fromJson(response.body().string(), JsonObject.class).get("sha").getAsString();
                    } else {
                        String err = getShortError(response);
                        mainHandler.post(() -> callback.onError("Commit failed: " + err, stages.toString()));
                        return;
                    }
                }

                // Linking Branch (Ref)
                boolean refLinked = false;
                if (baseSha != null) {
                    // PATCH existing ref
                    JsonObject refUpdateBody = new JsonObject();
                    refUpdateBody.addProperty("sha", newCommitSha);
                    refUpdateBody.addProperty("force", false);
                    
                    String refUrl = repoUrl + "/git/refs/heads/" + defaultBranch;
                    Request updateRefRequest = buildRequest(refUrl)
                            .patch(RequestBody.create(refUpdateBody.toString(), MediaType.get("application/json")))
                            .build();
                    try (Response response = client.newCall(updateRefRequest).execute()) {
                        stages.append("ref_patch=").append(response.code()).append(" ");
                        if (response.isSuccessful()) {
                            refLinked = true;
                        }
                    }
                }

                if (!refLinked) {
                    // POST new ref (fallback or new repo)
                    JsonObject createRefBody = new JsonObject();
                    createRefBody.addProperty("ref", "refs/heads/" + defaultBranch);
                    createRefBody.addProperty("sha", newCommitSha);
                    Request createRefRequest = buildRequest(repoUrl + "/git/refs")
                            .post(RequestBody.create(createRefBody.toString(), MediaType.get("application/json")))
                            .build();
                    try (Response response = client.newCall(createRefRequest).execute()) {
                        stages.append("ref_post=").append(response.code()).append(" ");
                        if (response.isSuccessful()) {
                            refLinked = true;
                        } else {
                            String err = getShortError(response);
                            mainHandler.post(() -> callback.onError("Ref linking failed: " + err, stages.toString()));
                            return;
                        }
                    }
                }

                String finalHtmlUrl = "https://github.com/" + login + "/" + repoName;
                mainHandler.post(() -> callback.onSuccess(finalHtmlUrl, stages.toString()));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.toString(), stages.toString()));
            }
        });
    }

    private String getShortError(Response response) {
        try {
            if (response.body() == null) return "null body";
            String body = response.body().string();
            if (body.length() > 300) return body.substring(0, 300) + "...";
            return body;
        } catch (Exception e) {
            return "failed to read error body";
        }
    }

    private Request.Builder buildRequest(String url) {
        return new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + getAccessToken())
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "TAJ-Studio");
    }

    public List<UploadFile> collectUploadFiles(File projectRoot) {
        List<UploadFile> result = new ArrayList<>();
        if (projectRoot != null && projectRoot.exists() && projectRoot.isDirectory()) {
            collectFilesInternal(projectRoot, projectRoot, result);
        }
        return result;
    }

    private void collectFilesInternal(File root, File dir, List<UploadFile> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (file.isDirectory()) {
                if (name.equals("build") || name.equals(".gradle") || name.equals(".idea") || 
                    name.equals(".git") || name.equals("bin") || name.equals("gen") || name.equals(".ds_store")) {
                    continue;
                }
                collectFilesInternal(root, file, result);
            } else {
                if (name.endsWith(".apk") || name.endsWith(".class") || name.endsWith(".dex") || 
                    name.endsWith(".o") || name.endsWith(".so") || name.equals(".ds_store") ||
                    file.length() > 25 * 1024 * 1024) {
                    continue;
                }
                String relativePath = root.toURI().relativize(file.toURI()).getPath();
                result.add(new UploadFile(relativePath, file));
            }
        }
    }

    private void collectFiles(File root, File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (file.isDirectory()) {
                if (name.equals("build") || name.equals(".gradle") || name.equals(".idea") || 
                    name.equals(".git") || name.equals("bin") || name.equals("gen") || name.equals(".ds_store")) {
                    continue;
                }
                collectFiles(root, file, result);
            } else {
                if (name.endsWith(".apk") || name.endsWith(".class") || name.endsWith(".dex") || 
                    name.endsWith(".o") || name.endsWith(".so") || name.equals(".ds_store") ||
                    file.length() > 25 * 1024 * 1024) {
                    continue;
                }
                result.add(file);
            }
        }
    }

    private String sanitizeRepoName(String title) {
        String sanitized = title.replaceAll("[^a-zA-Z0-9._-]", "-");
        return sanitized.isEmpty() ? "project" : sanitized;
    }

    /**
     * Reads file content as UTF-8 text, sanitizes invalid characters, and encodes to Base64.
     * This prevents 422 errors from GitHub when files contain non-standard UTF-8 bytes
     * (common in Sketchware-generated .java files with special characters or BOM markers).
     *
     * يقرأ محتوى الملف كنص UTF-8، وينظّف الأحرف غير الصالحة، ثم يشفّره إلى Base64.
     * هذا يمنع خطأ 422 من GitHub عندما تحتوي الملفات على بايتات UTF-8 غير قياسية
     * (شائع في ملفات .java المولَّدة بواسطة Sketchware والتي قد تحوي أحرفاً خاصة أو علامات BOM).
     */
    private String encodeFileToBase64(File file) throws IOException {
        // Read raw bytes first
        byte[] rawBytes = new byte[(int) file.length()];
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            int read = fis.read(rawBytes);
            if (read != rawBytes.length) {
                // File changed during read; retry with actual size
                rawBytes = java.util.Arrays.copyOf(rawBytes, read);
            }
        }

        // Try to decode as UTF-8, replacing invalid sequences with replacement char
        String content = new String(rawBytes, java.nio.charset.StandardCharsets.UTF_8);

        // Sanitize: remove null bytes and other control chars that GitHub rejects
        // Keep newlines (\n, \r), tabs (\t), but remove chars < 0x20 except those three
        StringBuilder sanitized = new StringBuilder(content.length());
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                sanitized.append(c);
            } else if (c < 0x20 || c == 0x7F) {
                // Skip control characters GitHub rejects
                continue;
            } else {
                sanitized.append(c);
            }
        }

        // Encode the sanitized string back to UTF-8 bytes, then Base64
        byte[] cleanBytes = sanitized.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return Base64.encodeToString(cleanBytes, Base64.NO_WRAP);
    }

    /**
     * يشفّر الملفات الثنائية (مثل الصور والخطوط) خاماً كما هي لضمان عدم تلفها.
     * Encodes binary files (images, fonts) raw as-is to prevent corruption.
     */
    private String encodeBinaryToBase64(File file) throws IOException {
        byte[] bytes = new byte[(int) file.length()];
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            int read = fis.read(bytes);
            if (read != bytes.length) {
                bytes = java.util.Arrays.copyOf(bytes, read);
            }
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    /**
     * يحدد ما إذا كان الملف ثنائياً بناءً على امتداده لاختيار طريقة التشفير المناسبة.
     * Determines if a file is binary by extension to choose the correct encoding method.
     */
    public static boolean isBinaryByExtension(String name) {
        if (name == null) return false;
        String ext = name.toLowerCase();
        return ext.endsWith(".png") || ext.endsWith(".jpg") || ext.endsWith(".jpeg") ||
               ext.endsWith(".gif") || ext.endsWith(".webp") || ext.endsWith(".bmp") ||
               ext.endsWith(".ico") || ext.endsWith(".ttf") || ext.endsWith(".otf") ||
               ext.endsWith(".woff") || ext.endsWith(".woff2") || ext.endsWith(".mp3") ||
               ext.endsWith(".mp4") || ext.endsWith(".wav") || ext.endsWith(".ogg") ||
               ext.endsWith(".zip") || ext.endsWith(".pdf");
    }

    public static class UserResponse {
        @SerializedName("login") public String login;
        @SerializedName("avatar_url") public String avatarUrl;
        @SerializedName("html_url") public String htmlUrl;
    }
}
