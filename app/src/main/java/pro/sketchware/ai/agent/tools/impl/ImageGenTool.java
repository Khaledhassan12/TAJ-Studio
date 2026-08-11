package pro.sketchware.ai.agent.tools.impl;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import pro.sketchware.ai.agent.tools.Tool;
import pro.sketchware.ai.agent.tools.ToolArgs;
import pro.sketchware.ai.agent.tools.ToolCtx;
import pro.sketchware.ai.agent.tools.ToolResult;
import pro.sketchware.ai.agent.tools.ToolSpec;
import pro.sketchware.ai.data.Paths;
import pro.sketchware.ai.data.SecureKeyStore;
import pro.sketchware.ai.images.ImageGenSettings;
import pro.sketchware.ai.models.ModelCatalog;
import pro.sketchware.ai.providers.ProviderConfig;
import pro.sketchware.ai.providers.ProviderRegistry;

/**
 * [WHAT] Agent tool "generate_image": creates a real image via an
 * OpenAI-protocol provider and saves it into the project's AI folder.
 * [WHY] P2-IG: real wiring per §11 (no fake UI); honest errors per R7.
 * [HOW] Reuses the OpenAiProvider request recipe: ProviderRegistry.getBaseUrl +
 * SecureKeyStore.getKeyForUse + OkHttp JSON post with Bearer auth. Response
 * b64_json is decoded and saved under
 * .sketchware/ai/projects/&lt;scId&gt;/images/img_&lt;ts&gt;.png.
 * NEVER logs keys or auth headers (RISK-4).
 *
 * [العربية]
 * أداة الوكيل "generate_image": تولّد صورة حقيقية عبر مزود ببروتوكول OpenAI
 * وتحفظها في مجلد مشروع الذكاء الاصطناعي. تعيد أخطاء صادقة ولا تسجل المفاتيح أبداً.
 */
public class ImageGenTool implements Tool {

    private static final String TAG = "ImageGenTool";

    @Override
    public ToolSpec spec() {
        return new ToolSpec("generate_image",
                "Generate an image from a text prompt using the configured image model; returns the saved file path",
                "{\"prompt\": \"string\"}");
    }

    @Override
    public ToolResult execute(ToolArgs args, ToolCtx ctx) {
        String prompt = args.getString("prompt");
        if (prompt == null || prompt.trim().isEmpty()) {
            return ToolResult.error("prompt is required");
        }

        Context appContext = ctx.context.getApplicationContext();
        ImageGenSettings settings = ImageGenSettings.get(appContext);
        if (!settings.isEnabled()) {
            return ToolResult.error("Image Generation is disabled");
        }

        // 1. Resolve the image model (explicit selection wins, else first
        // enabled model matching the image keywords).
        ModelCatalog catalog = ModelCatalog.get(appContext);
        awaitCatalogLoad(catalog);

        String providerId;
        String modelName;
        String modelKey = settings.getModel();
        if (modelKey != null) {
            String[] parts = modelKey.split(":", 2);
            if (parts.length != 2) {
                return ToolResult.error("Stored image model is malformed");
            }
            providerId = parts[0];
            modelName = parts[1];
        } else {
            ModelCatalog.ModelEntry fallback = null;
            for (ModelCatalog.ModelEntry e : catalog.usableModels()) {
                if (ImageGenSettings.matchesImageKeywords(e.modelId) || ImageGenSettings.matchesImageKeywords(e.alias)) {
                    fallback = e;
                    break;
                }
            }
            if (fallback == null) {
                return ToolResult.error("No image model configured/enabled");
            }
            providerId = fallback.providerId;
            modelName = fallback.modelId;
        }

        // 2. Provider must speak the OpenAI protocol (openai/openrouter/custom-openai).
        ProviderRegistry providerRegistry = ProviderRegistry.get(appContext);
        ProviderConfig cfg = providerRegistry.findById(providerId);
        if (cfg == null) {
            return ToolResult.error("Provider not found: " + providerId);
        }
        if (!"openai-wire".equals(cfg.wireProtocol)) {
            return ToolResult.error("Provider '" + cfg.displayName + "' does not support image generation (OpenAI-protocol required)");
        }

        String baseUrl = providerRegistry.getBaseUrl(providerId);
        if (baseUrl == null) {
            return ToolResult.error("Base URL not configured for " + cfg.displayName);
        }
        String apiKey = SecureKeyStore.get(appContext).getKeyForUse(providerId, null);
        if (apiKey == null && !cfg.worksWithoutKey) {
            return ToolResult.error("No API key configured for " + cfg.displayName);
        }

        // 3. Build the request (same recipe as OpenAiProvider.stream).
        String endpoint = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        endpoint += endpoint.contains("/v1") ? "/images/generations" : "/v1/images/generations";

        String size = settings.getSizeW() + "x" + settings.getSizeH();
        JSONObject body = new JSONObject();
        try {
            body.put("model", modelName);
            body.put("prompt", prompt);
            body.put("size", size);
            body.put("response_format", "b64_json");
            body.put("n", 1);
        } catch (Exception e) {
            return ToolResult.error("Failed to build image request: " + e.getMessage());
        }

        // Body only — no Authorization header is ever logged (RISK-4).
        Log.i(TAG, "generate_image POST " + endpoint + " body=" + body);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();

        Request.Builder rb = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")));
        if (apiKey != null) {
            rb.addHeader("Authorization", "Bearer " + apiKey);
        }

        // 4. Execute + honest error surfacing (R7).
        try (Response response = client.newCall(rb.build()).execute()) {
            if (!response.isSuccessful()) {
                return mapHttpError(response);
            }
            if (response.body() == null) {
                return ToolResult.error("Empty response from provider");
            }

            JSONObject json = new JSONObject(response.body().string());
            JSONArray data = json.optJSONArray("data");
            if (data == null || data.length() == 0) {
                return ToolResult.error("Provider returned no image data");
            }
            String b64 = data.getJSONObject(0).optString("b64_json", null);
            if (b64 == null || b64.isEmpty()) {
                return ToolResult.error("Provider returned no b64_json payload");
            }

            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            File dir = new File(Paths.projectDir(ctx.scId), "images");
            if (!dir.exists() && !dir.mkdirs()) {
                return ToolResult.error("Failed to create images directory");
            }
            File out = new File(dir, "img_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(bytes);
            }

            Log.i(TAG, "generate_image saved: " + out.getAbsolutePath());
            return ToolResult.success("Image saved: " + out.getAbsolutePath());
        } catch (SocketTimeoutException e) {
            return ToolResult.error("Image generation timed out");
        } catch (IOException e) {
            return ToolResult.error("Network error during image generation: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Image generation failed: " + e.getMessage());
        }
    }

    private void awaitCatalogLoad(ModelCatalog catalog) {
        CountDownLatch latch = new CountDownLatch(1);
        catalog.load(latch::countDown);
        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ToolResult mapHttpError(Response response) {
        int code = response.code();
        String detail = "";
        try {
            if (response.body() != null) {
                String raw = response.body().string();
                JSONObject json = new JSONObject(raw);
                JSONObject error = json.optJSONObject("error");
                if (error != null) detail = error.optString("message", "");
            }
        } catch (Exception ignored) {
        }
        String suffix = detail.isEmpty() ? "" : " — " + detail;
        if (code == 401 || code == 403) {
            return ToolResult.error("Invalid API key for image generation (HTTP " + code + ")" + suffix);
        }
        if (code == 429) {
            return ToolResult.error("Rate limited by provider (HTTP 429)" + suffix);
        }
        return ToolResult.error("Image generation failed (HTTP " + code + ")" + suffix);
    }
}
