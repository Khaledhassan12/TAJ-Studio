package pro.sketchware.ai.live;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-process invalidation bus for live editor domains.
 *
 * Listeners are copied while holding the registry lock, but callbacks are always
 * dispatched after releasing it. This prevents editor callbacks from running
 * under the global registry monitor and avoids re-entrant registration deadlocks.
 */
public final class LiveRegistry {

    public interface DomainListener {
        void onDomainChanged(String domain, String path);
    }

    private static final Map<String, List<DomainListener>> listeners = new HashMap<>();

    private static String normalizeDomain(String domain) {
        if (domain == null) return null;
        String value = domain.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.startsWith("layout")) return "layout";
        if (value.startsWith("values")) return "values";
        if (value.startsWith("drawable")) return "drawable";
        if (value.startsWith("anim")) return "anim";
        if (value.startsWith("menu")) return "menu";
        return value;
    }

    public static synchronized void register(String domain, DomainListener listener) {
        domain = normalizeDomain(domain);
        if (domain == null || domain.isEmpty() || listener == null) {
            return;
        }
        List<DomainListener> list = listeners.computeIfAbsent(domain, key -> new ArrayList<>());
        if (!list.contains(listener)) {
            list.add(listener);
        }
    }

    public static synchronized void unregister(String domain, DomainListener listener) {
        domain = normalizeDomain(domain);
        if (domain == null || listener == null) {
            return;
        }
        List<DomainListener> list = listeners.get(domain);
        if (list != null) {
            list.remove(listener);
            if (list.isEmpty()) {
                listeners.remove(domain);
            }
        }
    }

    public static void notifyChanged(String domain, String path) {
        domain = normalizeDomain(domain);
        if (domain == null || domain.isEmpty()) {
            return;
        }

        final String changedDomain = domain;
        final List<DomainListener> snapshot;
        synchronized (LiveRegistry.class) {
            List<DomainListener> current = listeners.get(changedDomain);
            snapshot = current == null ? new ArrayList<>() : new ArrayList<>(current);
        }

        // Empty path is a deliberate wildcard: refresh the active view for this domain.
        final String changedPath = path == null ? "" : path;
        for (DomainListener listener : snapshot) {
            UiPoster.post(() -> {
                try {
                    listener.onDomainChanged(changedDomain, changedPath);
                } catch (RuntimeException ignored) {
                    // One broken/tearing-down listener must not block other editor listeners.
                }
            });
        }
    }

    private LiveRegistry() {}
}
