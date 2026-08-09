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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor();

    // Cache (SSOT in memory)
    private final Map<String, List<String>> fetchedCache = new HashMap<>();
    private final Set<String> enabledSet = new HashSet<>();
    private final Map<String, String> aliasMap = new HashMap<>();
    private final List<CustomModel> customList = new ArrayList<>();
    private boolean isLoaded = false;

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

    public void load(Runnable onDone) {
        persistenceExecutor.execute(() -> {
            synchronized (this) {
                if (isLoaded) {
                    if (onDone != null) mainHandler.post(onDone);
                    return;
                }
                
                // Load Enabled
                String enabledJson = storage.kvGet("enabled_models");
                if (enabledJson != null) {
                    try {
                        Set<String> set = gson.fromJson(enabledJson, new TypeToken<Set<String>>(){}.getType());
                        if (set != null) enabledSet.addAll(set);
                    } catch (Exception ignored) {}
                }

                // Load Aliases
                String aliasJson = storage.kvGet("model_aliases");
                if (aliasJson != null) {
                    try {
                        Map<String, String> map = gson.fromJson(aliasJson, new TypeToken<Map<String, String>>(){}.getType());
                        if (map != null) aliasMap.putAll(map);
                    } catch (Exception ignored) {}
                }

                // Load Custom
                String customJson = storage.kvGet("custom_models");
                if (customJson != null) {
                    try {
                        List<CustomModel> list = gson.fromJson(customJson, new TypeToken<List<CustomModel>>(){}.getType());
                        if (list != null) customList.addAll(list);
                    } catch (Exception ignored) {}
                }

                // Load Fetched per provider
                List<ProviderConfig> configs = ProviderRegistry.get(context).loadAll();
                for (ProviderConfig cfg : configs) {
                    String json = storage.kvGet("fetched_" + cfg.id);
                    if (json != null) {
                        try {
                            List<String> ids = gson.fromJson(json, new TypeToken<List<String>>(){}.getType());
                            if (ids != null) fetchedCache.put(cfg.id, ids);
                        } catch (Exception ignored) {}
                    }
                }

                isLoaded = true;
                if (onDone != null) mainHandler.post(onDone);
            }
        });
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
        synchronized (this) {
            List<String> list = fetchedCache.get(providerId);
            return list != null ? new ArrayList<>(list) : new ArrayList<>();
        }
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

        synchronized (this) {
            fetchedCache.put(providerId, ids);
        }
        
        persistenceExecutor.execute(() -> {
            storage.kvPut("fetched_" + providerId, gson.toJson(ids));
        });
        
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
        String key = providerId + ":" + modelId;
        synchronized (this) {
            if (enabled) enabledSet.add(key);
            else enabledSet.remove(key);
        }
        
        persistenceExecutor.execute(() -> {
            synchronized (this) {
                storage.kvPut("enabled_models", gson.toJson(new HashSet<>(enabledSet)));
                // Clear default if disabled
                if (!enabled) {
                    String def = storage.kvGet("default_model");
                    if (key.equals(def)) storage.kvPut("default_model", null);
                }
            }
        });
        notifyChanged();
    }

    public boolean isEnabled(String providerId, String modelId) {
        synchronized (this) {
            return enabledSet.contains(providerId + ":" + modelId);
        }
    }

    // --- Aliases ---

    public void setAlias(String providerId, String modelId, String alias) {
        String key = providerId + ":" + modelId;
        synchronized (this) {
            aliasMap.put(key, alias);
        }
        persistenceExecutor.execute(() -> {
            synchronized (this) {
                storage.kvPut("model_aliases", gson.toJson(new HashMap<>(aliasMap)));
            }
        });
        notifyChanged();
    }

    public String getAlias(String providerId, String modelId) {
        synchronized (this) {
            String alias = aliasMap.get(providerId + ":" + modelId);
            return alias != null ? alias : modelId;
        }
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
        
        synchronized (this) {
            for (CustomModel cm : customList) {
                if (cm.provider.equals(provider) && cm.modelId.equals(modelId)) {
                    throw new Exception("Custom model already exists");
                }
            }
            CustomModel nm = new CustomModel();
            nm.provider = provider;
            nm.modelId = modelId;
            nm.alias = alias;
            customList.add(nm);
        }
        
        persistenceExecutor.execute(() -> {
            synchronized (this) {
                storage.kvPut("custom_models", gson.toJson(new ArrayList<>(customList)));
            }
        });
        notifyChanged();
    }

    public void deleteCustom(String provider, String modelId) {
        boolean removed = false;
        synchronized (this) {
            for (int i = 0; i < customList.size(); i++) {
                CustomModel cm = customList.get(i);
                if (cm.provider.equals(provider) && cm.modelId.equals(modelId)) {
                    customList.remove(i);
                    removed = true;
                    break;
                }
            }
        }
        if (removed) {
            persistenceExecutor.execute(() -> {
                synchronized (this) {
                    storage.kvPut("custom_models", gson.toJson(new ArrayList<>(customList)));
                }
            });
            setEnabled(provider, modelId, false); // Also disables and notifies
        }
    }

    public List<CustomModel> getCustomModels() {
        synchronized (this) {
            return new ArrayList<>(customList);
        }
    }

    // --- Default Model ---

    public void setDefault(String providerId, String modelId) {
        String val = (providerId == null || modelId == null) ? null : providerId + ":" + modelId;
        persistenceExecutor.execute(() -> {
            storage.kvPut("default_model", val);
        });
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
        Set<String> enabled;
        synchronized (this) {
            enabled = new HashSet<>(enabledSet);
        }

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
