package pro.sketchware.ai.net;

import android.util.Log;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import pro.sketchware.ai.config.AIConfigStore;
import pro.sketchware.ai.core.AIProvider;
import pro.sketchware.ai.core.AIRequest;
import pro.sketchware.ai.core.Protocol;
import pro.sketchware.ai.core.ProviderProfile;
import pro.sketchware.ai.core.StreamCallbacks;

/**
 * Base class for all three protocol engines. Owns the shared OkHttpClient,
 * provides URL building, per-contract auth header assembly, exactly-one-terminal-
 * callback guarantees, a single automatic retry (5xx / transport only, before
 * any token has been emitted) and cooperative cancellation.
 *
 * Keys and base URLs are sanitized on construction so a stale pasted key with
 * trailing whitespace/newlines can never reach the wire or the logs.
 */
public abstract class HttpAI implements AIProvider {

    private static final String TAG = "AIEngine";

    protected static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "tag-assistant-net");
        thread.setDaemon(true);
        return thread;
    });

    /** Connect 10s, read 60s (SSE streams stay open), write 60s. */
    private static final OkHttpClient SHARED_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    protected final ProviderProfile profile;
    protected final String baseUrl;
    protected final String apiKey;

    protected HttpAI(ProviderProfile profile, String baseUrl, String apiKey) {
        this.profile = profile;
        String effective = baseUrl == null || baseUrl.isEmpty() ? profile.defaultBaseUrl : baseUrl;
        this.baseUrl = AIConfigStore.sanitizeBaseUrl(effective);
        this.apiKey = AIConfigStore.sanitizeKey(apiKey);
    }

    @Override
    public String id() {
        return profile.id;
    }

    @Override
    public String displayName() {
        return profile.displayName;
    }

    @Override
    public Protocol protocol() {
        return profile.protocol;
    }

    @Override
    public final Handle stream(AIRequest request, StreamCallbacks callbacks) {
        CallHandle handle = new CallHandle();
        Tracked tracked = new Tracked(callbacks);
        EXECUTOR.execute(() -> runWithRetry(request, tracked, handle));
        return handle;
    }

    private void runWithRetry(AIRequest request, Tracked tracked, CallHandle handle) {
        for (int attempt = 0; attempt <= 1; attempt++) {
            if (handle.isCancelled()) {
                tracked.fail(AIException.cancelled());
                return;
            }
            try {
                executeAndStream(request, tracked, handle);
                return;
            } catch (AIException e) {
                if (handle.isCancelled()) {
                    tracked.fail(AIException.cancelled());
                    return;
                }
                boolean retriable = attempt == 0
                        && !tracked.hasEmitted()
                        && e.isRetriable();
                if (!retriable) {
                    tracked.fail(e);
                    return;
                }
            } catch (Exception e) {
                tracked.fail(new AIException(AIException.Type.UNKNOWN,
                        "Unexpected error while contacting the provider.", e));
                return;
            }
        }
    }
    private void executeAndStream(AIRequest request, Tracked tracked, CallHandle handle) throws AIException {
        Request httpRequest = buildRequest(request);
        Call call = client().newCall(httpRequest);
        handle.attach(call);

        StreamState state = newState();
        try (Response response = call.execute()) {
            if (handle.isCancelled()) {
                throw AIException.cancelled();
            }
            ResponseBody body = response.body();
            if (!response.isSuccessful()) {
                String errorBody = "";
                if (body != null) {
                    try {
                        errorBody = body.string();
                    } catch (IOException ignored) {
                        // Error bodies are best-effort diagnostics.
                    }
                }
                logHttpError(response.code(), errorBody);
                throw AIException.fromHttp(response, errorBody);
            }
            if (body == null) {
                throw new AIException(AIException.Type.HTTP, "The provider returned an empty response.");
            }

            String contentType = response.header("Content-Type", "");
            if (contentType != null && contentType.contains("text/event-stream")) {
                BufferedSource source = body.source();
                SseReader reader = new SseReader(source);
                SseReader.SseEvent event;
                while ((event = reader.nextEvent()) != null) {
                    if (handle.isCancelled()) {
                        throw AIException.cancelled();
                    }
                    if (event.isDone()) {
                        break;
                    }
                    if (event.hasData()) {
                        handleEvent(state, event.name, event.data, tracked);
                    }
                }
            } else {
                // Some compatible servers ignore stream=true and answer with plain JSON.
                parseNonStreaming(state, body.string(), tracked);
            }
            finishStream(state, tracked);
        } catch (IOException e) {
            if (handle.isCancelled()) {
                throw AIException.cancelled();
            }
            throw AIException.fromIo(e);
        }
    }

    /** Logs non-2xx failures: status + first 500 chars of body, key masked as ***. */
    private void logHttpError(int code, String errorBody) {
        try {
            String masked = errorBody == null ? "" : errorBody;
            if (!apiKey.isEmpty() && !masked.isEmpty()) {
                masked = masked.replace(apiKey, "***");
            }
            if (masked.length() > 500) {
                masked = masked.substring(0, 500);
            }
            Log.w(TAG, "HTTP " + code + " " + masked);
        } catch (Exception ignored) {
            Log.w(TAG, "HTTP " + code + " (unreadable error body)");
        }
    }

    protected OkHttpClient client() {
        return SHARED_CLIENT;
    }

    /** Joins the (already sanitized, version-inclusive) base URL + endpoint path. */
    protected String buildUrl(String path) {
        String base = stripTrailingSlash(baseUrl);
        String url = base + (path.startsWith("/") ? path : "/" + path);
        if (profile.urlQuerySuffix != null && !profile.urlQuerySuffix.isEmpty()) {
            url += (url.contains("?") ? "&" : "?") + profile.urlQuerySuffix;
        }
        return url;
    }

    /** Applies the profile's auth scheme and any extra static headers. */
    protected Request.Builder applyHeaders(Request.Builder builder) {
        if (!profile.skipAuth) {
            if (!apiKey.isEmpty()) {
                if (profile.apiKeyHeaderAuth) {
                    builder.header("api-key", apiKey);
                } else {
                    builder.header("Authorization", "Bearer " + apiKey);
                }
            }
        }
        for (Map.Entry<String, String> header : profile.extraHeaders.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        return builder;
    }

    protected static String stripTrailingSlash(String url) {
        String result = url == null ? "" : url.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    protected static RequestBody jsonBody(String json) {
        return RequestBody.create(json, JSON_TYPE);
    }

    // ------------------------------------------------------------------
    // Engine contract
    // ------------------------------------------------------------------

    /** Builds the full HTTP request for the engine's wire format. */
    protected abstract Request buildRequest(AIRequest request) throws AIException;

    /** Fresh per-call accumulation state (text, tool-call fragments, usage). */
    protected abstract StreamState newState();

    /** Handles one SSE frame\'s data payload. May throw to abort with a friendly error. */
    protected abstract void handleEvent(StreamState state, String eventName, String data, Tracked tracked)
            throws AIException;

    /** Parses a complete (non-SSE) JSON response body. */
    protected abstract void parseNonStreaming(StreamState state, String rawBody, Tracked tracked)
            throws AIException;

    /** Emits onComplete with everything accumulated during the stream. */
    protected abstract void finishStream(StreamState state, Tracked tracked);

    /** Marker interface for engine-specific accumulation state. */
    protected interface StreamState {
    }

    /**
     * Callback wrapper enforcing "exactly one terminal callback" and tracking
     * whether any token has been emitted (used by the retry policy).
     */
    protected static final class Tracked implements StreamCallbacks {
        private final StreamCallbacks delegate;
        private final AtomicBoolean emitted = new AtomicBoolean(false);
        private final AtomicBoolean terminated = new AtomicBoolean(false);

        Tracked(StreamCallbacks delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onToken(String token) {
            if (terminated.get()) {
                return;
            }
            emitted.set(true);
            delegate.onToken(token);
        }

        @Override
        public void onToolCall(pro.sketchware.ai.core.ToolCall toolCall) {
            if (!terminated.get()) {
                delegate.onToolCall(toolCall);
            }
        }

        @Override
        public void onComplete(pro.sketchware.ai.core.AIResponse response) {
            if (terminated.compareAndSet(false, true)) {
                delegate.onComplete(response);
            }
        }

        @Override
        public void onError(Throwable error) {
            fail(error instanceof AIException
                    ? (AIException) error
                    : new AIException(AIException.Type.UNKNOWN, String.valueOf(error), error));
        }

        public void fail(AIException error) {
            if (terminated.compareAndSet(false, true)) {
                delegate.onError(error);
            }
        }

        public void complete(pro.sketchware.ai.core.AIResponse response) {
            onComplete(response);
        }

        boolean hasEmitted() {
            return emitted.get();
        }
    }

    /** Cooperative cancellation handle bound to the in-flight OkHttp call. */
    protected static final class CallHandle implements Handle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile Call call;

        void attach(Call newCall) {
            call = newCall;
            if (cancelled.get()) {
                newCall.cancel();
            }
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                Call current = call;
                if (current != null) {
                    current.cancel();
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
