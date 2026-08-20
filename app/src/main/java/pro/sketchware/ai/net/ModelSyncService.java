package pro.sketchware.ai.net;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import pro.sketchware.ai.config.AIConfigStore;
import pro.sketchware.ai.core.ModelItem;
import pro.sketchware.ai.core.Protocol;
import pro.sketchware.ai.core.ProviderProfile;

/**
 * Fetches a provider\'s model list from its official models endpoint and
 * parses it per protocol. Must be called from a background thread.
 *
 * Endpoints (base URL already includes any version path):
 *  - OpenAI-compatible: GET {base}/models
 *  - Anthropic:         GET {base}/v1/models   (x-api-key + anthropic-version)
 *  - Gemini:            GET {base}/v1beta/models (x-goog-api-key)
 * Azure has NO model list endpoint and degrades gracefully. A 404 anywhere
 * also degrades gracefully (e.g. Perplexity).
 */
public final class ModelSyncService {

    private static final String TAG = "ModelSyncService";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final long TIMEOUT_CONNECT_MS = 10_000L;
    private static final long TIMEOUT_READ_MS = 30_000L;
    private static final long TIMEOUT_WRITE_MS = 15_000L;

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_CONNECT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_READ_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(TIMEOUT_WRITE_MS, TimeUnit.MILLISECONDS)
            .build();

    private ModelSyncService() {
    }

    /**
     * Synchronous key validation.
     * Verdicts per protocol requirements.
     */
    public static String validateKey(ProviderProfile profile, String apiKey) {
        String cleanKey = (apiKey != null && !apiKey.trim().isEmpty()) ? AIConfigStore.sanitizeKey(apiKey) : AIConfigStore.getInstance(null).safeKeyFor(profile.id);
        String base = AIConfigStore.sanitizeBaseUrl(profile.defaultBaseUrl);
        // If the profile's base is default, try to get override from store
        if (base.equals(AIConfigStore.sanitizeBaseUrl(profile.id.equals("custom") ? "" : pro.sketchware.ai.config.ProviderCatalog.getById(profile.id).defaultBaseUrl))) {
            String stored = AIConfigStore.getInstance(null).safeBaseUrlFor(profile.id, "");
            if (!stored.isEmpty()) base = stored;
        }
        
        Log.d(TAG, "Validating key for provider ID: " + profile.id + " at base URL: " + base);
        
        if (base.isEmpty()) return "Enter a valid base URL.";

        String path;
        if ("openrouter".equals(profile.id)) {
            path = "/key";
        } else if (profile.protocol == Protocol.ANTHROPIC) {
            path = "/v1/models";
        } else if (profile.protocol == Protocol.GEMINI) {
            path = "/v1beta/models";
        } else {
            path = "/models";
        }

        Request.Builder builder = new Request.Builder().url(base + path).get();
        if (profile.protocol == Protocol.ANTHROPIC) {
            builder.header("x-api-key", cleanKey);
            builder.header("anthropic-version", "2023-06-01");
        } else if (profile.protocol == Protocol.GEMINI) {
            if (!cleanKey.isEmpty()) {
                builder.header("x-goog-api-key", cleanKey);
            }
        } else {
            if (!cleanKey.isEmpty()) {
                builder.header("Authorization", "Bearer " + cleanKey);
            }
        }

        Request request = builder.build();
        AIConfigStore store = AIConfigStore.getInstance(null);
        boolean autoRetry = store.isAutoRetry();

        try (Response response = RetryPolicy.executeWithRetry(() -> CLIENT.newCall(request).execute(), autoRetry)) {
            if (response != null && response.isSuccessful()) {
                // For "other oc" (OpenAI compatible but not OpenRouter), /models might be public.
                if (!"openrouter".equals(profile.id) && profile.protocol == Protocol.OPENAI_COMPATIBLE) {
                    // Quick ping to confirm auth
                    String model = store.safeModel();
                    if (!model.isEmpty()) {
                        JSONObject body = new JSONObject();
                        body.put("model", model);
                        body.put("max_tokens", 1);
                        JSONArray msgs = new JSONArray();
                        msgs.put(new JSONObject().put("role", "user").put("content", "ping"));
                        body.put("messages", msgs);

                        Request ping = new Request.Builder()
                                .url(base + "/chat/completions")
                                .header("Authorization", "Bearer " + cleanKey)
                                .post(okhttp3.RequestBody.create(body.toString(), okhttp3.MediaType.parse("application/json")))
                                .build();
                        try (Response pr = CLIENT.newCall(ping).execute()) {
                            if (pr.isSuccessful()) return "Key accepted by provider.";
                            if (pr.code() == 401 || pr.code() == 403) {
                                return "Provider REJECTED this key. The app code is correct — generate a new key from the provider dashboard and paste it in API key field.";
                            }
                        }
                    }
                }
                return "Key accepted by provider.";
            }
            if (response != null && (response.code() == 401 || response.code() == 403)) {
                return "Provider REJECTED this key. The app code is correct — generate a new key from the provider dashboard and paste it in API key field.";
            }
            return "HTTP " + (response != null ? response.code() : "???") + ": " + (response != null ? readBody(response) : "Unknown error");
        } catch (Exception e) {
            return "Network error: " + e.getMessage();
        }
    }

