package pro.sketchware.ai.net;

import java.io.IOException;

import okhttp3.Response;

/**
 * Unified error taxonomy for every AI failure. Each instance carries a stable
 * {@link Type} plus the raw provider error body, so UI layers can show a
 * friendly line AND the raw body in dialogs without parsing exception text.
 */
public class AIException extends Exception {

    public enum Type {
        NETWORK,          // no connection, timeout, DNS, TLS
        AUTH,             // 401/403 invalid or missing key
        RATE_LIMIT,       // 402/429 quota or rate limit
        MODEL_NOT_FOUND,  // 404 or model-specific errors
        INVALID_JSON,     // malformed response body / SSE frame
        CANCELLED,        // user pressed stop
        HTTP,             // any other non-2xx
        UNKNOWN
    }

    public final Type type;
    public final int httpStatus;
    /** Raw provider error body, capped, for display below the friendly line. */
    public final String rawBody;
    /** The original OkHttp response if this was an HTTP error. */
    public transient okhttp3.Response lastResponse;

    public AIException(Type type, String message) {
        this(type, message, -1, null, "");
    }

    public AIException(Type type, String message, Throwable cause) {
        this(type, message, -1, cause, "");
    }

    public AIException(Type type, String message, int httpStatus, Throwable cause) {
        this(type, message, httpStatus, cause, "");
    }

    public AIException(Type type, String message, int httpStatus, Throwable cause, String rawBody) {
        super(message, cause);
        this.type = type;
        this.httpStatus = httpStatus;
        this.rawBody = rawBody == null ? "" : rawBody;
    }

    public AIException(Type type, String message, int httpStatus, Throwable cause, String rawBody, okhttp3.Response lastResponse) {
        this(type, message, httpStatus, cause, rawBody);
        this.lastResponse = lastResponse;
    }

    public static AIException cancelled() {
        return new AIException(Type.CANCELLED, "Request cancelled.");
    }

    /** Maps an OkHttp/IO failure to the network category. */
    public static AIException fromIo(IOException e) {
        String name = e.getClass().getSimpleName();
        if (e instanceof java.net.SocketTimeoutException) {
            return new AIException(Type.NETWORK,
                    "Network unreachable. The provider took too long to respond.", e);
        }
        if (e instanceof java.net.UnknownHostException) {
            return new AIException(Type.NETWORK,
                    "Network unreachable. Could not reach the provider. Check the base URL and your internet connection.", e);
        }
        return new AIException(Type.NETWORK,
                "Network unreachable (" + name + "). Check your connection and try again.", e);
    }

    /** Maps an HTTP status + provider error body to a friendly taxonomy message. */
    public static AIException fromHttp(Response response, String errorBody) {
        int code = response.code();
        String detail = trimRaw(errorBody);
        if (code == 401 || code == 403) {
            return new AIException(Type.AUTH, "Authentication failed. Double-check your API key.", code, null, detail, response);
        }
        if (code == 402 || code == 429) {
            return new AIException(Type.RATE_LIMIT, "Quota/rate limit reached.", code, null, detail, response);
        }
        if (code == 404) {
            return new AIException(Type.MODEL_NOT_FOUND, "Model or endpoint not found. Check model ID.", code, null, detail, response);
        }
        if (code >= 500 && code <= 599) {
            return new AIException(Type.HTTP, "Provider server error. Try again.", code, null, detail, response);
        }
        return new AIException(Type.HTTP, "Provider returned an error (HTTP " + code + ").", code, null, detail, response);
    }

    private static String trimRaw(String body) {
        if (body == null) {
            return "";
        }
        String t = body.trim();
        return t.length() > 2000 ? t.substring(0, 2000) + "..." : t;
    }

    /** True when this failure deserves one automatic retry: transport errors or provider 5xx. */
    public boolean isRetriable() {
        return type == Type.NETWORK || (httpStatus >= 500 && httpStatus <= 599) || isThinkingError();
    }

    public boolean isThinkingError() {
        return httpStatus == 400 && rawBody != null && rawBody.toLowerCase().contains("thinking");
    }

    /** Returns a short, user-friendly message for display in error cards. */
    public String friendlyMessage() {
        return getMessage() == null ? "Something went wrong." : getMessage();
    }
}
