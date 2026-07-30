package pro.sketchware.github;

import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.ArrayList;
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
        void onSuccess(String repoHtmlUrl);
        void onError(String error);
    }

    private GitHubManager(Context context) {
        context = context.getApplicationContext();
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
        notifyListeners();
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
            if (token == null || login.equals("Unknown")) {
                mainHandler.post(() -> callback.onError("Not signed in."));
                return;
            }

            if (projectRoot == null || !projectRoot.exists() || !projectRoot.isDirectory()) {
                mainHandler.post(() -> callback.onError("Project folder not found."));
                return;
            }

            try {
                mainHandler.post(() -> callback.onProgress(0, 0, "Preparing files…"));
                List<File> filesToUpload = new ArrayList<>();
                collectFiles(projectRoot, projectRoot, filesToUpload);

                if (filesToUpload.isEmpty()) {
                    mainHandler.post(() -> callback.onError("No files to upload."));
                    return;
                }

                String repoName = sanitizeRepoName(projectTitle);
                String repoUrl = "https://api.github.com/repos/" + login + "/" + repoName;
                
                mainHandler.post(() -> callback.onProgress(0, filesToUpload.size(), "Checking repository…"));
                
                String defaultBranch = "main";
                Request getRepoRequest = buildRequest(repoUrl).get().build();
                try (Response response = client.newCall(getRepoRequest).execute()) {
                    if (response.code() == 404) {
                        mainHandler.post(() -> callback.onProgress(0, filesToUpload.size(), "Creating repository…"));
                        JsonObject createRepoBody = new JsonObject();
                        createRepoBody.addProperty("name", repoName);
                        createRepoBody.addProperty("private", false);
                        createRepoBody.addProperty("auto_init", false);
                        
                        Request createRepoRequest = buildRequest("https://api.github.com/user/repos")
                                .post(RequestBody.create(createRepoBody.toString(), MediaType.get("application/json")))
                                .build();
                        try (Response createResponse = client.newCall(createRepoRequest).execute()) {
                            if (!createResponse.isSuccessful() && createResponse.code() != 422) {
                                mainHandler.post(() -> callback.onError("Failed to create repository: " + createResponse.code()));
                                return;
                            }
                        }
                    } else if (response.isSuccessful() && response.body() != null) {
                        JsonObject repoJson = gson.fromJson(response.body().string(), JsonObject.class);
                        defaultBranch = repoJson.get("default_branch").getAsString();
                    }
                }

                String baseSha = null;
                String baseTreeSha = null;
                Request getRefRequest = buildRequest(repoUrl + "/git/ref/heads/" + defaultBranch).get().build();
                try (Response response = client.newCall(getRefRequest).execute()) {
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
                    }
                }

                JsonArray treeArray = new JsonArray();
                for (int i = 0; i < filesToUpload.size(); i++) {
                    File file = filesToUpload.get(i);
                    String relativePath = projectRoot.toURI().relativize(file.toURI()).getPath();
                    final int index = i + 1;
                    mainHandler.post(() -> callback.onProgress(index, filesToUpload.size(), relativePath));

                    String contentBase64 = encodeFileToBase64(file);
                    JsonObject blobBody = new JsonObject();
                    blobBody.addProperty("content", contentBase64);
                    blobBody.addProperty("encoding", "base64");
                    
                    Request createBlobRequest = buildRequest(repoUrl + "/git/blobs")
                            .post(RequestBody.create(blobBody.toString(), MediaType.get("application/json")))
                            .build();
                    try (Response response = client.newCall(createBlobRequest).execute()) {
                        if (response.isSuccessful() && response.body() != null) {
                            JsonObject blobJson = gson.fromJson(response.body().string(), JsonObject.class);
                            JsonObject treeElement = new JsonObject();
                            treeElement.addProperty("path", relativePath);
                            treeElement.addProperty("mode", "100644");
                            treeElement.addProperty("type", "blob");
                            treeElement.addProperty("sha", blobJson.get("sha").getAsString());
                            treeArray.add(treeElement);
                        } else {
                            mainHandler.post(() -> callback.onError("Failed to upload file " + relativePath + ": " + response.code()));
                            return;
                        }
                    }
                }

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
                    if (response.isSuccessful() && response.body() != null) {
                        newTreeSha = gson.fromJson(response.body().string(), JsonObject.class).get("sha").getAsString();
                    } else {
                        mainHandler.post(() -> callback.onError("Failed to create tree: " + response.code()));
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
                    if (response.isSuccessful() && response.body() != null) {
                        newCommitSha = gson.fromJson(response.body().string(), JsonObject.class).get("sha").getAsString();
                    } else {
                        mainHandler.post(() -> callback.onError("Failed to create commit: " + response.code()));
                        return;
                    }
                }

                JsonObject refUpdateBody = new JsonObject();
                refUpdateBody.addProperty("sha", newCommitSha);
                refUpdateBody.addProperty("force", false);
                
                String refUrl = repoUrl + "/git/refs/heads/" + defaultBranch;
                Request updateRefRequest = buildRequest(refUrl)
                        .patch(RequestBody.create(refUpdateBody.toString(), MediaType.get("application/json")))
                        .build();
                try (Response response = client.newCall(updateRefRequest).execute()) {
                    if (response.code() == 422 && baseSha == null) {
                        JsonObject createRefBody = new JsonObject();
                        createRefBody.addProperty("ref", "refs/heads/" + defaultBranch);
                        createRefBody.addProperty("sha", newCommitSha);
                        Request createRefRequest = buildRequest(repoUrl + "/git/refs")
                                .post(RequestBody.create(createRefBody.toString(), MediaType.get("application/json")))
                                .build();
                        try (Response createRefResponse = client.newCall(createRefRequest).execute()) {
                            if (!createRefResponse.isSuccessful()) {
                                mainHandler.post(() -> callback.onError("Failed to create ref: " + createRefResponse.code()));
                                return;
                            }
                        }
                    } else if (!response.isSuccessful()) {
                        mainHandler.post(() -> callback.onError("Failed to update ref: " + response.code()));
                        return;
                    }
                }

                String finalHtmlUrl = "https://github.com/" + login + "/" + repoName;
                mainHandler.post(() -> callback.onSuccess(finalHtmlUrl));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private Request.Builder buildRequest(String url) {
        return new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + getAccessToken())
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "TAJ-Studio");
    }

    private void collectFiles(File root, File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String relativePath = root.toURI().relativize(file.toURI()).getPath();
            if (file.isDirectory()) {
                if (relativePath.startsWith("build/") || relativePath.startsWith(".gradle/") || 
                    relativePath.startsWith(".idea/") || relativePath.contains("/.git/")) {
                    continue;
                }
                collectFiles(root, file, result);
            } else {
                if (relativePath.endsWith(".apk") || file.length() > 25 * 1024 * 1024) {
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

    private String encodeFileToBase64(File file) throws IOException {
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(bytes);
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    public static class UserResponse {
        @SerializedName("login") public String login;
        @SerializedName("avatar_url") public String avatarUrl;
        @SerializedName("html_url") public String htmlUrl;
    }
}
