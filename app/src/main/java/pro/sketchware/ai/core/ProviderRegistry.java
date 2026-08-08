package pro.sketchware.ai.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [WHAT] Central registry for AI providers.
 * [WHY] Single source of truth for available engines (R5).
 */
public class ProviderRegistry {
    private static final Map<String, AiProvider> providers = new HashMap<>();

    public static void register(AiProvider provider) {
        providers.put(provider.id(), provider);
    }

    public static AiProvider get(String id) {
        return providers.get(id);
    }

    public static List<AiProvider> list() {
        return new ArrayList<>(providers.values());
    }

    public static void clear() {
        providers.clear();
    }
}
