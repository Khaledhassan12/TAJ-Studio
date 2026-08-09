package pro.sketchware.ai.models;

/**
 * [WHAT] A saved system prompt template.
 * [WHY] Allows users to reuse specific personas or instruction sets.
 * [HOW] Data class with name and content.
 */
public class SavedPrompt {
    public String id;
    public String name;
    public String content;

    public SavedPrompt() {}

    public SavedPrompt(String id, String name, String content) {
        this.id = id;
        this.name = name;
        this.content = content;
    }
}
