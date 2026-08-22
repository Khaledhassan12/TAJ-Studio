package pro.sketchware.ai.live;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LiveRegistry {

    public interface DomainListener {
        void onDomainChanged(String domain, String path);
    }

    private static final Map<String, List<DomainListener>> listeners = new HashMap<>();

    public static synchronized void register(String domain, DomainListener l) {
        List<DomainListener> list = listeners.computeIfAbsent(domain, k -> new ArrayList<>());
        if (!list.contains(l)) {
            list.add(l);
        }
    }

    public static synchronized void unregister(String domain, DomainListener l) {
        List<DomainListener> list = listeners.get(domain);
        if (list != null) {
            list.remove(l);
        }
    }

    public static synchronized void notifyChanged(String domain, String path) {
        List<DomainListener> list = listeners.get(domain);
        if (list != null) {
            for (DomainListener l : new ArrayList<>(list)) {
                UiPoster.post(() -> l.onDomainChanged(domain, path));
            }
        }
    }

    private LiveRegistry() {}
}
