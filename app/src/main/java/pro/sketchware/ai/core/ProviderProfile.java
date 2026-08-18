package pro.sketchware.ai.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable description of one selectable AI provider entry. The catalog
 * ({@link pro.sketchware.ai.config.ProviderCatalog}) defines all built-in
 * profiles; the user can also register custom profiles (any OpenAI-compatible
 * endpoint, or custom Anthropic/Gemini-compatible endpoints).
 */
public final class ProviderProfile {

    /** Reserved id for the universal user-defined endpoint entry. */
    public static final String CUSTOM_ID = "custom";

    public final String id;
    public final String displayName;
    public final Protocol protocol;
    public final String defaultBaseUrl;
    public final boolean baseUrlEditable;
    public final boolean requiresKey;
    public final String[] defaultModelSuggestions;
    /** Extra static HTTP headers sent with every request (e.g. OpenRouter attribution). */
    public final Map<String, String> extraHeaders;
    /** If true, auth is sent as an "api-key" header instead of "Authorization: Bearer". */
    public final boolean apiKeyHeaderAuth;
    /** Optional fixed query suffix appended to request URLs (e.g. Azure api-version). */
    public final String urlQuerySuffix;
    /** True when this profile was created/edited by the user rather than built-in. */
    public final boolean custom;

    private ProviderProfile(Builder b) {
        id = b.id;
        displayName = b.displayName;
        protocol = b.protocol;
        defaultBaseUrl = b.defaultBaseUrl;
        baseUrlEditable = b.baseUrlEditable;
        requiresKey = b.requiresKey;
        defaultModelSuggestions = b.defaultModelSuggestions == null
                ? new String[0] : b.defaultModelSuggestions.clone();
        extraHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(b.extraHeaders));
        apiKeyHeaderAuth = b.apiKeyHeaderAuth;
        urlQuerySuffix = b.urlQuerySuffix;
        custom = b.custom;
    }

    /** Returns a copy of this profile with a different base URL (used when the user edits it). */
    public ProviderProfile withBaseUrl(String newBaseUrl) {
        Builder b = new Builder(id, displayName, protocol)
                .baseUrl(newBaseUrl == null || newBaseUrl.isEmpty() ? defaultBaseUrl : newBaseUrl)
                .baseUrlEditable(baseUrlEditable)
                .requiresKey(requiresKey)
                .models(defaultModelSuggestions)
                .apiKeyHeaderAuth(apiKeyHeaderAuth)
                .urlQuerySuffix(urlQuerySuffix)
                .custom(custom);
        for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
            b.header(e.getKey(), e.getValue());
        }
        return b.build();
    }

    public boolean hasSuggestions() {
        return defaultModelSuggestions.length > 0;
    }

    public static final class Builder {
        private final String id;
        private final String displayName;
        private final Protocol protocol;
        private String defaultBaseUrl = "";
        private boolean baseUrlEditable = true;
        private boolean requiresKey = true;
        private String[] defaultModelSuggestions;
        private final Map<String, String> extraHeaders = new LinkedHashMap<>();
        private boolean apiKeyHeaderAuth = false;
        private String urlQuerySuffix = null;
        private boolean custom = false;

        public Builder(String id, String displayName, Protocol protocol) {
            this.id = id;
            this.displayName = displayName;
            this.protocol = protocol;
        }

        public Builder baseUrl(String url) {
            this.defaultBaseUrl = url;
            return this;
        }

        public Builder baseUrlEditable(boolean editable) {
            this.baseUrlEditable = editable;
            return this;
        }

        public Builder requiresKey(boolean required) {
            this.requiresKey = required;
            return this;
        }

        public Builder models(String... suggestions) {
            this.defaultModelSuggestions = suggestions;
            return this;
        }

        public Builder header(String name, String value) {
            this.extraHeaders.put(name, value);
            return this;
        }

        public Builder apiKeyHeaderAuth(boolean enabled) {
            this.apiKeyHeaderAuth = enabled;
            return this;
        }

        public Builder urlQuerySuffix(String suffix) {
            this.urlQuerySuffix = suffix;
            return this;
        }

        public Builder custom(boolean custom) {
            this.custom = custom;
            return this;
        }

        public ProviderProfile build() {
            return new ProviderProfile(this);
        }
    }
}
