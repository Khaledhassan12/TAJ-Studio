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

        String[] roles = new String[req.messages.size()];
        String[] contents = new String[req.messages.size()];
        int count = 0;
        for (int i = 0; i < req.messages.size(); i++) {
            AiMessage m = req.messages.get(i);
            if (m.role == AiMessage.Role.error) continue; // Change 4
            roles[count] = m.role.name();
            contents[count] = shapeMessageContent(m);
            count++;
        }
        
        // Resize if we skipped errors
        if (count < req.messages.size()) {
            String[] r = new String[count];
            String[] c = new String[count];
            System.arraycopy(roles, 0, r, 0, count);
            System.arraycopy(contents, 0, c, 0, count);
            roles = r;
            contents = c;
        }

        client.ensureModelAndComplete(modelFile.getAbsolutePath(), req.contextSize, req.mmprojPath, 
                roles, contents, (float) req.temperature, (float) req.topP, req.maxTokens, new RuntimeClient.Callback() {
            @Override public void onToken(String token) { cb.onToken(token); }
            @Override public void onThought(String thought) { cb.onThought(thought); }
            @Override public void onDone(AiResponse usage) { cb.onDone(usage); }
            @Override public void onError(String error) { cb.onError(new AiError(AiError.Type.Native, error)); }
        });

        return client::cancel;
    }

    /**
     * [WHAT] Shapes messages for local models.
     * [WHY] Local models need explicit "Tool call:" / "Tool result:" markers to follow reasoning loops.
     * [HOW] Appends markers to text content; skips error roles.
     */
    private String shapeMessageContent(AiMessage m) {
        if (m.role == AiMessage.Role.assistant && m.toolCallsJson != null) {
            return "Tool call: " + m.toolCallId + "\nArguments: " + m.toolCallsJson;
        } else if (m.role == AiMessage.Role.tool) {
            return "Tool result: " + m.content;
        }
        return m.content;
    }
}
