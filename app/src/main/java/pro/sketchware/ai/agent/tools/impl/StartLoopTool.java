package pro.sketchware.ai.agent.tools.impl;

import android.content.Context;

import org.json.JSONObject;

import java.util.UUID;

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
 * [WHAT] start_loop: model starts a repeating prompt in THIS conversation
 * (P2-AU, D17). Restarting an existing loop updates prompt/interval honestly.
 */
public class StartLoopTool implements Tool {

    @Override
    public ToolSpec spec() {
        return new ToolSpec("start_loop",
                "Start a loop that re-sends a prompt to THIS conversation every interval_minutes (minimum 1)",
                "{\"name\": \"string\", \"prompt\": \"string\", \"interval_minutes\": \"number\"}");
    }

    @Override
    public ToolResult execute(ToolArgs args, ToolCtx ctx) {
        Context appCtx = ctx.context.getApplicationContext();
        if (!AutomationSettings.get(appCtx).isTasksLoopsEnabled()) {
            return ToolResult.error("Tasks & Loops access is disabled in Automation settings");
        }
        if (ctx.conversationId == null || ctx.conversationId.isEmpty()) {
            return ToolResult.error("start_loop can only run inside a conversation turn");
        }

        String name = args.getString("name");
        String prompt = args.getString("prompt");
        String intervalRaw = args.getString("interval_minutes");
        if (name == null || name.trim().isEmpty()) return ToolResult.error("name is required");
        if (prompt == null || prompt.trim().isEmpty()) return ToolResult.error("prompt is required");
        if (intervalRaw == null || intervalRaw.trim().isEmpty()) return ToolResult.error("interval_minutes is required");
        name = name.trim();

        int interval;
        try {
            interval = Integer.parseInt(intervalRaw.trim());
        } catch (NumberFormatException e) {
            return ToolResult.error("interval_minutes must be a whole number, got '" + intervalRaw.trim() + "'");
        }
        if (interval < 1) return ToolResult.error("interval_minutes must be >= 1");

        LoopStore store = LoopStore.get(appCtx);
        boolean restarted = false;
        LoopEntity loop = store.findByName(name);
        if (loop == null) {
            loop = new LoopEntity();
            loop.id = UUID.randomUUID().toString();
            loop.name = name;
            loop.scId = ctx.scId;
            loop.conversationId = ctx.conversationId;
            loop.createdAt = System.currentTimeMillis();
        } else {
            restarted = true; // existing loop: update prompt/interval, keep identity
        }
        loop.prompt = prompt;
        loop.intervalMinutes = interval;

        LoopRunner.start(appCtx, loop);

        try {
            long next = System.currentTimeMillis() + loop.intervalMinutes * 60_000L;
            JSONObject out = new JSONObject();
            out.put("ok", true);
            out.put("id", loop.id);
            out.put("name", loop.name);
            out.put("interval_minutes", loop.intervalMinutes);
            out.put("restarted", restarted);
            out.put("next_fire", CreateTaskTool.format(next));
            return ToolResult.success(out.toString());
        } catch (Exception e) {
            return ToolResult.error("loop was started but the result could not be serialized: " + e.getMessage());
        }
    }
}
