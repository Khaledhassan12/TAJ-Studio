package pro.sketchware.ai.runtime;

/**
 * [WHAT] State-machine parser to split thoughts from text.
 * [WHY] Modern reasoning models (e.g. DeepSeek-R1) emit reasoning in <think> tags.
 * [HOW] Tracks tag status across token boundaries and routes to appropriate listeners.
 */
public class ThinkingParser {

    public interface Listener {
        void onText(String text);
        void onThought(String thought, String titleOrNull);
    }

    private final Listener listener;
    private boolean inThought = false;
    private final StringBuilder tagBuffer = new StringBuilder();
    
    // We expect <think> and </think>
    private static final String START_TAG = "<think>";
    private static final String END_TAG = "</think>";

    public ThinkingParser(Listener listener) {
        this.listener = listener;
    }

    public synchronized void feed(String piece) {
        // Simple state machine: if not in thought, look for <think>
        // if in thought, look for </think>
        // Token boundaries might split tags, so we use a rolling window or check piece content
        
        String combined = tagBuffer.append(piece).toString();
        tagBuffer.setLength(0);

        int searchIdx = 0;
        while (searchIdx < combined.length()) {
            if (!inThought) {
                int startIdx = combined.indexOf(START_TAG, searchIdx);
                if (startIdx != -1) {
                    // Emit text before tag
                    if (startIdx > searchIdx) {
                        listener.onText(combined.substring(searchIdx, startIdx));
                    }
                    inThought = true;
                    searchIdx = startIdx + START_TAG.length();
                } else {
                    // No start tag found. But wait, we might have a partial tag at the end!
                    // If the string ends with a prefix of START_TAG, buffer it.
                    int possibleStart = findPartialTag(combined, START_TAG, searchIdx);
                    if (possibleStart != -1) {
                        listener.onText(combined.substring(searchIdx, possibleStart));
                        tagBuffer.append(combined.substring(possibleStart));
                        return;
                    } else {
                        listener.onText(combined.substring(searchIdx));
                        return;
                    }
                }
            } else {
                int endIdx = combined.indexOf(END_TAG, searchIdx);
                if (endIdx != -1) {
                    // Emit thought content
                    if (endIdx > searchIdx) {
                        listener.onThought(combined.substring(searchIdx, endIdx), null);
                    }
                    inThought = false;
                    searchIdx = endIdx + END_TAG.length();
                } else {
                    // No end tag found. Buffer partial or emit thought.
                    int possibleEnd = findPartialTag(combined, END_TAG, searchIdx);
                    if (possibleEnd != -1) {
                        listener.onThought(combined.substring(searchIdx, possibleEnd), null);
                        tagBuffer.append(combined.substring(possibleEnd));
                        return;
                    } else {
                        listener.onThought(combined.substring(searchIdx), null);
                        return;
                    }
                }
            }
        }
    }

    private int findPartialTag(String str, String tag, int from) {
        for (int i = 1; i < tag.length(); i++) {
            String prefix = tag.substring(0, tag.length() - i);
            if (str.endsWith(prefix)) {
                return str.length() - prefix.length();
            }
        }
        return -1;
    }

    public synchronized void flush() {
        if (tagBuffer.length() > 0) {
            if (inThought) listener.onThought(tagBuffer.toString(), null);
            else listener.onText(tagBuffer.toString());
            tagBuffer.setLength(0);
        }
    }
}
