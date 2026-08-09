package pro.sketchware.ai.models;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pro.sketchware.ai.core.AiProvider;
import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.data.SecureKeyStore;
import pro.sketchware.ai.providers.ProviderConfig;
import pro.sketchware.ai.providers.ProviderRegistry;
import pro.sketchware.ai.providers.cloud.AnthropicProvider;
import pro.sketchware.ai.providers.cloud.GeminiProvider;
import pro.sketchware.ai.providers.cloud.OpenAiProvider;

/**
 * [WHAT] Single Source of Truth (SSOT) for AI models (fetched, custom, local).
 * [WHY] Centralizes model state, persistence, and synchronization (R5).
 * [HOW] Persists to AiStorage kv table; notifies listeners on changes.
 */
public class ModelCatalog {

    public static class ModelEntry {
        public String providerId;
        public String modelId;
        public String alias;
        public boolean isCustom;
        public boolean isLocal;

        public ModelEntry(String providerId, String modelId, String alias) {
            this.providerId = providerId;
            this.modelId = modelId;
            this.alias = alias;
        }
    }

    public static class SyncReport {
        public Map<String, Integer> ok = new HashMap<>();
        public Map<String, String> failed = new HashMap<>();
    }

    public interface Listener {
        void onCatalogChanged();
    }

    private static ModelCatalog instance;
    private final Context context;
    private final AiStorage storage;
    private final Gson gson = new Gson();
    private final List<Listener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static synchronized ModelCatalog get(Context context) {
        if (instance == null) {
            instance = new ModelCatalog(context.getApplicationContext());
        }
        return instance;
    }

    private ModelCatalog(Context context) {
        this.context = context;
        this.storage = AiStorage.get(context);
    }

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    private void notifyChanged() {
        mainHandler.post(() -> {
            for (Listener l : listeners) l.onCatalogChanged();
        });
    }

    // --- Fetched Cache ---

    public List<String> getFetchedModels(String providerId) {
        String json = storage.kvGet("fetched_" + providerId);
        if (json == null) return new ArrayList<>();
        return gson.fromJson(json, new TypeToken<List<String>>(){}.getType());
    }

    public int syncProvider(String providerId) throws Exception {
        ProviderConfig cfg = ProviderRegistry.get(context).findById(providerId);
        if (cfg == null) throw new Exception("Provider not found");

        AiProvider provider = ProviderRegistry.get(context).providerFor(cfg, null);
        List<String> ids;
        if (provider instanceof OpenAiProvider) {
            ids = ((OpenAiProvider) provider).fetchModelIds();
        } else if (provider instanceof AnthropicProvider) {
            ids = ((AnthropicProvider) provider).fetchModelIds();
        } else if (provider instanceof GeminiProvider) {
            ids = ((GeminiProvider) provider).fetchModelIds();
        } else {
            throw new Exception("Sync not supported for " + providerId);
        }

        storage.kvPut("fetched_" + providerId, gson.toJson(ids));
        notifyChanged();
        return ids.size();
    }

    public SyncReport syncAll() {
        SyncReport report = new SyncReport();
        List<ProviderConfig> configs = ProviderRegistry.get(context).loadAll();
        for (ProviderConfig cfg : configs) {
            if (cfg.keyCount > 0 || cfg.worksWithoutKey) {
                try {
                    int count = syncProvider(cfg.id);
                    report.ok.put(cfg.id, count);
                } catch (Exception e) {
                    report.failed.put(cfg.id, e.getMessage());
                }
            }
        }
        return report;
    }

    // --- Enabled Models ---

    public void setEnabled(String providerId, String modelId, boolean enabled) {
        Set<String> set = getEnabledKeys();
        String key = providerId + ":" + modelId;
        if (enabled) set.add(key);
        else set.remove(key);
        storage.kvPut("enabled_models", gson.toJson(set));
        
        // Clear default if disabled
        if (!enabled) {
            String def = storage.kvGet("default_model");
            if (key.equals(def)) storage.kvPut("default_model", null);
        }
        notifyChanged();
    }

    public boolean isEnabled(String providerId, String modelId) {
        return getEnabledKeys().contains(providerId + ":" + modelId);
    }

