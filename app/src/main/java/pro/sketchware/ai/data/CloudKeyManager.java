package pro.sketchware.ai.data;

import android.content.Context;

/**
 * [WHAT] Facade for managing cloud AI API keys.
 * [WHY] Simplifies key access for providers while ensuring encryption (RISK-4).
 * [HOW] Wraps SecureKeyStore with specific provider methods.
 */
public class CloudKeyManager {

    private final SecureKeyStore secureKeyStore;

    public CloudKeyManager(Context context) {
        this.secureKeyStore = SecureKeyStore.get(context);
    }

    public void putKey(String providerId, String key) {
        secureKeyStore.putKey(providerId, key);
    }

    public String getKey(String providerId) {
        return secureKeyStore.getKey(providerId);
    }

    public void removeKey(String providerId) {
        secureKeyStore.removeKey(providerId);
    }

    public boolean hasKey(String providerId) {
        return secureKeyStore.hasKey(providerId);
    }
}
