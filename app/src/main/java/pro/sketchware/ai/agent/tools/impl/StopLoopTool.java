package pro.sketchware.ai.agent.tools.impl;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

import pro.sketchware.ai.agent.tools.Tool;
import pro.sketchware.ai.agent.tools.ToolArgs;
import pro.sketchware.ai.agent.tools.ToolCtx;
import pro.sketchware.ai.agent.tools.ToolResult;
import pro.sketchware.ai.agent.tools.ToolSpec;
import pro.sketchware.ai.automation.AutomationSettings;
import pro.sketchware.ai.automation.LoopEntity;
import pro.sketchware.ai.automation.LoopRunner;
import pro.sketchware.ai.automation.LoopStore;

/**
 * [WHAT] stop_loop: stops a running loop by id or name (P2-AU, D17).
 * Honest error lists existing loops when nothing matches.
 */
public class StopLoopTool implements Tool {

    @Override
    public ToolSpec spec() {
        return new ToolSpec("stop_loop",
                "Stop a running conversation loop by its id or name",
                "{\"name_or_id\": \"string\"}");
    }

    @Override
    public ToolResult execute(ToolArgs args, ToolCtx ctx) {
        Context appCtx = ctx.context.getApplicationContext();
        if (!AutomationSettings.get(appCtx).isTasksLoopsEnabled()) {
            return ToolResult.error("Tasks & Loops access is disabled in Automation settings");
        }

        String key = args.getString("name_or_id");
        if (key == null || key.trim().isEmpty()) return ToolResult.error("name_or_id is required");
        key = key.trim();

        LoopStore store = LoopStore.get(appCtx);
        LoopEntity loop = store.findById(key);
        if (loop == null) loop = store.findByName(key);
        if (loop == null) {
            StringBuilder known = new StringBuilder();
            List<LoopEntity> all = store.list();
            for (LoopEntity l : all) {
                if (known.length() > 0) known.append(", ");
                known.append(l.name).append(l.running ? " (running)" : " (stopped)");
            }
            return ToolResult.error("no loop found with id or name '" + key + "'"
                    + (all.isEmpty() ? " — no loops exist" : " — known loops: " + known));
        }

        if (!loop.running) {
            return ToolResult.error("loop '" + loop.name + "' is already stopped");
        }

        LoopRunner.stop(appCtx, loop);

        try {
            JSONObject out = new JSONObject();
            out.put("ok", true);
            out.put("stopped", loop.name);
            out.put("id", loop.id);
            JSONArray remaining = new JSONArray();
            for (LoopEntity l : store.list()) {
                if (l.running) remaining.put(l.name);
            }
            out.put("still_running", remaining);
            return ToolResult.success(out.toString());
        } catch (Exception e) {
            return ToolResult.error("loop was stopped but the result could not be serialized: " + e.getMessage());
        }
    }
}
