package pro.sketchware.ai.agent;

import org.json.JSONObject;
import com.besome.sketch.design.DesignActivity;

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
        String type = args.optString("manager_type");
        ctx.runOnUiThread(() -> {
            DesignActivity a = ctx.activity.get();
            if (a == null) return;
            switch (type) {
                case "image" -> a.toImageManager();
                case "java" -> a.toJavaManager();
                case "resource" -> a.toResourceManager();
                case "library" -> a.toLibraryManager();
                case "view" -> a.toViewManager();
                case "asset" -> a.toAssetManager();
                case "font" -> a.toFontManager();
            }
        });
        return new ToolResult("Opened " + type + " manager", false);
    }
}
