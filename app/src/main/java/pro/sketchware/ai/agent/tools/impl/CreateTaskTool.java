package pro.sketchware.ai.agent.tools.impl;

import android.content.Context;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import pro.sketchware.ai.agent.tools.Tool;
import pro.sketchware.ai.agent.tools.ToolArgs;
import pro.sketchware.ai.agent.tools.ToolCtx;
import pro.sketchware.ai.agent.tools.ToolResult;
import pro.sketchware.ai.agent.tools.ToolSpec;
import pro.sketchware.ai.automation.AutomationSettings;
import pro.sketchware.ai.automation.CronExpression;
import pro.sketchware.ai.automation.TaskEntity;
import pro.sketchware.ai.automation.TaskManager;
import pro.sketchware.ai.automation.TaskStore;

/**
 * [WHAT] create_task: model creates a scheduled task bound to THIS conversation
 * (P2-AU, D17). One-shot (minutes_from_now) or recurring (5-field cron).
 * [WHY] Fires execute headlessly in the creating conversation. Every result
 * is honest: parse errors, duplicate names and no-future-fire are surfaced.
 */
public class CreateTaskTool implements Tool {

    @Override
    public ToolSpec spec() {
        return new ToolSpec("create_task",
                "Create a scheduled task that sends a prompt to THIS conversation later. " +
                "One-shot via minutes_from_now, or recurring via 5-field cron (minute hour day month weekday; supports * , - /).",
                "{\"name\": \"string\", \"prompt\": \"string\", \"minutes_from_now\": \"number (one-shot)\", \"cron\": \"string (recurring, e.g. \\\"*/30 * * * *\\\")\"}");
    }

    @Override
    public ToolResult execute(ToolArgs args, ToolCtx ctx) {
        Context appCtx = ctx.context.getApplicationContext();
        if (!AutomationSettings.get(appCtx).isTasksLoopsEnabled()) {
            return ToolResult.error("Tasks & Loops access is disabled in Automation settings");
        }
        if (ctx.conversationId == null || ctx.conversationId.isEmpty()) {
            return ToolResult.error("create_task can only run inside a conversation turn");
        }

        String name = args.getString("name");
        String prompt = args.getString("prompt");
        if (name == null || name.trim().isEmpty()) return ToolResult.error("name is required");
        if (prompt == null || prompt.trim().isEmpty()) return ToolResult.error("prompt is required");
        name = name.trim();

        TaskStore store = TaskStore.get(appCtx);
        if (store.findByName(name) != null) {
            return ToolResult.error("a task named '" + name + "' already exists — delete it first or choose another name");
        }

        String minutes = args.getString("minutes_from_now");
        String cron = args.getString("cron");
        boolean hasMinutes = minutes != null && !minutes.trim().isEmpty();
        boolean hasCron = cron != null && !cron.trim().isEmpty();
        if (hasMinutes == hasCron) {
            return ToolResult.error("provide exactly one of minutes_from_now (one-shot) or cron (recurring)");
        }

        TaskEntity task = new TaskEntity();
        task.id = UUID.randomUUID().toString();
        task.name = name;
        task.prompt = prompt;
        task.scId = ctx.scId;
        task.conversationId = ctx.conversationId;
        task.createdAt = System.currentTimeMillis();
        task.enabled = true;

        long now = System.currentTimeMillis();
        if (hasCron) {
            task.cronExpr = cron.trim();
            try {
                new CronExpression(task.cronExpr); // honest parse errors, never stored invalid
            } catch (IllegalArgumentException e) {
                return ToolResult.error("invalid cron: " + e.getMessage());
            }
        } else {
            long mins;
            try {
                mins = Long.parseLong(minutes.trim());
            } catch (NumberFormatException e) {
                return ToolResult.error("minutes_from_now must be a whole number, got '" + minutes.trim() + "'");
            }
            if (mins <= 0) return ToolResult.error("minutes_from_now must be > 0");
            task.runAt = now + mins * 60_000L;
        }

        long next = TaskManager.nextFireTime(task, now);
        if (next <= 0) {
            return ToolResult.error("task has no future fire time — check the cron expression");
        }

        store.upsert(task);
        TaskManager.schedule(appCtx, task);

        try {
            JSONObject out = new JSONObject();
            out.put("ok", true);
            out.put("id", task.id);
            out.put("name", task.name);
            out.put("type", task.isCron() ? "cron" : "one_shot");
            if (task.isCron()) out.put("cron", task.cronExpr);
            out.put("next_fire", format(next));
            out.put("conversation_id", task.conversationId);
            return ToolResult.success(out.toString());
        } catch (Exception e) {
            return ToolResult.error("task was created but the result could not be serialized: " + e.getMessage());
        }
    }

    static String format(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(millis));
    }
}
