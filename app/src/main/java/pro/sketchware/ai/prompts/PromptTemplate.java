package pro.sketchware.ai.prompts;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * [WHAT] Data model for a system prompt template.
 * [WHY] Allows users to customize how AI is instructed and how messages are wrapped.
 * [HOW] Multi-section items (System, Prefix, Suffix) with mixed TEXT/VAR types.
 */
public class PromptTemplate {

    public enum ItemType { TEXT, VAR }

    public static class Item {
        public ItemType type;
        public String text;
        public String varKey;

        public Item() {}
        public Item(ItemType type, String textOrKey) {
            this.type = type;
            if (type == ItemType.TEXT) this.text = textOrKey;
            else this.varKey = textOrKey;
        }
    }

    public String id;
    public String title;
    public boolean builtIn;
    public List<Item> system = new ArrayList<>();
    public List<Item> prefix = new ArrayList<>();
    public List<Item> suffix = new ArrayList<>();

    public PromptTemplate() {
        this.id = UUID.randomUUID().toString();
    }

    public PromptTemplate(String title, boolean builtIn) {
        this();
        this.title = title;
        this.builtIn = builtIn;
    }
}
