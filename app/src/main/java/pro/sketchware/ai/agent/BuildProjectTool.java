package pro.sketchware.ai.agent;

import android.view.View;

import org.json.JSONObject;

import com.besome.sketch.design.DesignActivity;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
                "Requests the current Sketchware project's build/run action. This tool confirms only that the UI action was dispatched, not that compilation or installation succeeded.",
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
            return new ToolResult("Build was not started: the active project editor is no longer available.", true);
        }

        final CountDownLatch dispatched = new CountDownLatch(1);
        final AtomicReference<String> failure = new AtomicReference<>();
        activity.runOnUiThread(() -> {
            try {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    failure.set("The active project editor closed before the build action could run.");
                    return;
                }
                View runButton = activity.findViewById(pro.sketchware.R.id.btn_run);
                if (runButton == null) {
                    failure.set("Build was not started: the Run button is not present in the active editor.");
                    return;
                }
                if (!runButton.isEnabled()) {
                    failure.set("Build was not started: the Run button is currently disabled.");
                    return;
                }
                runButton.performClick();
            } catch (RuntimeException e) {
                failure.set("Build action dispatch failed: " + e.getClass().getSimpleName());
            } finally {
                dispatched.countDown();
            }
        });

        try {
            if (!dispatched.await(5, TimeUnit.SECONDS)) {
                return new ToolResult("Build action was not verified as dispatched: UI thread timed out. Build status is unknown.", true);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ToolResult("Build action was cancelled before dispatch completed.", true);
        }

        if (failure.get() != null) {
            return new ToolResult(failure.get(), true);
        }
        return new ToolResult("Build/run action dispatched to the active project's UI. Compilation, installation, and runtime success are NOT verified by this tool.", false);
    }
}
