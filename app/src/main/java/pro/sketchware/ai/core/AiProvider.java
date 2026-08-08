package pro.sketchware.ai.core;

public interface AiProvider {
    String id();
    String name();
    CapabilityProfile caps();
    StreamHandle stream(AiRequest req, AiStreamCallback cb);
}
