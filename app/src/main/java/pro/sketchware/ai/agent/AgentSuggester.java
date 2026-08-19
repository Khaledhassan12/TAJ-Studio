package pro.sketchware.ai.agent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Scores user intent to suggest Agent mode for project-modifying tasks.
 */
public final class AgentSuggester {

    private static final Set<String> VERBS = new HashSet<>(Arrays.asList(
            "create", "add", "delete", "remove", "build", "compile", "open",
            "move", "copy", "paste", "rename", "redesign", "change",
            "upload", "download", "fix", "implement"
    ));

    private static final Set<String> NOUNS = new HashSet<>(Arrays.asList(
            "activity", "layout", "button", "textview", "component",
            "event", "file", "project", "screen", "widget", "library",
            "image", "manifest", "logcat"
    ));

    public static boolean needsAgent(String text) {
        if (text == null || text.isEmpty()) return false;
        String low = text.toLowerCase();
        String[] words = low.split("\\s+");
        int score = 0;
        for (String w : words) {
            if (VERBS.contains(w)) score++;
            if (NOUNS.contains(w)) score++;
        }
        // Special phrases
        if (low.contains("fix my layout")) score += 2;
        
        return score >= 2;
    }
}
