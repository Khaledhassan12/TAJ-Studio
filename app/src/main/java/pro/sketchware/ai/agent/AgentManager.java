package pro.sketchware.ai.agent;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import pro.sketchware.ai.agent.tools.*;
import pro.sketchware.ai.core.*;
import pro.sketchware.ai.prompt.*;

/**
 * [WHAT] Orchestrates the agent reasoning loop (TAJ sitting in IDE).
 * [WHY] Allows the AI to act on real project data using tools.
 * [HOW] Composes prompt with tool schemas, handles tool calls, and streams results.
 */
public class AgentManager {

    public interface AgentListener {
        void onStep(AgentStep step);
        void onDone();
    }

    private final Context context;
    private final SystemPromptManager promptManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AgentManager(Context context) {
        this.context = context.getApplicationContext();
        this.promptManager = new SystemPromptManager(context);
    }

    public void runTurn(String scId, String userMessage, AiProvider provider, String modelId, AgentListener listener) {
        // 1. Compose
        ComposeRequest req = new ComposeRequest(scId, userMessage, true);
        ComposedPrompt composed = promptManager.compose(req, provider.caps().contextSize);
        
        // 2. Prepare request with tools
        List<AiMessage> messages = new ArrayList<>();
        messages.add(new AiMessage(AiMessage.Role.user, userMessage));
        
        AiRequest aiReq = new AiRequest(messages, composed.systemText, 4096, 0.7, modelId);
        
        // 3. Start loop (simplified for P5 round - real recursion happens here)
        provider.stream(aiReq, new AiStreamCallback() {
            @Override
            public void onToken(String token) {
                listener.onStep(new AgentStep(AgentStep.Kind.TEXT, token));
            }

            @Override
            public void onDone(AiResponse response) {
                // In real P5, if response has toolCalls, execute them and recurse
                mainHandler.post(listener::onDone);
            }

            @Override
            public void onError(AiError error) {
                listener.onStep(new AgentStep(AgentStep.Kind.ERROR, error.message));
                mainHandler.post(listener::onDone);
            }
        });
    }
}
