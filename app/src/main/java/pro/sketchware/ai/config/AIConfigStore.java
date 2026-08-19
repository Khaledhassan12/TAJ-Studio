package pro.sketchware.ai.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.core.ModelItem;
import pro.sketchware.ai.core.Protocol;
import pro.sketchware.ai.core.ProviderProfile;

/**
 * Persistent TAG Assistant configuration. API keys live in
 * EncryptedSharedPreferences when the device allows it, with a plain
 * SharedPreferences fallback so the feature never crashes the host app.
 * Keys are sanitized on write and on read; they are never written to logs.
 * Per-provider model caches (id + optional pricing flag + sync timestamp)
 * are persisted here too.
 */
public final class AIConfigStore {

    private static final String TAG = "AIConfigStore";
    private static final String PREFS_NAME = "tag_assistant_config";
    private static final String KEY_ENABLED = "assistant_enabled";
    private static final String KEY_VERIFIED = "assistant_verified";
    private static final String KEY_DEFAULT_MODE = "default_mode";
    private static final String KEY_SELECTED_PROVIDER = "selected_provider";
    private static final String KEY_CUSTOM_PROVIDERS = "custom_providers";

    public static final String K_ENABLED = "assistant_enabled";
    public static final String K_PROVIDER = "assistant_provider";
    public static final String K_KEY = "assistant_key";
    public static final String K_BASE = "assistant_base_url";
    public static final String K_MODEL = "assistant_model";
    public static final String K_MODE = "assistant_mode";
    public static final String K_SHOW_THINKING = "assistant_show_thinking";