    /** Outcome of one model-list fetch. */
    public static final class Result {
        public final List<ModelItem> models;
        public final boolean pricingAvailable;
        public final boolean modelListUnavailable;

        private Result(List<ModelItem> models, boolean pricingAvailable, boolean modelListUnavailable) {
            this.models = models == null ? new ArrayList<>() : models;
            this.pricingAvailable = pricingAvailable;
            this.modelListUnavailable = modelListUnavailable;
        }

        static Result success(List<ModelItem> models, boolean pricingAvailable) {
            return new Result(models, pricingAvailable, false);
        }

        static Result unavailable() {
            return new Result(new ArrayList<>(), false, true);
        }
    }

    /**
     * Synchronous model-list fetch. Do NOT call on the main thread.
     *
     * @throws AIException with taxonomy mapping and raw body when the provider
     *                     answers with an error status other than 404.
     */
    public static Result fetch(ProviderProfile profile, String apiKey) throws AIException {
        AIConfigStore store = AIConfigStore.getInstance(null);
        String cleanKey = (apiKey != null && !apiKey.trim().isEmpty()) ? AIConfigStore.sanitizeKey(apiKey) : store.safeKeyFor(profile.id);
        String base = AIConfigStore.sanitizeBaseUrl(profile.defaultBaseUrl);
        // If the profile's base is default, try to get override from store
        if (base.equals(AIConfigStore.sanitizeBaseUrl(profile.id.equals("custom") ? "" : pro.sketchware.ai.config.ProviderCatalog.getById(profile.id).defaultBaseUrl))) {
             String stored = store.safeBaseUrlFor(profile.id, "");
             if (!stored.isEmpty()) base = stored;
        }

        Log.d(TAG, "Fetching models for provider ID: " + profile.id + " at base URL: " + base);

        if (base.isEmpty()) {
            throw new AIException(AIException.Type.MODEL_NOT_FOUND,
                    "Enter a valid base URL for this provider.");
        }

        // Azure (api-key header) exposes no /models list under the deployment URL.
        if (profile.apiKeyHeaderAuth) {
            return Result.unavailable();
        }

        String path;
        switch (profile.protocol) {
            case ANTHROPIC:
                path = "/v1/models";
                break;
            case GEMINI:
                path = "/v1beta/models";
                break;
            case OPENAI_COMPATIBLE:
            default:
                path = "/models";
                break;
        }

        Request.Builder builder = new Request.Builder().url(base + path).get();
        if (profile.protocol == Protocol.ANTHROPIC) {
            builder.header("x-api-key", cleanKey);
            builder.header("anthropic-version", ANTHROPIC_VERSION);
        } else if (profile.protocol == Protocol.GEMINI) {
            if (!cleanKey.isEmpty()) {
                builder.header("x-goog-api-key", cleanKey);
            }
        } else {
            if (!profile.skipAuth && !cleanKey.isEmpty()) {
                if (profile.apiKeyHeaderAuth) {
                    builder.header("api-key", cleanKey);
                } else {
                    builder.header("Authorization", "Bearer " + cleanKey);
                }
            }
        }
        for (Map.Entry<String, String> header : profile.extraHeaders.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }

        Request request = builder.build();

        try (Response response = RetryPolicy.executeWithRetry(() -> CLIENT.newCall(request).execute(), store.isAutoRetry())) {
            if (response != null && response.code() == 404) {
                // Provider has no model-list endpoint: enter the model ID manually.
                return Result.unavailable();
            }
            if (!response.isSuccessful()) {
                String errorBody = readBody(response);
                logError(response.code(), errorBody, cleanKey);
                throw AIException.fromHttp(response, errorBody);
            }
            String body = readBody(response);
            if (body.isEmpty()) {
                throw new AIException(AIException.Type.INVALID_JSON, "The provider returned an empty response.");
            }
            List<ModelItem> models = parseModels(profile, body);
            if (models.isEmpty()) {
                throw new AIException(AIException.Type.INVALID_JSON, "The provider returned no usable models.");
            }
            Log.i(TAG, "Fetched " + models.size() + " models for provider " + profile.id);
            return Result.success(models, ProviderProfile.exposesPricing(profile));
        } catch (IOException e) {
            throw AIException.fromIo(e);
        }
    }
    private static List<ModelItem> parseModels(ProviderProfile profile, String body) throws AIException {
        List<ModelItem> result = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(body);
            if (profile.protocol == Protocol.GEMINI) {
                JSONArray models = root.optJSONArray("models");
                if (models == null) {
                    throw new AIException(AIException.Type.INVALID_JSON, "The provider returned an unexpected response.");
                }
                for (int i = 0; i < models.length(); i++) {
                    JSONObject json = models.optJSONObject(i);
                    if (json == null) {
                        continue;
                    }
                    String name = json.optString("name", "");
                    if (name.isEmpty() || !supportsGenerateContent(json)) {
                        continue;
                    }
                    result.add(new ModelItem(stripModelsPrefix(name), null));
                }
                return result;
            }

            // OpenAI-compatible + Anthropic share the {"data":[{...}]} shape.
            JSONArray data = root.optJSONArray("data");
            if (data == null) {
                throw new AIException(AIException.Type.INVALID_JSON, "The provider returned an unexpected response.");
            }
            boolean pricing = ProviderProfile.exposesPricing(profile);
            for (int i = 0; i < data.length(); i++) {
                JSONObject json = data.optJSONObject(i);
                if (json == null) {
                    continue;
                }
                String id = json.optString("id", "");
                if (id.isEmpty()) {
                    continue;
                }
                Boolean free = null;
                if (pricing) {
                    JSONObject p = json.optJSONObject("pricing");
                    if (p != null) {
                        double prompt = parsePrice(p.optString("prompt"));
                        double completion = parsePrice(p.optString("completion"));
                        free = prompt == 0.0d && completion == 0.0d;
                    }
                }
                result.add(new ModelItem(id, free));
            }
            return result;
        } catch (JSONException e) {
            throw new AIException(AIException.Type.INVALID_JSON, "The provider returned malformed JSON.", e);
        }
    }

    private static boolean supportsGenerateContent(JSONObject json) {
        JSONArray methods = json.optJSONArray("supportedGenerationMethods");
        if (methods == null) {
            return false;
        }
        for (int i = 0; i < methods.length(); i++) {
            if ("generateContent".equals(methods.optString(i, ""))) {
                return true;
            }
        }
        return false;
    }

    private static String stripModelsPrefix(String name) {
        if (name != null && name.startsWith("models/")) {
            return name.substring("models/".length());
        }
        return name == null ? "" : name;
    }

    /** Parses OpenRouter price strings; anything unparseable is treated as non-zero. */
    private static double parsePrice(String raw) {
        if (raw == null) {
            return -1.0d;
        }
        String t = raw.trim();
        if (t.isEmpty() || "Infinity".equals(t) || "+Infinity".equals(t)
                || "-Infinity".equals(t) || "None".equals(t)) {
            return -1.0d;
        }
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return -1.0d;
        }
    }

    private static String readBody(Response response) {
        try {
            ResponseBody body = response.body();
            return body == null ? "" : body.string();
        } catch (IOException e) {
            return "";
        }
    }

    /** Logs status + first 500 chars of the body, key masked as ***. */
    private static void logError(int code, String body, String key) {
        try {
            String masked = body == null ? "" : body;
            if (!key.isEmpty() && !masked.isEmpty()) {
                masked = masked.replace(key, "***");
            }
            if (masked.length() > 500) {
                masked = masked.substring(0, 500);
            }
            Log.w(TAG, "HTTP " + code + " " + masked);
        } catch (Exception ignored) {
            Log.w(TAG, "HTTP " + code + " (unreadable error body)");
        }
    }
}
