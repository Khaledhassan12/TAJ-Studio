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

public class OpenAiProvider implements AiProvider {

    private final OkHttpClient client;
    private final String apiKey;
    private final String baseUrl;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public OpenAiProvider(String apiKey) {
        this(apiKey, "https://api.openai.com/v1");
    }

    public OpenAiProvider(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override public String id() { return "openai"; }
    @Override public String name() { return "OpenAI"; }

    @Override
    public CapabilityProfile caps() {
        return new CapabilityProfile(true, true, 128000, CapabilityProfile.SystemPromptStyle.MESSAGE);
    }

    @Override
    public StreamHandle stream(AiRequest req, AiStreamCallback cb) {
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
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
                .post(RequestBody.create(body.toString(), mediaType))
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        Call call = client.newCall(request);
        new Thread(() -> executeCall(call, cb)).start();

        return call::cancel;
    }

    private void executeCall(Call call, AiStreamCallback cb) {
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
                StringBuilder fullContent = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            mainHandler.post(() -> cb.onDone(new AiResponse(fullContent.toString(), "stop", 0, 0)));
                            break;
                        }
                        try {
                            JSONObject json = new JSONObject(data);
                            JSONArray choices = json.getJSONArray("choices");
                            if (choices.length() > 0) {
                                JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                                if (delta.has("content")) {
                                    String token = delta.getString("content");
                                    fullContent.append(token);
                                    mainHandler.post(() -> cb.onToken(token));
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
    }

    private AiError mapError(Response response) {
        if (response.code() == 401) return new AiError(AiError.Type.Auth, "Invalid API Key");
        if (response.code() == 429) return new AiError(AiError.Type.RateLimit, "Rate limited");
        return new AiError(AiError.Type.Provider, "HTTP " + response.code());
    }
}
