package pro.sketchware.github;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
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

    public static class UserResponse {
        @SerializedName("login") public String login;
        @SerializedName("avatar_url") public String avatarUrl;
        @SerializedName("html_url") public String htmlUrl;
    }
}
