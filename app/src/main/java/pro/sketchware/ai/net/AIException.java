package pro.sketchware.ai.net;

import java.io.IOException;

import okhttp3.Response;

/**
 * Unified error taxonomy for every AI failure. Each instance carries a stable
 * {@link Type} so UI layers can map failures to friendly, actionable messages
 * without parsing raw exception text.
 */
public class AIException extends Exception {

    public enum Type {
        NETWORK,          // no connection, timeout, DNS, TLS
        AUTH,             // 401/403 invalid or missing key
        RATE_LIMIT,       // 429
        MODEL_NOT_FOUND,  // 404 or model-specific errors
        INVALID_JSON,     // malformed response body / SSE frame
        CANCELLED,        // user pressed stop
        HTTP,             // any other non-2xx
        UNKNOWN
    }

    public final Type type;
    public final int httpStatus;

    public AIException(Type type, String message) {
        this(type, message, -1, null);
    }

    public AIException(Type type, String message, Throwable cause) {
        this(type, message, -1, cause);
    }

    public AIException(Type type, String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.type = type;
        this.httpStatus = httpStatus;
    }

    public static AIException cancelled() {
        return new AIException(Type.CANCELLED, "Request cancelled.");
    }

    /** Maps an OkHttp/IO failure to the closest network category. */
    public static AIException fromIo(IOException e) {
        String name = e.getClass().getSimpleName();
        if (e instanceof java.net.SocketTimeoutException) {
            return new AIException(Type.NETWORK, "The provider took too long to respond. Check your connection and try again.", e);
        }
        if (e instanceof java.net.UnknownHostException) {
            return new AIException(Type.NETWORK, "Could not reach the provider. Check the base URL and your internet connection.", e);
        }
        return new AIException(Type.NETWORK, "Network error (" + name + "). Check your connection and try again.", e);
    }

    /** Maps an HTTP status + provider error body to a friendly message. */
    public static AIException fromHttp(Response response, String errorBody) {
        int code = response.code();
        String detail = trim(errorBody);
        if (code == 401 || code == 403) {
            return new AIException(Type.AUTH,
                    "Authentication failed (" + code + "). Double-check your API key." + detailSuffix(detail),
                    code, null);
        }
        if (code == 429) {
            return new AIException(Type.RATE_LIMIT,
                    "Rate limit reached (" + code + "). Wait a moment and try again." + detailSuffix(detail),
                    code, null);
        }
        if (code == 404) {
            return new AIException(Type.MODEL_NOT_FOUND,
                    "Endpoint or model not found (" + code + "). Check the model name and base URL." + detailSuffix(detail),
                    code, null);
        }
        return new AIException(Type.HTTP,
                "Provider returned an error (HTTP " + code + ")." + detailSuffix(detail),
                code, null);
    }

    private static String detailSuffix(String detail) {
        return detail.isEmpty() ? "" : "\n\n" + detail;
    }

    private static String trim(String body) {
        if (body == null) {
            return "";
        }
        String t = body.trim();
        return t.length() > 300 ? t.substring(0, 300) + "…" : t;
    }

    /** Returns a short, user-friendly message for display in error cards. */
    public String friendlyMessage() {
        return getMessage() == null ? "Something went wrong." : getMessage();
    }
}
