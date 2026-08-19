package pro.sketchware.ai.agent;

import org.json.JSONObject;
import com.besome.sketch.design.DesignActivity;

public class BuildProjectTool implements Tool {

    @Override
    public ToolSpec spec() {
        return new ToolSpec("build_project", "Builds and runs the current Sketchware project", new JSONObject());
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) {
        ctx.runOnUiThread(() -> {
            DesignActivity a = ctx.activity.get();
            if (a != null) {
                a.findViewById(pro.sketchware.R.id.btn_run).performClick();
            }
        });
        return new ToolResult("Build started in the UI", false);
    }
}
