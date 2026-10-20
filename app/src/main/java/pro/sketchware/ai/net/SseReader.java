package pro.sketchware.ai.net;

import java.io.IOException;

import okio.BufferedSource;

/**
 * Minimal, robust Server-Sent-Events reader over an okio stream.
 * Handles multi-line data payloads, "event:" names, ":" keep-alive comments,
 * and the OpenAI "[DONE]" sentinel. Returns null when the stream closes.
 */
public final class SseReader {

    /** One fully received SSE frame. */
    public static final class SseEvent {
        /** Event name from "event:" lines; null when the server sent none. */
        public final String name;
        /** Concatenated "data:" payload lines. */
        public final String data;

        SseEvent(String name, String data) {
            this.name = name;
            this.data = data;
        }

        public boolean isDone() {
            return "[DONE]".equals(data);
        }

        public boolean hasData() {
            return !data.isEmpty();
        }
    }

    private final BufferedSource source;
    private static final int MAX_EVENT_CHARS = 1024 * 1024;
    private static final int MAX_EVENT_NAME_CHARS = 256;

    private final StringBuilder dataBuffer = new StringBuilder();
    private String eventName = null;

    public SseReader(BufferedSource source) {
        this.source = source;
    }

    /**
     * Reads frames until one complete event is available.
     *
     * @return the next event, or null when the stream ended.
     */
    public SseEvent nextEvent() throws IOException {
        while (true) {
            String line = source.readUtf8Line();
            if (line == null) {
                // Stream closed; flush any dangling frame before giving up.
                return flushIfPending();
            }

            if (line.isEmpty()) {
                SseEvent event = flushIfPending();
                if (event != null) {
                    return event;
                }
                continue;
            }

            if (line.startsWith(":")) {
                // Comment / keep-alive; ignore.
                continue;
            }

            int separator = line.indexOf(':');
            String field;
            String value;
            if (separator < 0) {
                field = line;
                value = "";
            } else {
                field = line.substring(0, separator);
                value = line.substring(separator + 1);
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }
            }

            switch (field) {
                case "data":
                    // Provider/proxy streams are untrusted. Bound accumulated payloads so a
                    // malformed or hostile stream cannot grow the app heap without limit.
                    int addedChars = value.length() + (dataBuffer.length() > 0 ? 1 : 0);
                    if (dataBuffer.length() + addedChars > MAX_EVENT_CHARS) {
                        throw new IOException("AI stream event exceeded the 1 MiB safety limit.");
                    }
                    if (dataBuffer.length() > 0) {
                        dataBuffer.append('\n');
                    }
                    dataBuffer.append(value);
                    break;
                case "event":
                    if (value.length() > MAX_EVENT_NAME_CHARS) {
                        throw new IOException("AI stream event name exceeded the safety limit.");
                    }
                    eventName = value;
                    break;
                default:
                    // "id:", "retry:" and unknown fields are ignored.
                    break;
            }
        }
    }

    private SseEvent flushIfPending() {
        if (dataBuffer.length() == 0 && eventName == null) {
            return null;
        }
        String data = dataBuffer.toString();
        String name = eventName;
        dataBuffer.setLength(0);
        eventName = null;
        return new SseEvent(name, data);
    }
}
