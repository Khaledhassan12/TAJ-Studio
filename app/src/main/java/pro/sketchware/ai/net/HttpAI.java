package pro.sketchware.ai.net;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
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
    /** Bounds serialized prompt growth before any provider/network work begins. */
    private static final int MAX_REQUEST_CHARS = 512 * 1024;
    private static final int MAX_REQUEST_MESSAGES = 512;
    /** Hard cap for one provider response, including cumulative SSE frames. */
    private static final long MAX_RESPONSE_BYTES = 4L * 1024L * 1024L;

    protected static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    /**
     * Bounded process-wide network work pool. A cached pool can create an
     * unbounded number of threads when multiple screens/providers are triggered
     * rapidly; a small queue keeps memory and scheduling predictable on phones.
     */
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            2, 4, 30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32),
            runnable -> {
                Thread thread = new Thread(runnable, "taj-ai-net");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());

    /** Connect 10s, read 60s (SSE streams stay open), write 60s. */
    private static final OkHttpClient SHARED_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    protected final ProviderProfile profile;
    protected final String baseUrl;
    protected String apiKey;
    protected final Context context;

    protected HttpAI(Context context, ProviderProfile profile, String baseUrl, String apiKey) {
        this.context = context.getApplicationContext();
        this.profile = profile;
        AIConfigStore store = AIConfigStore.getInstance(this.context);
        // baseUrl passed here is usually profile.defaultBaseUrl from AIProviderRegistry
        // We want to use the slot override if present.
        this.baseUrl = store.safeBaseUrlFor(profile.id, baseUrl != null && !baseUrl.isEmpty() ? baseUrl : profile.defaultBaseUrl);
        this.apiKey = store.safeKeyFor(profile.id);
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
        String budgetError = validateRequestBudget(request);
        if (budgetError != null) {
            tracked.fail(new AIException(AIException.Type.INVALID_JSON, budgetError));
            return handle;
        }
        try {
            EXECUTOR.execute(() -> runWithRetry(request, tracked, handle));
        } catch (RejectedExecutionException rejected) {
            tracked.fail(new AIException(AIException.Type.UNKNOWN,
                    "TAJ AI is busy with other requests. Wait a moment and try again.", rejected));
        }
        return handle;
    }

    /**
     * Reject pathological prompt payloads before JSON serialization/network dispatch.
     * This is a character budget, not a model-token estimator; providers still enforce
     * their own context windows. The limit prevents accidental transcript/tool-output
     * amplification from consuming excessive heap and mobile bandwidth.
     */
    private static String validateRequestBudget(AIRequest request) {
        if (request == null) return "AI request is missing.";
        if (request.messages.size() > MAX_REQUEST_MESSAGES) {
            return "This conversation is too long for one request. Start a new chat or shorten its history.";
        }
        long chars = 0L;
        if (request.systemPrompt != null) chars += request.systemPrompt.length();
        for (pro.sketchware.ai.core.AIMessage message : request.messages) {
            if (message == null) return "AI request contains an invalid message.";
            chars += message.text.length();
            chars += message.reasoning.length();
            if (message.hasToolCalls()) {
                for (pro.sketchware.ai.core.ToolCall call : message.toolCalls) {
                    if (call == null) return "AI request contains an invalid tool call.";
                    chars += call.name == null ? 0 : call.name.length();
                    chars += call.arguments == null ? 0 : call.arguments.toString().length();
                }
            }
            if (chars > MAX_REQUEST_CHARS) {
                return "This request exceeds TAJ AI's 512 KiB prompt safety limit. Shorten the conversation or split the task into smaller steps.";
            }
        }
        for (pro.sketchware.ai.core.ToolSpec tool : request.tools) {
            if (tool == null) return "AI request contains an invalid tool specification.";
            chars += tool.name == null ? 0 : tool.name.length();
            chars += tool.description == null ? 0 : tool.description.length();
            chars += tool.jsonSchema == null ? 0 : tool.jsonSchema.toString().length();
            if (chars > MAX_REQUEST_CHARS) {
                return "The prompt and tool definitions exceed TAJ AI's 512 KiB safety limit. Reduce tool/context payload size.";
            }
        }
        return null;
    }

    private void runWithRetry(AIRequest request, Tracked tracked, CallHandle handle) {
        AIRequest currentRequest = request;
        AIConfigStore store = AIConfigStore.getInstance(context);
        boolean autoRetry = store.isAutoRetry();
        int maxAttempts = RetryPolicy.MAX_ATTEMPTS;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (handle.isCancelled()) {
                tracked.fail(AIException.cancelled());
                return;
            }
            try {
                executeAndStream(currentRequest, tracked, handle);
                return;
            } catch (AIException e) {
                if (handle.isCancelled()) {
                    tracked.fail(AIException.cancelled());
                    return;
                }

                // Retry logic:
                // 1. Thinking error: Always retriable once (immediate, thinking disabled).
                // 2. 429/5xx: Retriable up to 3 times if autoRetry is ON.
                // 3. Network/IOException: Retriable exactly once.
                
                boolean isThinking = e.isThinkingError();
                boolean isRateLimitOrServer = autoRetry && RetryPolicy.isRetryable(e.httpStatus);
                boolean isNetwork = e.type == AIException.Type.NETWORK && attempt == 1;

                boolean retriable = !tracked.hasEmitted() && (isThinking || isRateLimitOrServer || isNetwork);

                if (!retriable || attempt >= maxAttempts) {
                    tracked.fail(e);
                    return;
                }

                if (isThinking) {
                    currentRequest = new AIRequest.Builder()
                            .systemPrompt(request.systemPrompt)
                            .messages(request.messages)
                            .tools(request.tools)
                            .model(request.model)
                            .temperature(request.temperature)
                            .maxTokens(request.maxTokens)
                            .thinkingEnabled(false)
                            .build();
                } else {
                    long waitMs = RetryPolicy.waitMs(attempt, e.lastResponse);
                    handle.beginRetryWait();
                    try {
                        tracked.onRateLimitWait(waitMs, attempt, maxAttempts);
                        long start = System.currentTimeMillis();
                        while (System.currentTimeMillis() - start < waitMs) {
                            if (handle.isCancelled()) throw new InterruptedException();
                            if (handle.shouldSkipWait()) break;
                            long remaining = waitMs - (System.currentTimeMillis() - start);
                            if (remaining <= 0L) break;
                            Thread.sleep(Math.min(500L, remaining));
                        }
                    } catch (InterruptedException ie) {
                        tracked.fail(AIException.cancelled());
                        return;
                    } finally {
                        // The skip request belongs to this specific wait only. A late tap must
                        // never skip a later retry or leak into another request.
                        handle.endRetryWait();
                    }
                }
            } catch (Exception e) {
                tracked.fail(new AIException(AIException.Type.UNKNOWN,
                        "Unexpected error while contacting the provider.", e));
                return;
            }
        }
    }
    private void executeAndStream(AIRequest request, Tracked tracked, CallHandle handle) throws AIException {
        // Defensive: ensure apiKey matches store
        AIConfigStore store = AIConfigStore.getInstance(context);
        String currentKey = store.safeKeyFor(profile.id);
        if (!currentKey.equals(this.apiKey)) {
            this.apiKey = currentKey;
        }

        Request httpRequest = buildRequest(request);

        String headerName = profile.apiKeyHeaderAuth ? "api-key" : (profile.protocol == Protocol.GEMINI ? "x-goog-api-key" : (profile.protocol == Protocol.ANTHROPIC ? "x-api-key" : "Authorization"));
        Log.d("TAG_AI", "HOST=" + httpRequest.url().host() + " MODEL=" + store.safeModelFor(profile.id) + " AUTH_HEADER=" + headerName);

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
                long receivedChars = 0L;
                while ((event = reader.nextEvent()) != null) {
                    receivedChars += event.data.length();
                    if (receivedChars > MAX_RESPONSE_BYTES) {
                        throw new AIException(AIException.Type.UNKNOWN,
                                "The provider response exceeded TAJ AI's 4 MiB safety limit.");
                    }
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
                // Request at most one byte beyond the cap before materializing the body.
                BufferedSource source = body.source();
                source.request(MAX_RESPONSE_BYTES + 1L);
                if (source.buffer().size() > MAX_RESPONSE_BYTES) {
                    throw new AIException(AIException.Type.UNKNOWN,
                            "The provider response exceeded TAJ AI's 4 MiB safety limit.");
                }
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

    /** Logs only the status code; provider error bodies can contain credentials or user data. */
    private void logHttpError(int code, String errorBody) {
        try {
            Log.w(TAG, "HTTP " + code + " (provider error details omitted)");
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
        public void onReasoningToken(String token) {
            if (!terminated.get()) {
                if (token != null && !token.isEmpty()) emitted.set(true);
                delegate.onReasoningToken(token);
            }
        }

        @Override
        public void onRateLimitWait(long waitMs, int attempt, int maxAttempts) {
            if (!terminated.get()) {
                delegate.onRateLimitWait(waitMs, attempt, maxAttempts);
            }
        }

        @Override
        public void onToolCall(pro.sketchware.ai.core.ToolCall toolCall) {
            if (!terminated.get()) {
                if (toolCall != null) emitted.set(true);
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
        private boolean retryWaitActive;
        private boolean skipWaitRequested;
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

        synchronized void beginRetryWait() {
            retryWaitActive = true;
            skipWaitRequested = false;
        }

        synchronized void endRetryWait() {
            retryWaitActive = false;
            skipWaitRequested = false;
        }

        @Override
        public synchronized void skipWait() {
            if (retryWaitActive) {
                skipWaitRequested = true;
            }
        }

        public synchronized boolean shouldSkipWait() {
            if (!retryWaitActive || !skipWaitRequested) return false;
            skipWaitRequested = false;
            return true;
        }
    }
}
