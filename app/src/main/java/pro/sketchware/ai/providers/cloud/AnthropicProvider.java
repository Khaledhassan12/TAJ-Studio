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

public class AnthropicProvider implements AiProvider {

    private final OkHttpClient client;
    private final String apiKey;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AnthropicProvider(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override public String id() { return "anthropic"; }
    @Override public String name() { return "Anthropic"; }

    @Override
    public CapabilityProfile caps() {
        return new CapabilityProfile(true, true, 200000, CapabilityProfile.SystemPromptStyle.TOP_LEVEL_FIELD);
    }

    @Override
    public StreamHandle stream(AiRequest req, AiStreamCallback cb) {
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        JSONObject body = new JSONObject();
        try {
            body.put("model", req.modelId);
            body.put("stream", true);
            if (req.maxTokens > 0) body.put("max_tokens", req.maxTokens);
            if (req.systemPrompt != null && !req.systemPrompt.isEmpty()) {
                body.put("system", req.systemPrompt);
            }

            JSONArray messages = new JSONArray();
            for (AiMessage m : req.messages) {
                messages.put(new JSONObject().put("role", m.role.name()).put("content", m.content));
            }
            body.put("messages", messages);
        } catch (Exception e) {
            cb.onError(new AiError(AiError.Type.Unknown, e.getMessage()));
            return () -> {};
        }

        Request request = new Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .post(RequestBody.create(body.toString(), mediaType))
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
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
                    if (line.startsWith("event: ")) {
                        String event = line.substring(7).trim();
                        String dataLine = reader.readLine();
                        if (dataLine != null && dataLine.startsWith("data: ")) {
                            String data = dataLine.substring(6).trim();
                            if ("content_block_delta".equals(event)) {
                                JSONObject json = new JSONObject(data);
                                JSONObject delta = json.getJSONObject("delta");
                                if (delta.has("text")) {
                                    String token = delta.getString("text");
                                    fullContent.append(token);
                                    mainHandler.post(() -> cb.onToken(token));
                                }
                            } else if ("message_stop".equals(event)) {
                                mainHandler.post(() -> cb.onDone(new AiResponse(fullContent.toString(), "stop", 0, 0)));
                                break;
                            }
                        }
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
