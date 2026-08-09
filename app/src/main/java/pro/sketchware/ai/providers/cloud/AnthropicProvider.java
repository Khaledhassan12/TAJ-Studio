package pro.sketchware.ai.providers.cloud;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import pro.sketchware.ai.core.*;
import pro.sketchware.ai.data.SecureKeyStore;
import pro.sketchware.ai.providers.ProviderRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * [WHAT] Anthropic Messages API provider.
 * [WHY] Enables cloud AI features using Claude models.
 * [HOW] Uses OkHttp for SSE events. Maps system prompt to top-level field as required by Anthropic.
 */
public class AnthropicProvider implements AiProvider {

    private final Context context;
    private final String id;
    private final String name;
    private final String fixedKeyId;
    private final OkHttpClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AnthropicProvider(Context context, String id, String name, String fixedKeyId) {
        this.context = context.getApplicationContext();
        this.id = id;
        this.name = name;
        this.fixedKeyId = fixedKeyId;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override public String id() { return id; }
    @Override public String name() { return name; }

    /**
     * [Step 1] Real fetchModelIds() implementation.
     * anthropic  GET {baseUrl}/models (x-api-key + anthropic-version:2023-06-01) -> data[].id
     */
    public List<String> fetchModelIds() throws Exception {
        String baseUrl = ProviderRegistry.get(context).getBaseUrl(id);
        String apiKey = SecureKeyStore.get(context).getKeyForUse(id, fixedKeyId);

        if (apiKey == null || baseUrl == null) throw new Exception("AuthException: API Key or Base URL not configured");

        String endpoint = baseUrl.endsWith("/") ? baseUrl + "models" : baseUrl + "/models";
        Request request = new Request.Builder()
                .url(endpoint)
                .get()
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 401 || response.code() == 403) throw new Exception("AuthException: Invalid key");
            if (response.code() == 429) throw new Exception("RateLimitException: Too many requests");
            if (!response.isSuccessful()) throw new Exception("Provider error: HTTP " + response.code());

            JSONObject json = new JSONObject(response.body().string());
            JSONArray data = json.getJSONArray("data");
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) {
                ids.add(data.getJSONObject(i).getString("id"));
            }
            return ids;
        }
    }

    @Override
    public CapabilityProfile caps() {
        return new CapabilityProfile(true, true, 200000, CapabilityProfile.SystemPromptStyle.TOP_LEVEL_FIELD);
    }

    @Override
    public StreamHandle stream(AiRequest req, AiStreamCallback cb) {
        // Resolve at call time (Step 1)
        String baseUrl = ProviderRegistry.get(context).getBaseUrl(id);
        String apiKey = SecureKeyStore.get(context).getKeyForUse(id, fixedKeyId);

        if (apiKey == null || baseUrl == null) {
            cb.onError(new AiError(AiError.Type.Auth, "API Key or Base URL not configured"));
            return () -> {};
        }

        JSONObject body = new JSONObject();
        try {
            body.put("model", req.modelId);
            body.put("stream", true);
            if (req.maxTokens > 0) body.put("max_tokens", req.maxTokens);
            else body.put("max_tokens", 4096);

            if (req.systemPrompt != null && !req.systemPrompt.isEmpty()) {
                body.put("system", req.systemPrompt);
            }

            JSONArray messages = new JSONArray();
            for (AiMessage m : req.messages) {
                if (m.role == AiMessage.Role.user || m.role == AiMessage.Role.assistant) {
                    messages.put(new JSONObject().put("role", m.role.name()).put("content", m.content));
                }
            }
            body.put("messages", messages);
        } catch (Exception e) {
            cb.onError(new AiError(AiError.Type.Unknown, e.getMessage()));
            return () -> {};
        }

        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        Request request = new Request.Builder()
                .url(cleanBaseUrl + "/messages")
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .build();

        Call call = client.newCall(request);
        new Thread(() -> {
            try (Response response = call.execute()) {
                if (!response.isSuccessful()) {
                    AiError error = mapError(response);
                    mainHandler.post(() -> cb.onError(error));
                    return;
                }
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    mainHandler.post(() -> cb.onError(new AiError(AiError.Type.Provider, "Empty response")));
                    return;
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream()))) {
                    String line;
                    AiResponse finalResponse = new AiResponse("", "stop", 0, 0);
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("event: ")) {
                            String event = line.substring(7).trim();
                            String dataLine = reader.readLine();
                            if (dataLine != null && dataLine.startsWith("data: ")) {
                                String data = dataLine.substring(6).trim();
                                try {
                                    JSONObject json = new JSONObject(data);
                                    if ("message_start".equals(event)) {
                                        JSONObject msg = json.getJSONObject("message");
                                        JSONObject usage = msg.optJSONObject("usage");
                                        if (usage != null) {
                                            finalResponse.inputTokens = usage.optInt("input_tokens");
                                            finalResponse.outputTokens = usage.optInt("output_tokens");
                                        }
                                    } else if ("content_block_delta".equals(event)) {
                                        String token = json.getJSONObject("delta").optString("text", "");
                                        if (!token.isEmpty()) {
                                            mainHandler.post(() -> cb.onToken(token));
                                        }
                                    } else if ("message_delta".equals(event)) {
                                        JSONObject usage = json.optJSONObject("usage");
                                        if (usage != null) {
                                            finalResponse.outputTokens = usage.optInt("output_tokens");
                                        }
                                    } else if ("message_stop".equals(event)) {
                                        finalResponse.promptTokens = finalResponse.inputTokens != null ? finalResponse.inputTokens : 0;
                                        finalResponse.completionTokens = finalResponse.outputTokens != null ? finalResponse.outputTokens : 0;
                                        finalResponse.totalTokens = finalResponse.promptTokens + finalResponse.completionTokens;
                                        mainHandler.post(() -> cb.onDone(finalResponse));
                                        break;
                                    }
                                } catch (org.json.JSONException ignored) {}
                            }
                        }
                    }
                }
            } catch (IOException e) {
                if (!call.isCanceled()) {
                    mainHandler.post(() -> cb.onError(new AiError(AiError.Type.Network, e.getMessage())));
                }
            }
        }).start();

        return call::cancel;
    }

    private AiError mapError(Response response) {
        if (response.code() == 401) return new AiError(AiError.Type.Auth, "Invalid API Key");
        if (response.code() == 429) return new AiError(AiError.Type.RateLimit, "Rate limited");
        return new AiError(AiError.Type.Provider, "HTTP " + response.code());
    }
}
