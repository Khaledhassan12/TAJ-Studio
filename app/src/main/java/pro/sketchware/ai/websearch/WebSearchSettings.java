package pro.sketchware.ai.websearch;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import pro.sketchware.ai.data.AiStorage;

/**
 * [WHAT] SSOT for Web Search settings.
 * [WHY] P2-WS: Wraps AiStorage kv for typed access (R5/R16); single writer per field.
 * [HOW] Singleton; strictly derived from persisted storage; num_results clamped 1..10.
 *
 * [العربية]
 * المصدر الوحيد لإعدادات بحث الويب.
 * يغلف مفاتيح AiStorage بوصول مُنمّط مع كاتب واحد لكل حقل وضبط عدد النتائج بين 1 و10.
 */
public class WebSearchSettings {

    public static final int MIN_RESULTS = 1;
    public static final int MAX_RESULTS = 10;
    public static final int DEFAULT_NUM_RESULTS = 5;
    public static final String DEFAULT_PROVIDER = "brave";

    // Provider constants
    public static final String PROVIDER_BRAVE = "brave";
    public static final String PROVIDER_KAGI = "kagi";
    public static final String PROVIDER_SERPER = "serper";
    public static final String PROVIDER_TAVILY = "tavily";
    public static final String PROVIDER_SEARXNG = "searxng";
    public static final String PROVIDER_DUCKDUCKGO = "duckduckgo";

    private static WebSearchSettings instance;
    private final AiStorage storage;

    private WebSearchSettings(Context context) {
        this.storage = AiStorage.get(context);
    }

    public static synchronized WebSearchSettings get(Context context) {
        if (instance == null) {
            instance = new WebSearchSettings(context.getApplicationContext());
        }
        return instance;
    }

    // --- Enabled ---

    public boolean isEnabled() {
        return storage.isWebSearchEnabled();
    }

    public void setEnabled(boolean enabled) {
        storage.setWebSearchEnabled(enabled);
    }

    // --- Provider ---

    public String getProvider() {
        String val = storage.getWebSearchProvider();
        return val != null ? val : DEFAULT_PROVIDER;
    }

    public void setProvider(String provider) {
        storage.setWebSearchProvider(provider);
    }

    // --- API Keys (JSON map: provider -> key) ---

    public String getKey(String provider) {
        String keysJson = storage.getWebSearchKeysJson();
        if (keysJson == null || keysJson.isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(keysJson);
            return obj.optString(provider, null);
        } catch (JSONException e) {
            return null;
        }
    }

    public void setKey(String provider, String key) {
        String keysJson = storage.getWebSearchKeysJson();
        JSONObject obj;
        try {
            obj = (keysJson != null && !keysJson.isEmpty()) ? new JSONObject(keysJson) : new JSONObject();
        } catch (JSONException e) {
            obj = new JSONObject();
        }
        try {
            if (key == null || key.isEmpty()) {
                obj.remove(provider);
            } else {
                obj.put(provider, key);
            }
            storage.setWebSearchKeysJson(obj.toString());
        } catch (JSONException e) {
            // Ignore
        }
    }

    // --- SearXNG URL ---

    public String getSearxngUrl() {
        String val = storage.getWebSearchSearxngUrl();
        return val != null ? val : "";
    }

    public void setSearxngUrl(String url) {
        storage.setWebSearchSearxngUrl(url != null ? url : "");
    }

    // --- Num Results ---

    public int getNumResults() {
        return clamp(storage.getWebSearchNumResults());
    }

    public void setNumResults(int num) {
        storage.setWebSearchNumResults(clamp(num));
    }

    // --- Helpers ---

    /**
     * @return true if the given provider requires an API key.
     */
    public static boolean providerNeedsKey(String provider) {
        return PROVIDER_BRAVE.equals(provider)
                || PROVIDER_KAGI.equals(provider)
                || PROVIDER_SERPER.equals(provider)
                || PROVIDER_TAVILY.equals(provider);
    }

    /**
     * @return display name for the provider (for UI).
     */
    public static String getProviderDisplayName(String provider) {
        switch (provider) {
            case PROVIDER_BRAVE: return "Brave";
            case PROVIDER_KAGI: return "Kagi";
            case PROVIDER_SERPER: return "Serper";
            case PROVIDER_TAVILY: return "Tavily";
            case PROVIDER_SEARXNG: return "SearXNG";
            case PROVIDER_DUCKDUCKGO: return "DuckDuckGo";
            default: return provider;
        }
    }

    private static int clamp(int value) {
        return Math.max(MIN_RESULTS, Math.min(MAX_RESULTS, value));
    }
}
