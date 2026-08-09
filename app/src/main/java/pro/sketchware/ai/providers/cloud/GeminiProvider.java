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
import java.util.concurrent.TimeUnit;

/**
 * [WHAT] Google Gemini API provider.
 * [WHY] Enables cloud AI features using Gemini models.
 * [HOW] Uses OkHttp for SSE streaming. Maps internal AiRequest to Gemini's contents/parts format.
 */
public class GeminiProvider implements AiProvider {

    private final Context context;
    private final String id;
    private final String name;
    private final String fixedKeyId;
    private final OkHttpClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public GeminiProvider(Context context, String id, String name, String fixedKeyId) {
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

    @Override
    public CapabilityProfile caps() {
        return new CapabilityProfile(true, true, 1000000, CapabilityProfile.SystemPromptStyle.TOP_LEVEL_FIELD);
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
            JSONArray contents = new JSONArray();
            for (AiMessage m : req.messages) {
                JSONObject part = new JSONObject().put("text", m.content);
                JSONObject content = new JSONObject()
                        .put("role", m.role == AiMessage.Role.assistant ? "model" : "user")
                        .put("parts", new JSONArray().put(part));
                contents.put(content);
            }
            body.put("contents", contents);

            if (req.systemPrompt != null && !req.systemPrompt.isEmpty()) {
                body.put("systemInstruction", new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject().put("text", req.systemPrompt))));
            }

            JSONObject genConfig = new JSONObject();
            if (req.maxTokens > 0) genConfig.put("maxOutputTokens", req.maxTokens);
            genConfig.put("temperature", req.temperature);
            body.put("generationConfig", genConfig);
        } catch (Exception e) {
            cb.onError(new AiError(AiError.Type.Unknown, e.getMessage()));
            return () -> {};
        }

        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String url = cleanBaseUrl + "/models/" + req.modelId + ":streamGenerateContent?alt=sse&key=" + apiKey;
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
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
                            try {
                                JSONObject json = new JSONObject(data);
                                String token = json.getJSONArray("candidates")
                                        .getJSONObject(0)
                                        .getJSONObject("content")
                                        .getJSONArray("parts")
                                        .getJSONObject(0)
                                        .optString("text", "");
                                if (!token.isEmpty()) {
                                    mainHandler.post(() -> cb.onToken(token));
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
                mainHandler.post(() -> cb.onDone(new AiResponse("", "stop", 0, 0)));
            } catch (IOException e) {
                if (!call.isCanceled()) {
                    mainHandler.post(() -> cb.onError(new AiError(AiError.Type.Network, e.getMessage())));
                }
            }
        }).start();

        return call::cancel;
    }

    private AiError mapError(Response response) {
        if (response.code() == 400) return new AiError(AiError.Type.Provider, "Invalid request or model");
        if (response.code() == 429) return new AiError(AiError.Type.RateLimit, "Rate limited");
        return new AiError(AiError.Type.Provider, "HTTP " + response.code());
    }
}
