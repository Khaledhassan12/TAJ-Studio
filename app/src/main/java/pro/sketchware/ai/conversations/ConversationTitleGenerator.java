package pro.sketchware.ai.conversations;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.activities.main.activities.MainActivity;
import pro.sketchware.ai.core.*;
import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.providers.ProviderRegistry;

/**
 * [WHAT] Generates concise conversation titles in the background.
 * [WHY] Improves user organization and history browsing.
 * [HOW] Uses a model to summarize the first exchange; preserves manual renames (Change 6).
 */
public class ConversationTitleGenerator {

    private static final String TAG = "ConvTitleGen";
    private static final String DEFAULT_PLACEHOLDER = "New Chat";
    private final Context context;
    private final AiStorage storage;

    public ConversationTitleGenerator(Context context) {
        this.context = context.getApplicationContext();
        this.storage = AiStorage.get(context);
    }

    public synchronized void maybeGenerateTitle(String scId, String conversationId, List<AiMessage> history) {
        if (!storage.isTitleGenEnabled()) return;

        // Trigger condition
        String currentTitle = storage.kvGet("conv_title_" + conversationId);
        if (currentTitle == null) currentTitle = DEFAULT_PLACEHOLDER;
        
        if (!DEFAULT_PLACEHOLDER.equals(currentTitle)) return;

        AiMessage firstUser = null;
        AiMessage firstAssistant = null;

        for (AiMessage m : history) {
            if (m.role == AiMessage.Role.user && firstUser == null && !m.content.trim().isEmpty()) {
                firstUser = m;
            } else if (m.role == AiMessage.Role.assistant && firstAssistant == null && !m.content.trim().isEmpty()) {
                firstAssistant = m;
            }
        }

        if (firstUser != null && firstAssistant != null) {
            generate(scId, conversationId, firstUser.content, firstAssistant.content);
        }
    }

    private void generate(String scId, String conversationId, String userText, String assistantText) {
        AiProvider provider = resolveProvider(conversationId);
        String modelId = resolveModelId(conversationId, provider);
        if (provider == null || modelId == null) return;

        if (storage.isTitleGenNotificationsEnabled()) {
            postNotification("Generating Title...", "Analyzing conversation context...", false);
        }

        String prompt = storage.getTitleGenPrompt() + "\n\n"
                + "User: " + userText + "\nAssistant: " + (assistantText.length() > 500 ? assistantText.substring(0, 500) : assistantText)
                + "\n\nRespond with ONLY the title text, no quotes, no punctuation, no explanation.";

        AiRequest req = new AiRequest(new ArrayList<>(), prompt, 32, 0.5f, modelId);
        
        provider.stream(req, new AiStreamCallback() {
            private final StringBuilder titleBuffer = new StringBuilder();
            @Override public void onToken(String token) { titleBuffer.append(token); }
            @Override public void onDone(AiResponse response) {
                finalizeTitle(conversationId, titleBuffer.toString(), userText);
            }
            @Override public void onError(AiError error) {
                Log.w(TAG, "Title generation failed: " + error.message);
                finalizeTitle(conversationId, "", userText);
            }
        });
    }

    private synchronized void finalizeTitle(String conversationId, String generated, String fallbackText) {
        String title = generated.replaceAll("\\s+", " ").trim();
        boolean success = !title.isEmpty();
        if (title.isEmpty()) {
            title = fallbackText.replaceAll("\\s+", " ").trim();
        }
        if (title.length() > 60) title = title.substring(0, 60);

        // Race-safe persistence
        String currentTitle = storage.kvGet("conv_title_" + conversationId);
        if (currentTitle == null) currentTitle = DEFAULT_PLACEHOLDER;
        
        if (DEFAULT_PLACEHOLDER.equals(currentTitle)) {
            if (storage.updateTitleIfPlaceholder(conversationId, title, DEFAULT_PLACEHOLDER)) {
                storage.kvPut("conv_title_" + conversationId, title);
                if (storage.isTitleGenNotificationsEnabled()) {
                    if (success) postNotification("Title Generated", title, true);
                    else postNotification("Title Generation Failed", "Used user message snippet instead.", true);
                }
            }
        }
    }

    private void postNotification(String title, String body, boolean autoCancel) {
        String channelId = "fcm_default_channel";
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        NotificationChannel channel = new NotificationChannel(channelId, "Sketchware Pro Notifications", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(channel);

        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_sketchware_24)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(autoCancel)
                .setContentIntent(pi);

        nm.notify(9911, b.build());
    }

    private AiProvider resolveProvider(String conversationId) {
        String pId = storage.kvGet("conv_provider_" + conversationId);
        if (pId == null) return null;
        pro.sketchware.ai.providers.ProviderConfig cfg = ProviderRegistry.get(context).findById(pId);
        if (cfg != null) return ProviderRegistry.get(context).providerFor(cfg, null);
        return null;
    }

    private String resolveModelId(String conversationId, AiProvider provider) {
        String mId = storage.getTitleGenModel();
        if (mId != null) return mId;
        return storage.kvGet("conv_model_" + conversationId);
    }
}
