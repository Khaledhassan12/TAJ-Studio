package pro.sketchware.ai.transcription;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import pro.sketchware.ai.core.AiError;
import pro.sketchware.ai.core.AiMessage;
import pro.sketchware.ai.core.AiProvider;
import pro.sketchware.ai.core.AiRequest;
import pro.sketchware.ai.core.AiResponse;
import pro.sketchware.ai.core.AiStreamCallback;
import pro.sketchware.ai.providers.ProviderConfig;
import pro.sketchware.ai.providers.ProviderRegistry;

/**
 * [WHAT] Engine for performing image transcription.
 * [WHY] P2-IT: Transcribes images using a vision model (R5).
 * [HOW] Batches images, calls provider with prompt + base64 images, concatenates results.
 */
public class ImageTranscriptionEngine {

    public interface Callback {
        void onResult(String transcription);
        void onError(String error);
    }

    private final Context context;
    private final TranscriptionSettings settings;

    public ImageTranscriptionEngine(Context context) {
        this.context = context.getApplicationContext();
        this.settings = TranscriptionSettings.get(context);
    }

    public void maybeTranscribe(List<String> imagePaths, String activeModelId, Callback callback) {
        if (!settings.isEnabled() || imagePaths == null || imagePaths.isEmpty()) {
            callback.onResult("");
            return;
        }

        String transcriptionModelId = settings.getModel();
        List<String> enabledModels = settings.getEnabledModels();

        if (transcriptionModelId == null || !enabledModels.contains(transcriptionModelId)) {
            // If active model is the transcription model, we can use it?
            // Actually spec says "if !enabled || images empty || activeModelId !in enabledModels || model==null => no-op"
            // Wait, "activeModelId !in enabledModels" refers to the model selected FOR TRANSCRIPTION.
            callback.onResult("");
            return;
        }

        String[] parts = transcriptionModelId.split(":", 2);
        if (parts.length != 2) {
            callback.onResult("");
            return;
        }

        String providerId = parts[0];
        String modelName = parts[1];

        ProviderConfig cfg = ProviderRegistry.get(context).findById(providerId);
        if (cfg == null) {
            callback.onResult("");
            return;
        }

        AiProvider provider = ProviderRegistry.get(context).providerFor(cfg, null);
        if (provider == null) {
            callback.onResult("");
            return;
        }

        runTranscription(imagePaths, provider, modelName, callback);
    }

    private void runTranscription(List<String> imagePaths, AiProvider provider, String modelName, Callback callback) {
        int batchSize = settings.getBatchSize();
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < imagePaths.size(); i += batchSize) {
            batches.add(imagePaths.subList(i, Math.min(i + batchSize, imagePaths.size())));
        }

        StringBuilder fullDescription = new StringBuilder();
        AtomicInteger remaining = new AtomicInteger(batches.size());

        for (List<String> batch : batches) {
            List<AiMessage> messages = new ArrayList<>();
            AiMessage msg = new AiMessage(AiMessage.Role.user, settings.getPrompt());
            msg.imagePaths = batch;
            messages.add(msg);

            AiRequest req = new AiRequest(messages, null, 1024, 0.0f, modelName);
            req.imagePaths = batch;

            provider.stream(req, new AiStreamCallback() {
                StringBuilder batchResult = new StringBuilder();

                @Override
                public void onToken(String token) {
                    batchResult.append(token);
                }

                @Override
                public void onThought(String thought) {}

                @Override
                public void onDone(AiResponse usage) {
                    synchronized (fullDescription) {
                        if (fullDescription.length() > 0) fullDescription.append("\n\n");
                        fullDescription.append(batchResult.toString());
                    }
                    if (remaining.decrementAndGet() == 0) {
                        callback.onResult(fullDescription.toString());
                    }
                }

                @Override
                public void onError(AiError error) {
                    if (remaining.getAndSet(0) > 0) {
                        callback.onError(error.message);
                    }
                }
            });
        }
    }

    public static String encodeImageToBase64(String path) {
        File file = new File(path);
        if (!file.exists()) return null;

        Bitmap bitmap = BitmapFactory.decodeFile(path);
        if (bitmap == null) return null;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] bytes = baos.toByteArray();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
}
