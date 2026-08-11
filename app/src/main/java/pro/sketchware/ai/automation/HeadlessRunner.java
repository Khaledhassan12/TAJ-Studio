package pro.sketchware.ai.automation;

import android.content.ContentValues;
import android.content.Context;
import android.util.Log;

import java.util.UUID;

import pro.sketchware.ai.agent.AgentManager;
import pro.sketchware.ai.agent.AgentStep;
import pro.sketchware.ai.core.AiProvider;
import pro.sketchware.ai.core.AiResponse;
import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.providers.ProviderConfig;
import pro.sketchware.ai.providers.ProviderRegistry;

/**
 * [WHAT] Headless execution of one prompt inside a persisted conversation
 * (P2-AU, D17). Used by task/loop fires.
 * [WHY] Replicates the SAME send choke point the UI uses (SessionFragment):
 * persist user message via AiStorage.insertMessage -> AgentManager.runTurn
 * -> persist assistant message. Provider/model come from the conversation
 * binding kv ("conv_provider_<id>" / "conv_model_<id>"). Conversations bound
 * to local-llama cannot run headless (the runtime needs the UI session) —
 * that failure is reported honestly, never faked (§11/§16).
 */
public class HeadlessRunner {

    public static final String TAG = "HeadlessRunner";

    public interface Callback {
        void onFinished(boolean ok, String summary);
    }

    public static void run(Context context, String scId, String conversationId,
                           String prompt, String modelIdOverride, Callback callback) {
        Context appCtx = context.getApplicationContext();
        AiStorage storage = AiStorage.get(appCtx);

        String providerId = storage.kvGet("conv_provider_" + conversationId);
        String modelId = modelIdOverride != null && !modelIdOverride.isEmpty()
                ? modelIdOverride
                : storage.kvGet("conv_model_" + conversationId);

        if (providerId == null || modelId == null) {
            String err = "conversation has no bound provider/model (provider=" + providerId
                    + ", model=" + modelId + ")";
            Log.w(TAG, "run failed: " + err);
            if (callback != null) callback.onFinished(false, err);
            return;
        }
        if ("local-llama".equals(providerId)) {
            String err = "local model conversations cannot run headless (runtime UI session required)";
            Log.w(TAG, "run failed: " + err);
            if (callback != null) callback.onFinished(false, err);
            return;
        }

        ProviderConfig cfg = ProviderRegistry.get(appCtx).findById(providerId);
        if (cfg == null) {
            String err = "provider not found: " + providerId;
            Log.w(TAG, "run failed: " + err);
            if (callback != null) callback.onFinished(false, err);
            return;
        }
        AiProvider provider = ProviderRegistry.get(appCtx).providerFor(cfg, null);

        // Same choke point as the UI: persist the user message FIRST.
        ContentValues cv = new ContentValues();
        cv.put("id", UUID.randomUUID().toString());
        cv.put("conversationId", conversationId);
        cv.put("role", "user");
        cv.put("content", prompt);
        cv.put("createdAt", System.currentTimeMillis());
        storage.insertMessage(cv);

        final StringBuilder reply = new StringBuilder();
        final StringBuilder lastError = new StringBuilder();

        new AgentManager(appCtx).runTurn(scId, conversationId, prompt, null, provider, modelId,
                new AgentManager.AgentListener() {
                    @Override
                    public void onStep(AgentStep step) {
                        if (step.kind == AgentStep.Kind.TEXT) {
                            reply.append(step.payload);
                        } else if (step.kind == AgentStep.Kind.ERROR) {
                            lastError.append(step.payload);
                        }
                    }

                    @Override
                    public void onDone(AiResponse usage) {
                        boolean ok = usage != null && reply.length() > 0;
                        if (ok) {
                            // Same choke point as the UI: persist assistant reply.
                            ContentValues am = new ContentValues();
                            am.put("id", UUID.randomUUID().toString());
                            am.put("conversationId", conversationId);
                            am.put("role", "assistant");
                            am.put("content", reply.toString());
                            am.put("createdAt", System.currentTimeMillis());
                            storage.insertMessage(am);
                            String summary = "reply persisted (" + reply.length() + " chars)";
                            Log.i(TAG, "run done: conversation=" + conversationId + " " + summary);
                            if (callback != null) callback.onFinished(true, summary);
                        } else {
                            String err = usage == null
                                    ? "provider error: " + (lastError.length() > 0 ? lastError : "unknown")
                                    : "empty reply from model";
                            Log.w(TAG, "run failed: " + err);
                            if (callback != null) callback.onFinished(false, err);
                        }
                    }
                });
    }
}
