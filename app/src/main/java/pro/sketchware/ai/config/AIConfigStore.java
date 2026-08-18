package pro.sketchware.ai.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import pro.sketchware.ai.core.Protocol;
import pro.sketchware.ai.core.ProviderProfile;

/**
 * Persistent TAG Assistant configuration. API keys live in
 * EncryptedSharedPreferences when the device allows it, with a plain
 * SharedPreferences fallback so the feature never crashes the host app.
 * Keys are never written to logs.
 */
public final class AIConfigStore {

    private static final String TAG = "AIConfigStore";
    private static final String PREFS_NAME = "tag_assistant_config";
    private static final String KEY_ENABLED = "assistant_enabled";
    private static final String KEY_DEFAULT_MODE = "default_mode";
    private static final String KEY_SELECTED_PROVIDER = "selected_provider";
    private static final String KEY_CUSTOM_PROVIDERS = "custom_providers";

    public static final String MODE_CHAT = "chat";
    public static final String MODE_AGENT = "agent";

    private static volatile AIConfigStore instance;

    private final SharedPreferences prefs;

    private AIConfigStore(Context context) {
        SharedPreferences candidate = null;
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            candidate = EncryptedSharedPreferences.create(
                    PREFS_NAME,
                    masterKeyAlias,
                    context.getApplicationContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            // Isolation principle: encryption must never break the feature.
            Log.w(TAG, "EncryptedSharedPreferences unavailable, using plain fallback");
        }
        if (candidate == null) {
            try {
                candidate = context.getApplicationContext()
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            } catch (Exception e) {
                Log.e(TAG, "Failed to open any preferences store");
            }
        }
        prefs = candidate;
    }

    public static AIConfigStore getInstance(Context context) {
        if (instance == null) {
            synchronized (AIConfigStore.class) {
                if (instance == null) {
                    instance = new AIConfigStore(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    // ------------------------------------------------------------------
    // Master switches
    // ------------------------------------------------------------------

    public boolean isAssistantEnabled() {
        return prefs == null || prefs.getBoolean(KEY_ENABLED, true);
    }

    public void setAssistantEnabled(boolean enabled) {
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
        }
    }

    public String getDefaultMode() {
        return prefs == null ? MODE_CHAT : prefs.getString(KEY_DEFAULT_MODE, MODE_CHAT);
    }

    public void setDefaultMode(String mode) {
        if (prefs != null) {
            prefs.edit().putString(KEY_DEFAULT_MODE, MODE_AGENT.equals(mode) ? MODE_AGENT : MODE_CHAT).apply();
        }
    }

    // ------------------------------------------------------------------
    // Provider selection and per-provider settings
    // ------------------------------------------------------------------

    public String getSelectedProviderId() {
        return prefs == null ? "openai" : prefs.getString(KEY_SELECTED_PROVIDER, "openai");
    }

    public void setSelectedProviderId(String providerId) {
        if (prefs != null && providerId != null) {
            prefs.edit().putString(KEY_SELECTED_PROVIDER, providerId).apply();
        }
    }

    public String getApiKey(String providerId) {
        return prefs == null ? "" : prefs.getString("api_key_" + providerId, "");
    }

    public void setApiKey(String providerId, String key) {
        if (prefs != null) {
            prefs.edit().putString("api_key_" + providerId, key == null ? "" : key.trim()).apply();
        }
    }

    public String getBaseUrl(String providerId) {
        return prefs == null ? "" : prefs.getString("base_url_" + providerId, "");
    }

    public void setBaseUrl(String providerId, String url) {
        if (prefs != null) {
            prefs.edit().putString("base_url_" + providerId, url == null ? "" : url.trim()).apply();
        }
    }

    public String getModel(String providerId) {
        return prefs == null ? "" : prefs.getString("model_" + providerId, "");
    }

    public void setModel(String providerId, String model) {
        if (prefs != null) {
            prefs.edit().putString("model_" + providerId, model == null ? "" : model.trim()).apply();
        }
    }

    // ------------------------------------------------------------------
    // Custom (user-created) provider profiles
    // ------------------------------------------------------------------

    public java.util.List<ProviderProfile> getCustomProviders() {
        java.util.List<ProviderProfile> result = new java.util.ArrayList<>();
        if (prefs == null) {
            return result;
        }
        String raw = prefs.getString(KEY_CUSTOM_PROVIDERS, "");
        if (raw.isEmpty()) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json == null) {
                    continue;
                }
                ProviderProfile profile = profileFromJson(json);
                if (profile != null) {
                    result.add(profile);
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "Custom provider list is corrupt, starting fresh");
        }
        return result;
    }

    public void addCustomProvider(ProviderProfile profile) {
        if (prefs == null || profile == null) {
            return;
        }
        java.util.List<ProviderProfile> profiles = getCustomProviders();
        profiles.removeIf(existing -> existing.id.equals(profile.id));
        profiles.add(profile);
        saveCustomProviders(profiles);
    }

    public void removeCustomProvider(String id) {
        if (prefs == null) {
            return;
        }
        java.util.List<ProviderProfile> profiles = getCustomProviders();
        profiles.removeIf(existing -> existing.id.equals(id));
        saveCustomProviders(profiles);
    }

    private void saveCustomProviders(java.util.List<ProviderProfile> profiles) {
        JSONArray array = new JSONArray();
        for (ProviderProfile profile : profiles) {
            array.put(profileToJson(profile));
        }
        prefs.edit().putString(KEY_CUSTOM_PROVIDERS, array.toString()).apply();
    }

    private static JSONObject profileToJson(ProviderProfile profile) {
        JSONObject json = new JSONObject();
        try {
            json.put("id", profile.id);
            json.put("displayName", profile.displayName);
            json.put("protocol", profile.protocol.name());
            json.put("baseUrl", profile.defaultBaseUrl);
            json.put("requiresKey", profile.requiresKey);
            json.put("models", new JSONArray(profile.defaultModelSuggestions));
        } catch (JSONException ignored) {
            // Static string values cannot fail to serialize.
        }
        return json;
    }

    private static ProviderProfile profileFromJson(JSONObject json) {
        try {
            Protocol protocol = Protocol.valueOf(json.optString("protocol", Protocol.OPENAI_COMPATIBLE.name()));
            JSONArray modelsJson = json.optJSONArray("models");
            String[] models = new String[modelsJson == null ? 0 : modelsJson.length()];
            for (int i = 0; i < models.length; i++) {
                models[i] = modelsJson.optString(i, "");
            }
            return new ProviderProfile.Builder(json.optString("id"), json.optString("displayName"), protocol)
                    .baseUrl(json.optString("baseUrl", ""))
                    .requiresKey(json.optBoolean("requiresKey", false))
                    .models(models)
                    .custom(true)
                    .build();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Masks a key for safe display (first 4 + last 4 chars). */
    public static String maskKey(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "…" + key.substring(key.length() - 4);
    }
}
