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
 * [WHAT] Anthropic Messages API provider.
 * [WHY] Enables cloud AI features using Claude models.
 * [HOW] Uses OkHttp for SSE events. Maps system prompt to top-level field as required by Anthropic.
 */
public class AnthropicProvider implements AiProvider {

    private final String apiKey;
    private final OkHttpClient client;
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

        Request request = new Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
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
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("event: ")) {
                            String event = line.substring(7).trim();
                            String dataLine = reader.readLine();
                            if (dataLine != null && dataLine.startsWith("data: ")) {
                                String data = dataLine.substring(6).trim();
                                try {
                                    if ("content_block_delta".equals(event)) {
                                        JSONObject json = new JSONObject(data);
                                        String token = json.getJSONObject("delta").optString("text", "");
                                        if (!token.isEmpty()) {
                                            mainHandler.post(() -> cb.onToken(token));
                                        }
                                    } else if ("message_stop".equals(event)) {
                                        mainHandler.post(() -> cb.onDone(new AiResponse("", "stop", 0, 0)));
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
