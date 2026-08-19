package pro.sketchware.ai.core;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.ai.config.AIConfigStore;
import pro.sketchware.ai.config.ProviderCatalog;
import pro.sketchware.ai.net.engines.AnthropicEngine;
import pro.sketchware.ai.net.engines.GeminiEngine;
import pro.sketchware.ai.net.engines.OpenAICompatibleEngine;

/**
 * Singleton registry of all provider profiles: the built-in catalog plus any
 * user-created custom profiles. Also the factory that turns the active profile
 * + credentials into a callable {@link AIProvider} engine.
 */
public final class AIProviderRegistry {

    private static volatile AIProviderRegistry instance;

    private final LinkedHashMap<String, ProviderProfile> profiles = new LinkedHashMap<>();

    private AIProviderRegistry(Context context) {
        for (ProviderProfile profile : ProviderCatalog.builtInProfiles()) {
            profiles.put(profile.id, profile);
        }
        AIConfigStore store = AIConfigStore.getInstance(context);
        for (ProviderProfile custom : store.getCustomProviders()) {
            profiles.put(custom.id, custom);
        }
        // User overrides of a built-in base URL.
        for (Map.Entry<String, ProviderProfile> entry : profiles.entrySet()) {
            String override = store.getBaseUrl(entry.getKey());
            if (!override.isEmpty() && !override.equals(entry.getValue().defaultBaseUrl)) {
                entry.setValue(entry.getValue().withBaseUrl(override));
            }
        }
    }

    public static AIProviderRegistry getInstance(Context context) {
        if (instance == null) {
            synchronized (AIProviderRegistry.class) {
                if (instance == null) {
                    instance = new AIProviderRegistry(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /** All selectable profiles in display order (built-ins first, custom last). */
    public List<ProviderProfile> allProfiles() {
        return new ArrayList<>(profiles.values());
    }

    public ProviderProfile get(String id) {
        return id == null ? null : profiles.get(id);
    }

    public ProviderProfile findByDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        for (ProviderProfile profile : profiles.values()) {
            if (profile.displayName.equals(displayName)) {
                return profile;
            }
        }
        return null;
    }

    /**
     * The profile currently selected in settings; falls back to OpenAI so the
     * caller never has to handle null.
     */
    public ProviderProfile activeProfile(Context context) {
        AIConfigStore store = AIConfigStore.getInstance(context);
        ProviderProfile profile = get(store.getSelectedProviderId());
        if (profile == null) {
            profile = get("openai");
        }
        return profile;
    }

    /** Builds the right engine for a profile with the given credentials. */
    public AIProvider createProvider(Context context, ProviderProfile profile, String apiKey) {
        switch (profile.protocol) {
            case ANTHROPIC:
                return new AnthropicEngine(context, profile, profile.defaultBaseUrl, apiKey);
            case GEMINI:
                return new GeminiEngine(context, profile, profile.defaultBaseUrl, apiKey);
            case OPENAI_COMPATIBLE:
            default:
                return new OpenAICompatibleEngine(context, profile, profile.defaultBaseUrl, apiKey);
        }
    }

    /** Convenience: active profile + stored key + stored model resolved into a ready engine. */
    public AIProvider createActiveProvider(Context context) {
        AIConfigStore store = AIConfigStore.getInstance(context);
        ProviderProfile profile = activeProfile(context);
        return createProvider(context, profile, store.safeKey());
    }

    /** Model to use for a profile: user override, else first suggestion. */
    public static String resolveModel(Context context, ProviderProfile profile) {
        AIConfigStore store = AIConfigStore.getInstance(context);
        String saved = store.getModel(profile.id);
        if (!saved.isEmpty()) {
            return saved;
        }
        return profile.defaultModelSuggestions.length > 0 ? profile.defaultModelSuggestions[0] : "";
    }
}
