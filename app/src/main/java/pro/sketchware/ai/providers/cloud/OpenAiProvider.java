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
 * [WHAT] OpenAI Chat Completions provider.
 * [WHY] Enables cloud AI features using the OpenAI API.
 * [HOW] Uses OkHttp for SSE streaming. Maps internal AiRequest to OpenAI JSON format.
 */
public class OpenAiProvider implements AiProvider {

    private final Context context;
    private final String id;
    private final String name;
    private final String fixedKeyId;
    private final OkHttpClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public OpenAiProvider(Context context, String id, String name, String fixedKeyId) {
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
     * Endpoints (openai-wire compatible):
     * openai     GET {baseUrl}/models -> data[].id
     * openrouter GET {baseUrl}/models -> data[].id
     * deepseek   GET {baseUrl}/models -> data[].id
     * qwen       GET {baseUrl}/models -> data[].id
     * groq       GET {baseUrl}/models -> data[].id
     * ollama     GET {baseUrl}/models -> data[].id
     */
    public List<String> fetchModelIds() throws Exception {
        String baseUrl = ProviderRegistry.get(context).getBaseUrl(id);
        String apiKey = SecureKeyStore.get(context).getKeyForUse(id, fixedKeyId);
        
        if (baseUrl == null) throw new Exception("Base URL not configured");

        String endpoint = baseUrl.endsWith("/") ? baseUrl + "models" : baseUrl + "/models";
        Request.Builder rb = new Request.Builder().url(endpoint).get();
        if (apiKey != null) rb.addHeader("Authorization", "Bearer " + apiKey);
        
        try (Response response = client.newCall(rb.build()).execute()) {
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
        boolean isNativeOpenAi = "openai".equals(id);
        return new CapabilityProfile(true, isNativeOpenAi, 128000, CapabilityProfile.SystemPromptStyle.MESSAGE);
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
            body.put("stream_options", new JSONObject().put("include_usage", true));
            body.put("temperature", req.temperature);
            if (req.maxTokens > 0) body.put("max_tokens", req.maxTokens);

            JSONArray messages = new JSONArray();
            if (req.systemPrompt != null && !req.systemPrompt.isEmpty()) {
                messages.put(new JSONObject().put("role", "system").put("content", req.systemPrompt));
            }
            for (AiMessage m : req.messages) {
                messages.put(new JSONObject().put("role", m.role.name()).put("content", m.content));
            }
            body.put("messages", messages);
        } catch (Exception e) {
            cb.onError(new AiError(AiError.Type.Unknown, e.getMessage()));
            return () -> {};
        }

        String endpoint = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
        Request request = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                .addHeader("Authorization", "Bearer " + apiKey)
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
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) {
                                mainHandler.post(() -> cb.onDone(finalResponse));
                                break;
                            }
                            try {
                                JSONObject json = new JSONObject(data);
                                if (json.has("usage") && !json.isNull("usage")) {
                                    JSONObject usage = json.getJSONObject("usage");
                                    finalResponse.promptTokens = usage.optInt("prompt_tokens");
                                    finalResponse.completionTokens = usage.optInt("completion_tokens");
                                    finalResponse.totalTokens = usage.optInt("total_tokens");
                                    finalResponse.inputTokens = finalResponse.promptTokens;
                                    finalResponse.outputTokens = finalResponse.completionTokens;
                                }

                                JSONArray choices = json.optJSONArray("choices");
                                if (choices != null && choices.length() > 0) {
                                    JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                                    if (delta != null) {
                                        String token = delta.optString("content", "");
                                        if (!token.isEmpty()) {
                                            mainHandler.post(() -> cb.onToken(token));
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
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
