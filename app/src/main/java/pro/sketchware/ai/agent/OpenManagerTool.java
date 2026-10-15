package pro.sketchware.ai.agent;

import org.json.JSONObject;
import com.besome.sketch.design.DesignActivity;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class OpenManagerTool implements Tool {

    @Override
    public ToolSpec spec() {
        JSONObject params = new JSONObject();
        try {
            JSONObject type = new JSONObject();
            type.put("type", "string");
            type.put("enum", new org.json.JSONArray()
                .put("image").put("java").put("resource").put("library").put("view").put("asset").put("font"));
            JSONObject props = new JSONObject();
            props.put("manager_type", type);
            params.put("type", "object");
            params.put("properties", props);
            params.put("required", new org.json.JSONArray().put("manager_type"));
        } catch (Exception ignored) {}
        return new ToolSpec("open_manager", "Opens a specific Sketchware manager", params);
    }

    @Override
    public Domain domain() {
        return Domain.PROJECT;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) {
        String type = args.optString("manager_type", "");
        if (!java.util.Arrays.asList("image", "java", "resource", "library", "view", "asset", "font").contains(type)) {
            return new ToolResult("Unsupported manager type: " + type, true);
        }
        DesignActivity activity = ctx.activity.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return new ToolResult("Cannot open manager: the active project editor is unavailable.", true);
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ToolResult> result = new AtomicReference<>();
        activity.runOnUiThread(() -> {
            try {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    result.set(new ToolResult("Cannot open manager: the project editor closed before dispatch.", true));
                    return;
                }
                switch (type) {
                    case "image" -> activity.toImageManager();
                    case "java" -> activity.toJavaManager();
                    case "resource" -> activity.toResourceManager();
                    case "library" -> activity.toLibraryManager();
                    case "view" -> activity.toViewManager();
                    case "asset" -> activity.toAssetManager();
                    case "font" -> activity.toFontManager();
                }
                result.set(new ToolResult("Manager navigation dispatched: " + type, false));
            } catch (RuntimeException e) {
                result.set(new ToolResult("Manager navigation failed: " + e.getMessage(), true));
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) return new ToolResult("Manager navigation dispatch timed out.", true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ToolResult("Manager navigation was interrupted.", true);
        }
        ToolResult dispatched = result.get();
        return dispatched == null ? new ToolResult("Manager navigation returned no status.", true) : dispatched;
    }
}
