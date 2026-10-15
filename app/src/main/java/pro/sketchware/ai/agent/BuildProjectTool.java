package pro.sketchware.ai.agent;

import android.view.View;
import org.json.JSONObject;
import com.besome.sketch.design.DesignActivity;
import pro.sketchware.build.BuildLogHub;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class BuildProjectTool implements Tool {

    @Override
    public ToolSpec spec() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            schema.put("properties", new JSONObject());
        } catch (org.json.JSONException e) {
            throw new IllegalStateException("Unable to construct build tool schema.", e);
        }
        return new ToolSpec("build_project",
                "Builds the active Sketchware project and waits for the actual compile/sign terminal result. Reports APK build success separately from installation or runtime success.",
                schema);
    }

    @Override
    public Domain domain() {
        return Domain.PROJECT;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) {
        final DesignActivity activity = ctx.activity.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return ToolResult.structured("build_project", "EDITOR_UNAVAILABLE", null,
                    "Build was not started because the active project editor is no longer available.", null, true);
        }

        final BuildLogHub hub = BuildLogHub.getInstance();
        final long previousGeneration = hub.getSessionGeneration();
        final CountDownLatch dispatched = new CountDownLatch(1);
        final CountDownLatch terminal = new CountDownLatch(1);
        final AtomicReference<String> dispatchFailure = new AtomicReference<>();
        final AtomicReference<BuildLogHub.Phase> terminalPhase = new AtomicReference<>();
        final AtomicReference<String> terminalMessage = new AtomicReference<>("");
        final AtomicBoolean armed = new AtomicBoolean(false);
        final AtomicBoolean started = new AtomicBoolean(false);

        BuildLogHub.BuildListener listener = (phase, message, history) -> {
            if (!armed.get() || hub.getSessionGeneration() <= previousGeneration) return;
            if (phase == BuildLogHub.Phase.RUNNING
                    && "Build started".equals(message)
                    && (history == null || history.isEmpty())) {
                started.set(true);
                return;
            }
            if (!started.get()) return;
            if (phase == BuildLogHub.Phase.SUCCESS || phase == BuildLogHub.Phase.FAILURE
                    || phase == BuildLogHub.Phase.CANCELED) {
                terminalPhase.set(phase);
                terminalMessage.set(message == null ? "" : message);
                terminal.countDown();
            }
        };
        hub.addListener(listener);

        try {
            activity.runOnUiThread(() -> {
                try {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        dispatchFailure.set("The active project editor closed before the build action could run.");
                        return;
                    }
                    View runButton = activity.findViewById(pro.sketchware.R.id.btn_run);
                    if (runButton == null) {
                        dispatchFailure.set("Build was not started: the Run button is not present in the active editor.");
                        return;
                    }
                    if (!runButton.isEnabled()) {
                        dispatchFailure.set("Build was not started: the Run button is currently disabled.");
                        return;
                    }
                    armed.set(true);
                    runButton.performClick();
                } catch (RuntimeException e) {
                    dispatchFailure.set("Build action dispatch failed: " + e.getClass().getSimpleName());
                } finally {
                    dispatched.countDown();
                }
            });

            if (!dispatched.await(5, TimeUnit.SECONDS)) {
                return ToolResult.structured("build_project", "DISPATCH_TIMEOUT", null,
                        "The UI did not confirm build dispatch within five seconds. Build status is unknown.", null, true);
            }
            if (dispatchFailure.get() != null) {
                return ToolResult.structured("build_project", "NOT_STARTED", null, dispatchFailure.get(), null, true);
            }
            if (hub.getSessionGeneration() <= previousGeneration) {
                return ToolResult.structured("build_project", "NOT_STARTED", null,
                        "The Run action returned without opening a new build session. No build success is claimed.", null, true);
            }

            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
            while (terminal.getCount() > 0) {
                if (ctx.isCancellationRequested()) {
                    return ToolResult.structured("build_project", "WAIT_CANCELLED", null,
                            "Waiting for the build result was cancelled. The build may still be running; inspect the build monitor before retrying.", null, true);
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return ToolResult.structured("build_project", "RESULT_TIMEOUT", null,
                            "The build did not publish a terminal result within five minutes. It may still be running; no success is claimed.", null, true);
                }
                terminal.await(Math.min(250L, TimeUnit.NANOSECONDS.toMillis(remaining)), TimeUnit.MILLISECONDS);
            }

            BuildLogHub.Phase phase = terminalPhase.get();
            String message = terminalMessage.get();
            String logSummary = summarizeRecentLogs(hub.getHistory());
            if (phase == BuildLogHub.Phase.SUCCESS) {
                return ToolResult.structured("build_project", "BUILD_SUCCESS", null,
                        "APK path reported by build: " + sanitize(message, 1200)
                                + ". Compilation and APK signing completed. Installation and app launch are not verified by this result. "
                                + logSummary, null, false);
            }
            if (phase == BuildLogHub.Phase.CANCELED) {
                return ToolResult.structured("build_project", "BUILD_CANCELLED", null,
                        "The build system reported cancellation. " + logSummary, null, true);
            }
            return ToolResult.structured("build_project", "BUILD_FAILED", null,
                    "The build system reported failure: " + sanitize(message, 1800) + " " + logSummary, null, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.structured("build_project", "WAIT_INTERRUPTED", null,
                    "Waiting for the build result was interrupted. The build may still be running; no success is claimed.", null, true);
        } finally {
            hub.removeListener(listener);
        }
    }

    private static String summarizeRecentLogs(List<BuildLogHub.LogEntry> entries) {
        if (entries == null || entries.isEmpty()) return "No build log entries were available.";
        StringBuilder out = new StringBuilder("Recent build log:");
        int start = Math.max(0, entries.size() - 8);
        for (int i = start; i < entries.size(); i++) {
            BuildLogHub.LogEntry entry = entries.get(i);
            if (entry == null || entry.message == null || entry.message.trim().isEmpty()) continue;
            out.append("\n- ").append(sanitize(entry.message, 320));
        }
        if (out.length() > 2600) return out.substring(0, 2600);
        return out.toString();
    }

    private static String sanitize(String value, int limit) {
        if (value == null) return "";
        String safe = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return safe.length() > limit ? safe.substring(0, limit) + "…" : safe;
    }
}