    private Set<String> getEnabledKeys() {
        String json = storage.kvGet("enabled_models");
        if (json == null) return new HashSet<>();
        return gson.fromJson(json, new TypeToken<Set<String>>(){}.getType());
    }

    // --- Aliases ---

    public void setAlias(String providerId, String modelId, String alias) {
        Map<String, String> map = getAliases();
        map.put(providerId + ":" + modelId, alias);
        storage.kvPut("model_aliases", gson.toJson(map));
        notifyChanged();
    }

    public String getAlias(String providerId, String modelId) {
        String alias = getAliases().get(providerId + ":" + modelId);
        return alias != null ? alias : modelId;
    }

    private Map<String, String> getAliases() {
        String json = storage.kvGet("model_aliases");
        if (json == null) return new HashMap<>();
        return gson.fromJson(json, new TypeToken<Map<String, String>>(){}.getType());
    }

    // --- Custom Models ---

    public static class CustomModel {
        public String provider;
        public String modelId;
        public String alias;
    }

    public void addCustom(String provider, String modelId, String alias) throws Exception {
        if (provider == null || provider.isEmpty() || modelId == null || modelId.isEmpty()) {
            throw new Exception("Provider and Model ID required");
        }
        List<CustomModel> list = getCustomModels();
        for (CustomModel cm : list) {
            if (cm.provider.equals(provider) && cm.modelId.equals(modelId)) {
                throw new Exception("Custom model already exists");
            }
        }
        CustomModel nm = new CustomModel();
        nm.provider = provider;
        nm.modelId = modelId;
        nm.alias = alias;
        list.add(nm);
        storage.kvPut("custom_models", gson.toJson(list));
        notifyChanged();
    }

    public void deleteCustom(String provider, String modelId) {
        List<CustomModel> list = getCustomModels();
        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            CustomModel cm = list.get(i);
            if (cm.provider.equals(provider) && cm.modelId.equals(modelId)) {
                list.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) {
            storage.kvPut("custom_models", gson.toJson(list));
            setEnabled(provider, modelId, false); // Also disables
            notifyChanged();
        }
    }

    public List<CustomModel> getCustomModels() {
        String json = storage.kvGet("custom_models");
        if (json == null) return new ArrayList<>();
        return gson.fromJson(json, new TypeToken<List<CustomModel>>(){}.getType());
    }

    // --- Default Model ---

    public void setDefault(String providerId, String modelId) {
        if (providerId == null || modelId == null) {
            storage.kvPut("default_model", null);
        } else {
            storage.kvPut("default_model", providerId + ":" + modelId);
        }
        notifyChanged();
    }

    public ModelEntry getDefaultModel() {
        String val = storage.kvGet("default_model");
        if (val == null) return null;
        String[] parts = val.split(":", 2);
        if (parts.length < 2) return null;
        return new ModelEntry(parts[0], parts[1], getAlias(parts[0], parts[1]));
    }

    // --- Usable Models ---

    public List<ModelEntry> usableModels() {
        List<ModelEntry> usable = new ArrayList<>();
        Set<String> enabled = getEnabledKeys();

        // 1. Fetched
        List<ProviderConfig> configs = ProviderRegistry.get(context).loadAll();
        for (ProviderConfig cfg : configs) {
            List<String> fetched = getFetchedModels(cfg.id);
            for (String mid : fetched) {
                if (enabled.contains(cfg.id + ":" + mid)) {
                    usable.add(new ModelEntry(cfg.id, mid, getAlias(cfg.id, mid)));
                }
            }
        }

        // 2. Custom
        List<CustomModel> custom = getCustomModels();
        for (CustomModel cm : custom) {
            if (enabled.contains(cm.provider + ":" + cm.modelId)) {
                ModelEntry entry = new ModelEntry(cm.provider, cm.modelId, cm.alias != null && !cm.alias.isEmpty() ? cm.alias : cm.modelId);
                entry.isCustom = true;
                usable.add(entry);
            }
        }

        // 3. Local
        List<LocalChatModelConfig> localConfigs = ModelManager.get(context).listLocalConfigs();
        for (LocalChatModelConfig l : localConfigs) {
            ModelEntry entry = new ModelEntry("local-llama", l.modelId, l.alias != null && !l.alias.isEmpty() ? l.alias : l.modelId);
            entry.isLocal = true;
            usable.add(entry);
        }

        return usable;
    }
}
