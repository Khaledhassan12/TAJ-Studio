package pro.sketchware.ai.hf;

/**
 * [WHAT] Thread-safe flag to signal cancellation.
 */
public class CancelFlag {
    private volatile boolean cancelled = false;

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
