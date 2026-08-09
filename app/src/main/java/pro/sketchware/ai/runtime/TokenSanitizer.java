package pro.sketchware.ai.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * [WHAT] Rolling buffer to strip stop-patterns from the token stream.
 * [WHY] Local models often leak ChatML tags or special tokens at the end of generation.
 * [HOW] Buffers pieces until they either match a pattern or are guaranteed safe to emit.
 */
public class TokenSanitizer {

    public interface Listener {
        void onToken(String token);
        void onStopSignalled();
    }

    private static final String[] STOP_PATTERNS = {"<|im_end|>", "<|im_start|>", "</s>", "</s>"};
    private final StringBuilder buffer = new StringBuilder();
    private final Listener listener;
    private final int maxPatternLen;
    private boolean stopped = false;

    public TokenSanitizer(Listener listener) {
        this.listener = listener;
        int max = 0;
        for (String p : STOP_PATTERNS) max = Math.max(max, p.length());
        this.maxPatternLen = max;
    }

    public synchronized void feed(String piece) {
        if (stopped) return;
        buffer.append(piece);
        checkBuffer();
    }

    public synchronized void flush() {
        if (!stopped && buffer.length() > 0) {
            listener.onToken(buffer.toString());
            buffer.setLength(0);
        }
    }

    private void checkBuffer() {
        String current = buffer.toString();
        for (String pattern : STOP_PATTERNS) {
            int idx = current.indexOf(pattern);
            if (idx != -1) {
                listener.onToken(current.substring(0, idx));
                buffer.setLength(0);
                stopped = true;
                listener.onStopSignalled();
                return;
            }
        }

        // If buffer is too long, we can safely emit the start of it
        // Keep at least maxPatternLen * 2 to catch patterns split across many pieces
        int safetyMargin = maxPatternLen * 2;
        if (buffer.length() > safetyMargin) {
            int toEmit = buffer.length() - safetyMargin;
            listener.onToken(buffer.substring(0, toEmit));
            buffer.delete(0, toEmit);
        }
    }
}
