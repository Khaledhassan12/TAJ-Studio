package pro.sketchware.ai.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.IOException;
import java.security.GeneralSecurityException;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    private final Gson gson = new Gson();

    public static class KeyEntry {
        public String id;
        public String name;
        public String key;

        public KeyEntry(String id, String name, String key) {
            this.id = id;
            this.name = name;
            this.key = key;
        }
    }

    public static class KeyInfo {
        public String id;
        public String name;
        public KeyInfo(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

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

    // --- Multi-key Support ---

    public void addKey(String provider, String name, String key) {
        if (isUnavailable) return;
        List<KeyEntry> keys = loadKeys(provider);
        keys.add(new KeyEntry(UUID.randomUUID().toString(), name, key));
        saveKeys(provider, keys);
    }

    public List<KeyInfo> listKeyNames(String provider) {
        List<KeyInfo> infos = new ArrayList<>();
        if (isUnavailable) return infos;
        for (KeyEntry entry : loadKeys(provider)) {
            infos.add(new KeyInfo(entry.id, entry.name));
        }
        return infos;
    }

    public String getKeyForUse(String provider, String keyIdOrNull) {
        if (isUnavailable) return null;
        List<KeyEntry> keys = loadKeys(provider);
        if (keys.isEmpty()) {
            // Fallback to legacy single key if migration hasn't happened
            return getKey(provider);
        }
        if (keyIdOrNull == null) return keys.get(0).key;
        for (KeyEntry entry : keys) {
            if (entry.id.equals(keyIdOrNull)) return entry.key;
        }
        return null;
    }

    public void removeKey(String provider, String keyId) {
        if (isUnavailable) return;
        List<KeyEntry> keys = loadKeys(provider);
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).id.equals(keyId)) {
                keys.remove(i);
                break;
            }
        }
        saveKeys(provider, keys);
    }

    public int keyCount(String provider) {
        if (isUnavailable) return 0;
        List<KeyEntry> keys = loadKeys(provider);
        if (keys.isEmpty() && hasKey(provider)) return 1; // Count legacy key
        return keys.size();
    }

    private List<KeyEntry> loadKeys(String provider) {
        String json = prefs.getString("keys_" + provider, null);
        if (json == null) return new ArrayList<>();
        return gson.fromJson(json, new TypeToken<List<KeyEntry>>(){}.getType());
    }

    private void saveKeys(String provider, List<KeyEntry> keys) {
        prefs.edit().putString("keys_" + provider, gson.toJson(keys)).apply();
    }

    // --- Legacy Single Key (still encrypted, keep working for migration) ---

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
        // Check both legacy and multi-key
        return prefs.contains("key_" + provider) || !loadKeys(provider).isEmpty();
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

    // --- Backup/Restore (P2-DC) ---

    public String dumpKeys() {
        if (isUnavailable) return "{}";
        JSONObject json = new JSONObject();
        try {
            for (String key : prefs.getAll().keySet()) {
                Object val = prefs.getAll().get(key);
                if (val != null) json.put(key, val);
            }
        } catch (JSONException ignored) {}
        return json.toString();
    }

    public void restoreKeys(String json) {
        if (isUnavailable || json == null) return;
        try {
            JSONObject obj = new JSONObject(json);
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object val = obj.opt(key);
                if (val instanceof String) editor.putString(key, (String) val);
                else if (val instanceof Boolean) editor.putBoolean(key, (Boolean) val);
                else if (val instanceof Integer) editor.putInt(key, (Integer) val);
                else if (val instanceof Long) editor.putLong(key, (Long) val);
            }
            editor.apply();
        } catch (JSONException ignored) {}
    }
}


