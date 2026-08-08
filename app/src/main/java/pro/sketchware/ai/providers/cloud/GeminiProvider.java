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

public class GeminiProvider implements AiProvider {

    private final OkHttpClient client;
    private final String apiKey;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public GeminiProvider(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override public String id() { return "gemini"; }
    @Override public String name() { return "Gemini"; }

    @Override
    public CapabilityProfile caps() {
        return new CapabilityProfile(true, true, 1000000, CapabilityProfile.SystemPromptStyle.TOP_LEVEL_FIELD);
    }

    @Override
    public StreamHandle stream(AiRequest req, AiStreamCallback cb) {
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
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
                body.put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", req.systemPrompt))));
            }

            JSONObject genConfig = new JSONObject();
            if (req.maxTokens > 0) genConfig.put("maxOutputTokens", req.maxTokens);
            genConfig.put("temperature", req.temperature);
            body.put("generationConfig", genConfig);
        } catch (Exception e) {
            cb.onError(new AiError(AiError.Type.Unknown, e.getMessage()));
            return () -> {};
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + req.modelId + ":streamGenerateContent?alt=sse&key=" + apiKey;
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body.toString(), mediaType))
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
                        try {
                            JSONObject json = new JSONObject(data);
                            JSONArray candidates = json.getJSONArray("candidates");
                            if (candidates.length() > 0) {
                                JSONObject content = candidates.getJSONObject(0).getJSONObject("content");
                                JSONArray parts = content.getJSONArray("parts");
                                if (parts.length() > 0) {
                                    String token = parts.getJSONObject(0).getString("text");
                                    fullContent.append(token);
                                    mainHandler.post(() -> cb.onToken(token));
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
                mainHandler.post(() -> cb.onDone(new AiResponse(fullContent.toString(), "stop", 0, 0)));
            }
        } catch (IOException e) {
            if (!call.isCanceled()) {
                mainHandler.post(() -> cb.onError(new AiError(AiError.Type.Network, e.getMessage())));
            }
        }
    }

    private AiError mapError(Response response) {
        if (response.code() == 400) return new AiError(AiError.Type.Provider, "Invalid request or model");
        if (response.code() == 429) return new AiError(AiError.Type.RateLimit, "Rate limited");
        return new AiError(AiError.Type.Provider, "HTTP " + response.code());
    }
}
