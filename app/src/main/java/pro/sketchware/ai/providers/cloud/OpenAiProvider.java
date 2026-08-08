package pro.sketchware.ai.providers.cloud;

import android.os.Handler;
import android.os.Looper;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import pro.sketchware.ai.core.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * [WHAT] OpenAI Chat Completions provider.
 * [WHY] Enables cloud AI features using the OpenAI API.
 * [HOW] Uses OkHttp for SSE streaming. Maps internal AiRequest to OpenAI JSON format.
 */
public class OpenAiProvider implements AiProvider {

    private final String id;
    private final String name;
    private final String apiKey;
    private final String baseUrl;
    private final OkHttpClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public OpenAiProvider(String apiKey) {
        this("openai", "OpenAI", apiKey, "https://api.openai.com/v1");
    }

    public OpenAiProvider(String id, String name, String apiKey, String baseUrl) {
        this.id = id;
        this.name = name;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override public String id() { return id; }
    @Override public String name() { return name; }

    @Override
    public CapabilityProfile caps() {
        // Real OpenAI caps: supports streaming, tools, and large context.
        return new CapabilityProfile(true, true, 128000, CapabilityProfile.SystemPromptStyle.MESSAGE);
    }

    @Override
    public StreamHandle stream(AiRequest req, AiStreamCallback cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("model", req.modelId);
            body.put("stream", true);
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

        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
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
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) {
                                mainHandler.post(() -> cb.onDone(new AiResponse("", "stop", 0, 0)));
                                break;
                            }
                            try {
                                JSONObject json = new JSONObject(data);
                                String token = json.getJSONArray("choices")
                                        .getJSONObject(0)
                                        .getJSONObject("delta")
                                        .optString("content", "");
                                if (!token.isEmpty()) {
                                    mainHandler.post(() -> cb.onToken(token));
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
