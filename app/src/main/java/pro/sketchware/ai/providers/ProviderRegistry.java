package pro.sketchware.ai.providers;

import android.content.Context;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.data.SecureKeyStore;

import pro.sketchware.ai.core.AiProvider;
import pro.sketchware.ai.providers.cloud.AnthropicProvider;
import pro.sketchware.ai.providers.cloud.GeminiProvider;
import pro.sketchware.ai.providers.cloud.OpenAiProvider;

/**
 * [WHAT] Singleton that manages loading and saving ProviderConfigs.
 * [WHY] Acts as the Single Source of Truth (SSOT) for provider metadata (R5).
 * [HOW] Persists configs to AiStorage kv table; checks SecureKeyStore for key status.
 */
public class ProviderRegistry {

    private static final String KEY_PROVIDERS_LIST = "ai_providers_registry_list";
    private static final String KEY_CUSTOM_PROVIDERS = "ai_custom_providers_list";
    
    private static ProviderRegistry instance;
    private final AiStorage storage;
    private final SecureKeyStore keyStore;
    private final Gson gson = new Gson();

    public static synchronized ProviderRegistry get(Context context) {
        if (instance == null) {
            instance = new ProviderRegistry(context.getApplicationContext());
        }
        return instance;
    }

    private ProviderRegistry(Context context) {
        this.storage = AiStorage.get(context);
        this.keyStore = SecureKeyStore.get(context);
    }

    public List<ProviderConfig> loadAll() {
        List<ProviderConfig> configs = loadBuiltIns();
        configs.addAll(loadCustom());

        // Sync with SecureKeyStore
        for (ProviderConfig cfg : configs) {
            cfg.keyCount = keyStore.keyCount(cfg.id);
            // worksWithoutKey (like Ollama) counts as "hasKey" for picker/status purposes
            cfg.hasKey = cfg.keyCount > 0 || cfg.worksWithoutKey;
        }

        return configs;
    }

    private List<ProviderConfig> loadBuiltIns() {
        String json = storage.kvGet(KEY_PROVIDERS_LIST);
        List<ProviderConfig> configs = null;
        if (!TextUtils.isEmpty(json)) {
            configs = gson.fromJson(json, new TypeToken<List<ProviderConfig>>(){}.getType());
        }

        if (configs == null || configs.isEmpty()) {
            configs = getDefaultConfigs();
            performMigration();
            saveBuiltIns(configs);
        }
        return configs;
    }

    private void performMigration() {
        // "gemini" -> "google"
        if (keyStore.hasKey("gemini")) {
            String key = keyStore.getKey("gemini");
            if (key != null) {
                keyStore.addKey("google", "Legacy Gemini Key", key);
                keyStore.removeKey("gemini");
            }
        }
        // "compatible" -> keep as custom if present
        if (keyStore.hasKey("compatible")) {
            String key = keyStore.getKey("compatible");
            if (key != null) {
                addCustom("Legacy Compatible", "openai-wire", "https://api.openai.com/v1");
                List<ProviderConfig> custom = loadCustom();
                if (!custom.isEmpty()) {
                    String newId = custom.get(custom.size() - 1).id;
                    keyStore.addKey(newId, "Legacy Key", key);
                }
                keyStore.removeKey("compatible");
            }
        }
    }

    public List<ProviderConfig> loadCustom() {
        String json = storage.kvGet(KEY_CUSTOM_PROVIDERS);
        if (TextUtils.isEmpty(json)) return new ArrayList<>();
        List<ProviderConfig> custom = gson.fromJson(json, new TypeToken<List<ProviderConfig>>(){}.getType());
        return custom != null ? custom : new ArrayList<>();
    }

    public void addCustom(String name, String protocol, String baseUrl) {
        List<ProviderConfig> custom = loadCustom();
        String id = "custom_" + UUID.randomUUID().toString().substring(0, 8);
        custom.add(new ProviderConfig(id, name, ProviderConfig.TYPE_CLOUD, protocol, baseUrl, true));
        storage.kvPut(KEY_CUSTOM_PROVIDERS, gson.toJson(custom));
    }

    public void deleteCustom(String id) {
        List<ProviderConfig> custom = loadCustom();
        for (int i = 0; i < custom.size(); i++) {
            if (custom.get(i).id.equals(id)) {
                custom.remove(i);
                break;
            }
        }
        storage.kvPut(KEY_CUSTOM_PROVIDERS, gson.toJson(custom));
        // Purge keys
        List<SecureKeyStore.KeyInfo> keys = keyStore.listKeyNames(id);
        for (SecureKeyStore.KeyInfo k : keys) keyStore.removeKey(id, k.id);
        keyStore.removeKey(id); // Legacy key
    }

