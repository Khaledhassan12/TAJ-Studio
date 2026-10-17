package pro.sketchware.ai.agent;

import org.json.JSONObject;
import org.json.JSONArray;

import com.besome.sketch.design.DesignActivity;

public class OpenManagerTool implements Tool {

    @Override
    public ToolSpec spec() {
        JSONObject params = new JSONObject();
        try {
            JSONObject type = new JSONObject();
            type.put("type", "string");
            type.put("enum", new JSONArray()
                    .put("image").put("java").put("resource").put("library")
                    .put("view").put("asset").put("font"));
            params.put("type", "object");
            params.put("properties", new JSONObject().put("manager_type", type));
            params.put("required", new JSONArray().put("manager_type"));
        } catch (org.json.JSONException e) {
            throw new IllegalStateException("Could not construct open_manager schema.", e);
        }
        return new ToolSpec("open_manager",
                "Requests navigation to a supported Sketchware manager. The tool reports dispatch status, not confirmed screen visibility.",
                params);
    }

    @Override
    public Domain domain() {
        return Domain.PROJECT;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) {
        String type = args.optString("manager_type", "");
        if (!("image".equals(type) || "java".equals(type) || "resource".equals(type)
                || "library".equals(type) || "view".equals(type)
                || "asset".equals(type) || "font".equals(type))) {
            return ToolResult.structured("open_manager", "INVALID_ARGUMENTS", null,
                    "Unsupported manager type; no navigation was requested.", null, true);
        }

        DesignActivity activity = ctx.activity.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return ToolResult.structured("open_manager", "NO_ACTIVE_EDITOR", null,
                    "The editor that owns this task is no longer active; navigation was not requested.", null, true);
        }

        ctx.runOnUiThread(() -> {
            DesignActivity current = ctx.activity.get();
            if (current == null || current.isFinishing() || current.isDestroyed()
                    || !ctx.sc_id.equals(DesignActivity.sc_id)) return;
            switch (type) {
                case "image" -> current.toImageManager();
                case "java" -> current.toJavaManager();
                case "resource" -> current.toResourceManager();
                case "library" -> current.toLibraryManager();
                case "view" -> current.toViewManager();
                case "asset" -> current.toAssetManager();
                case "font" -> current.toFontManager();
            }
        });
        return ToolResult.structured("open_manager", "REQUESTED", null,
                "Navigation was dispatched to the active editor. The tool cannot independently confirm that the destination screen became visible.", null, false);
    }
}
