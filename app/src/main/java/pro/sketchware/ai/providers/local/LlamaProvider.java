package pro.sketchware.ai.providers.local;

import pro.sketchware.ai.core.*;
import pro.sketchware.ai.runtime.RuntimeClient;
import pro.sketchware.ai.data.Paths;
import java.io.File;

public class LlamaProvider implements AiProvider {

    private final RuntimeClient client;

    public LlamaProvider(RuntimeClient client) {
        this.client = client;
    }

    @Override public String id() { return "local-llama"; }
    @Override public String name() { return "Local (Llama)"; }

    @Override
    public CapabilityProfile caps() {
        return new CapabilityProfile(true, false, 4096, CapabilityProfile.SystemPromptStyle.MESSAGE);
    }

    @Override
    public StreamHandle stream(AiRequest req, AiStreamCallback cb) {
        File modelFile = Paths.modelFile(req.modelId);
        if (!modelFile.exists()) {
            cb.onError(new AiError(AiError.Type.Native, "Model file not found"));
            return () -> {};
        }

        // Simplification: local only supports one message or needs concatenation for now
        StringBuilder prompt = new StringBuilder();
        if (req.systemPrompt != null) prompt.append(req.systemPrompt).append("\n\n");
        for (AiMessage m : req.messages) {
            prompt.append(m.role).append(": ").append(m.content).append("\n");
        }
        prompt.append("assistant: ");

        client.ensureModelAndComplete(modelFile.getAbsolutePath(), prompt.toString(), new RuntimeClient.Callback() {
            @Override public void onToken(String token) { cb.onToken(token); }
            @Override public void onDone() { cb.onDone(new AiResponse("", "stop", 0, 0)); }
            @Override public void onError(String error) { cb.onError(new AiError(AiError.Type.Native, error)); }
        });

        return client::cancel;
    }
}
