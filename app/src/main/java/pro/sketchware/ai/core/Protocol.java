package pro.sketchware.ai.core;

/**
 * The three wire protocols TAG Assistant speaks. Every provider in the catalog
 * maps to exactly one of these; "compatible" providers only differ in base URL,
 * auth header and a few optional extra headers.
 */
public enum Protocol {
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    GEMINI
}
