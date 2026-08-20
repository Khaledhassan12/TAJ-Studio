package pro.sketchware.ai.net;

import okhttp3.Response;
import java.util.Random;

public final class RetryPolicy {

    public static final int MAX_ATTEMPTS = 3;
    private static final Random JITTER = new Random();

    public static boolean isRetryable(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    public static boolean isRetryable(Throwable e) {
        return e instanceof java.io.IOException;
    }

    public static long waitMs(int attempt, Response response) {
        if (response != null) {
            String retryAfter = response.header("Retry-After");
            if (retryAfter != null && !retryAfter.isEmpty()) {
                try {
                    // Try to parse as seconds
                    return Long.parseLong(retryAfter) * 1000L;
                } catch (NumberFormatException ignored) {}
            }
        }

        // Exponential backoff: 5s, 10s, 20s... capped at 60s
        long base = (long) Math.min(60000, 5000 * Math.pow(2, attempt));
        // +/- 20% jitter
        double range = base * 0.2;
        double offset = (JITTER.nextDouble() * 2 - 1) * range;
        return (long) (base + offset);
    }

    public interface SyncCall {
        Response execute() throws java.io.IOException;
    }

    /**
     * Executes a synchronous call with automatic retries for rate limits and server errors.
     * Silent loop (no UI callback) intended for background services like ModelSync.
     */
    public static Response executeWithRetry(SyncCall call, boolean autoRetry) throws java.io.IOException {
        int max = autoRetry ? MAX_ATTEMPTS : 1;
        for (int i = 1; i <= max; i++) {
            Response response = call.execute();
            if (response.isSuccessful() || !isRetryable(response.code()) || i >= max) {
                return response;
            }
            long wait = waitMs(i, response);
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return response;
            }
        }
        return null; // unreachable
    }

    private RetryPolicy() {}
}