    public String getBaseUrl(String providerId) {
        String override = storage.kvGet("ai_baseurl_override_" + providerId);
        if (!TextUtils.isEmpty(override)) return override;
        
        ProviderConfig cfg = findById(providerId);
        return cfg != null ? cfg.baseUrl : null;
    }

    public void setBaseUrlOverride(String providerId, String url) {
        if (TextUtils.isEmpty(url)) {
            storage.kvPut("ai_baseurl_override_" + providerId, null);
        } else {
            storage.kvPut("ai_baseurl_override_" + providerId, url);
        }
    }

    public ProviderConfig findById(String id) {
        for (ProviderConfig cfg : loadAll()) {
            if (cfg.id.equals(id)) return cfg;
        }
        return null;
    }

    public AiProvider providerFor(ProviderConfig config, String keyIdOrNull) {
        if ("google-wire".equals(config.wireProtocol)) {
            return new GeminiProvider(storage.getContext(), config.id, config.displayName, keyIdOrNull);
        } else if ("anthropic-wire".equals(config.wireProtocol)) {
            return new AnthropicProvider(storage.getContext(), config.id, config.displayName, keyIdOrNull);
        } else {
            // Default to openai-wire
            return new OpenAiProvider(storage.getContext(), config.id, config.displayName, keyIdOrNull);
        }
    }

    public void save(ProviderConfig cfg) {
        if (cfg.id.startsWith("custom_")) {
            List<ProviderConfig> custom = loadCustom();
            updateInList(custom, cfg);
            storage.kvPut(KEY_CUSTOM_PROVIDERS, gson.toJson(custom));
        } else {
            List<ProviderConfig> builtIns = loadBuiltIns();
            updateInList(builtIns, cfg);
            saveBuiltIns(builtIns);
        }
    }

    private void updateInList(List<ProviderConfig> list, ProviderConfig cfg) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(cfg.id)) {
                list.set(i, cfg);
                return;
            }
        }
        list.add(cfg);
    }

    public void delete(String id) {
        if (id.startsWith("custom_")) {
            deleteCustom(id);
        } else {
            // Built-ins cannot be deleted, only disabled
            ProviderConfig cfg = findById(id);
            if (cfg != null) {
                cfg.enabled = false;
                save(cfg);
            }
        }
    }

    private void saveBuiltIns(List<ProviderConfig> configs) {
        storage.kvPut(KEY_PROVIDERS_LIST, gson.toJson(configs));
    }

    public List<ProviderConfig> getDefaultConfigs() {
        List<ProviderConfig> defaults = new ArrayList<>();
        // Mockup order: google, openai, anthropic, deepseek, qwen, groq, ollama, openrouter
        defaults.add(new ProviderConfig("google", "Google Gemini", ProviderConfig.TYPE_CLOUD, "google-wire", "https://generativelanguage.googleapis.com/v1beta", true));
        defaults.add(new ProviderConfig("openai", "OpenAI", ProviderConfig.TYPE_CLOUD, "openai-wire", "https://api.openai.com/v1", true));
        defaults.add(new ProviderConfig("anthropic", "Anthropic", ProviderConfig.TYPE_CLOUD, "anthropic-wire", "https://api.anthropic.com/v1", true));
        defaults.add(new ProviderConfig("deepseek", "DeepSeek", ProviderConfig.TYPE_CLOUD, "openai-wire", "https://api.deepseek.com", true));
        defaults.add(new ProviderConfig("qwen", "Qwen", ProviderConfig.TYPE_CLOUD, "openai-wire", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", true));
        defaults.add(new ProviderConfig("groq", "Groq", ProviderConfig.TYPE_CLOUD, "openai-wire", "https://api.groq.com/openai/v1", true));
        
        ProviderConfig ollama = new ProviderConfig("ollama", "Ollama", ProviderConfig.TYPE_CLOUD, "openai-wire", "http://www.ollama.com/api/v1", true);
        ollama.worksWithoutKey = true;
        defaults.add(ollama);
        
        defaults.add(new ProviderConfig("openrouter", "OpenRouter", ProviderConfig.TYPE_CLOUD, "openai-wire", "https://openrouter.ai/api/v1", true));
        return defaults;
    }
}
