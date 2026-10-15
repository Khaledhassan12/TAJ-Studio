package pro.sketchware.ai.agent;

import org.json.JSONObject;
import com.besome.sketch.design.DesignActivity;
import android.view.View;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BuildProjectTool implements Tool {

    @Override
    public ToolSpec spec() {
        return new ToolSpec("build_project", "Builds and runs the current Sketchware project", new JSONObject());
    }

    @Override
    public Domain domain() {
        return Domain.PROJECT;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) {
        DesignActivity activity = ctx.activity.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return new ToolResult("Cannot build: the active project editor is no longer available.", true);
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ToolResult> result = new AtomicReference<>();
        activity.runOnUiThread(() -> {
            try {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    result.set(new ToolResult("Cannot build: the project editor closed before dispatch.", true));
                    return;
                }
                View runButton = activity.findViewById(pro.sketchware.R.id.btn_run);
                if (runButton == null || !runButton.hasOnClickListeners()) {
                    result.set(new ToolResult("Cannot build: the editor's Run action is unavailable.", true));
                    return;
                }
                runButton.performClick();
                result.set(new ToolResult("Build/run action dispatched to the currently open project. The actual build result must be checked in the editor output; it has not been verified by this tool.", false));
            } catch (RuntimeException e) {
                result.set(new ToolResult("Build/run action failed: " + e.getMessage(), true));
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) return new ToolResult("Build/run action dispatch timed out.", true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ToolResult("Build/run action dispatch was interrupted.", true);
        }
        ToolResult dispatched = result.get();
        return dispatched == null ? new ToolResult("Build/run action returned no status.", true) : dispatched;
    }
}
