package pro.sketchware.ai.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * [WHAT] Secure storage for AI API keys and tokens.
 * [WHY] Protects sensitive credentials (OpenAI, Anthropic, Gemini, HF) from plaintext exposure (RISK-4).
 * [HOW] Uses AndroidX EncryptedSharedPreferences with AES256_GCM.
 *
 * [العربية]
 * مخزن آمن لمفاتيح ورموز الذكاء الاصطناعي.
 * يحمي البيانات الحساسة من الظهور بنص صريح باستخدام التشفير.
 */
public class SecureKeyStore {

    private static final String TAG = "SecureKeyStore";
    private static final String PREF_NAME = "ai_keys_secure";

    private static SecureKeyStore instance;
    private SharedPreferences prefs;
    private boolean isUnavailable = false;

    public static synchronized SecureKeyStore get(Context context) {
        if (instance == null) {
            instance = new SecureKeyStore(context.getApplicationContext());
        }
        return instance;
    }

    private SecureKeyStore(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            prefs = EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences. Secrets will be unavailable.", e);
            isUnavailable = true;
        }
    }

    public void putKey(String provider, String key) {
        if (isUnavailable) {
            Log.w(TAG, "SecureKeyStore unavailable. putKey ignored for provider: " + provider);
            return;
        }
        if (key == null) {
            removeKey(provider);
            return;
        }
        prefs.edit().putString("key_" + provider, key).apply();
    }

    public String getKey(String provider) {
        if (isUnavailable) return null;
        return prefs.getString("key_" + provider, null);
    }

    public void removeKey(String provider) {
        if (isUnavailable) return;
        prefs.edit().remove("key_" + provider).apply();
    }

    public boolean hasKey(String provider) {
        if (isUnavailable) return false;
        return prefs.contains("key_" + provider);
    }

    public void putHfToken(String token) {
        if (isUnavailable) {
            Log.w(TAG, "SecureKeyStore unavailable. putHfToken ignored.");
            return;
        }
        prefs.edit().putString("hf_token", token).apply();
    }

    public String getHfToken() {
        if (isUnavailable) return null;
        return prefs.getString("hf_token", null);
    }

    public void removeHfToken() {
        if (isUnavailable) return;
        prefs.edit().remove("hf_token").apply();
    }
}