    private static final String MODELS_CACHE_PREFIX = "models_cache_";
    private static final String MODELS_CACHE_TS_PREFIX = "models_cache_ts_";
    private static final long MODELS_CACHE_TTL_MS = 24 * 60 * 60 * 1000L;

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
        migrateOldKeys();
    }

    private void migrateOldKeys() {
        if (prefs == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        String currentProvider = getSelectedProviderId();

        // 1. Provider
        if (!prefs.contains(K_PROVIDER) && prefs.contains(KEY_SELECTED_PROVIDER)) {
            editor.putString(K_PROVIDER, prefs.getString(KEY_SELECTED_PROVIDER, "openai"));
        }

        // 2. Key
        if (!prefs.contains(K_KEY) && prefs.contains("api_key_" + currentProvider)) {
            editor.putString(K_KEY, prefs.getString("api_key_" + currentProvider, ""));
        }

        // 3. Base URL
        if (!prefs.contains(K_BASE) && prefs.contains("base_url_" + currentProvider)) {
            editor.putString(K_BASE, prefs.getString("base_url_" + currentProvider, ""));
        }

        // 4. Model
        if (!prefs.contains(K_MODEL) && prefs.contains("model_" + currentProvider)) {
            editor.putString(K_MODEL, prefs.getString("model_" + currentProvider, ""));
        }

        // 5. Mode
        if (!prefs.contains(K_MODE) && prefs.contains(KEY_DEFAULT_MODE)) {
            editor.putString(K_MODE, prefs.getString(KEY_DEFAULT_MODE, MODE_CHAT));
        }

        editor.apply();
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

    public String safeKey() {
        String k = getKey();
        if (k == null) return "";
        k = k.trim();
        if (k.startsWith("\"") && k.endsWith("\"") && k.length() > 1) {
            k = k.substring(1, k.length() - 1);
        }
        return k.replaceAll("[\\r\\n]", "");
    }

    public String safeBaseUrl(String fallback) {
        String u = getBaseUrl();
        if (u == null || u.trim().isEmpty()) u = fallback;
        u = u.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    public String safeModel() {
        String m = getModel();
        return m == null ? "" : m.trim();
    }

    public boolean isEnabledAndConfigured() {
        String m = safeModel();
        if (!isAssistantEnabled() || m.isEmpty()) return false;
        String k = safeKey();
        boolean keyRequired = requiresKeyFor(getSelectedProviderId());
        return !keyRequired || !k.isEmpty();
    }

    public boolean requiresKeyFor(String providerId) {
        return providerId == null || !(providerId.equals("ollama") || providerId.equals("custom"));
    }

    public boolean isEnabledAndVerified() {
        return isAssistantEnabled() && isVerified() && !safeModel().isEmpty();
    }

    // ------------------------------------------------------------------
    // Key / URL sanitization (applied on save AND on use)
    // ------------------------------------------------------------------

    /**
     * Normalizes a pasted API key: trims whitespace (including \r\n), strips
     * surrounding quotes and rejects empty results.
     */
    public static String sanitizeKey(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        while (value.length() > 0) {
            char first = value.charAt(0);
            if (first == '"' || first == '\'' || Character.isWhitespace(first)) {
                value = value.substring(1);
            } else {
                break;
            }
        }
        while (value.length() > 0) {
            char last = value.charAt(value.length() - 1);
            if (last == '"' || last == '\'' || Character.isWhitespace(last)) {
                value = value.substring(0, value.length() - 1);
            } else {
                break;
            }
        }
        return value.trim();
    }

    /** Trims a base URL and removes any trailing slashes (no other rewriting). */
    public static String sanitizeBaseUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
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

    public boolean isVerified() {
        return prefs != null && prefs.getBoolean(KEY_VERIFIED, false);
    }

    public void setVerified(boolean verified) {
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_VERIFIED, verified).apply();
        }
    }

    public boolean isShowThinking() {
        return prefs == null || prefs.getBoolean(K_SHOW_THINKING, true);
    }

    public void setShowThinking(boolean show) {
        if (prefs != null) {
            prefs.edit().putBoolean(K_SHOW_THINKING, show).apply();
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

    public String getProviderId() {
        return prefs == null ? "openrouter" : prefs.getString(K_PROVIDER, "openrouter");
    }

    public void setProviderId(String id) {
        if (prefs != null && id != null) {
            prefs.edit().putString(K_PROVIDER, id.trim()).putBoolean(KEY_VERIFIED, false).apply();
        }
    }

    public String getKey() {
        return prefs == null ? "" : prefs.getString(K_KEY, "");
    }

    public void setKey(String key) {
        if (prefs != null) {
            prefs.edit().putString(K_KEY, sanitizeKey(key)).putBoolean(KEY_VERIFIED, false).apply();
        }
    }

    public String getBaseUrl() {
        return prefs == null ? "" : prefs.getString(K_BASE, "");
    }

    public void setBaseUrl(String url) {
        if (prefs != null) {
            prefs.edit().putString(K_BASE, sanitizeBaseUrl(url)).putBoolean(KEY_VERIFIED, false).apply();
        }
    }

    public String getModel() {
        return prefs == null ? "" : prefs.getString(K_MODEL, "");
    }

    public void setModel(String model) {
        if (prefs != null) {
            prefs.edit().putString(K_MODEL, model == null ? "" : model.trim()).putBoolean(KEY_VERIFIED, false).apply();
        }
    }

    public String getSelectedProviderId() {
        return getProviderId();
    }

    public void setSelectedProviderId(String providerId) {
        setProviderId(providerId);
    }

    public String getApiKey(String providerId) {
        return getKey();
    }

    public void setApiKey(String providerId, String key) {
        setKey(key);
    }

    public String getBaseUrl(String providerId) {
        return getBaseUrl();
    }

    public void setBaseUrl(String providerId, String url) {
        setBaseUrl(url);
    }

    public String getModel(String providerId) {
        return getModel();
    }

    public void setModel(String providerId, String model) {
        setModel(model);
    }
    // ------------------------------------------------------------------
    // Per-provider model cache (id + optional pricing + sync timestamp)
    // ------------------------------------------------------------------

    public void saveModelsCache(String providerId, List<ModelItem> items) {
        if (prefs == null || providerId == null) {
            return;
        }
        JSONArray array = new JSONArray();
        if (items != null) {
            for (ModelItem item : items) {
                array.put(item.toJson());
            }
        }
        prefs.edit()
                .putString(MODELS_CACHE_PREFIX + providerId, array.toString())
                .putLong(MODELS_CACHE_TS_PREFIX + providerId, System.currentTimeMillis())
                .apply();
    }

    public List<ModelItem> loadModelsCache(String providerId) {
        List<ModelItem> result = new ArrayList<>();
        if (prefs == null || providerId == null) {
            return result;
        }
        String raw = prefs.getString(MODELS_CACHE_PREFIX + providerId, "");
        if (raw.isEmpty()) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json != null) {
                    result.add(ModelItem.fromJson(json));
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "Model cache for " + providerId + " is corrupt, ignoring");
        }
        return result;
    }

    /** Wall-clock epoch millis of the last successful model sync for this provider (0 = never). */
    public long getModelsCacheTimestamp(String providerId) {
        return prefs == null ? 0L : prefs.getLong(MODELS_CACHE_TS_PREFIX + providerId, 0L);
    }

    /** True when the cache exists AND was synced within the last 24 hours. */
    public boolean isModelsCacheFresh(String providerId) {
        long timestamp = getModelsCacheTimestamp(providerId);
        return timestamp > 0L && System.currentTimeMillis() - timestamp < MODELS_CACHE_TTL_MS;
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
    public static String maskKey(String k) {
        if (k == null || k.isEmpty()) return "EMPTY";
        if (k.length() <= 6) return k.charAt(0) + "…";
        return k.substring(0, 4) + "…" + k.substring(k.length() - 2);
    }
}
