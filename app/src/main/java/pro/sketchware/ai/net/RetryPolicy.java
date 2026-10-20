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

    /**
     * Bounded exponential backoff for interactive AI calls.
     * attempt is 1-based: roughly 0.8–1.2s, 1.6–2.4s, then capped.
     * Provider Retry-After is honored but clamped so a malformed server header
     * cannot stall the assistant for minutes or overflow a millisecond conversion.
     */
    public static long waitMs(int attempt, Response response) {
        if (response != null) {
            String retryAfter = response.header("Retry-After");
            if (retryAfter != null && !retryAfter.trim().isEmpty()) {
                try {
                    long seconds = Long.parseLong(retryAfter.trim());
                    if (seconds >= 0L) {
                        return Math.min(30_000L, seconds > 30L ? 30_000L : seconds * 1000L);
                    }
                } catch (NumberFormatException ignored) {
                    // HTTP-date Retry-After and malformed values fall back to backoff.
                }
            }
        }

        int safeAttempt = Math.max(1, Math.min(attempt, 6));
        long base = Math.min(15_000L, 1_000L << (safeAttempt - 1));
        double offset = (JITTER.nextDouble() * 0.4d) - 0.2d;
        return Math.max(250L, Math.min(15_000L, (long) (base * (1.0d + offset))));
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
            // Release the error body/connection before waiting for the next attempt.
            response.close();
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException("Interrupted while waiting to retry the request.", e);
            }
        }
        return null; // unreachable
    }

    private RetryPolicy() {}
}
