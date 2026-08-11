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
import pro.sketchware.ai.automation.TaskEntity;
import pro.sketchware.ai.automation.TaskManager;
import pro.sketchware.ai.automation.TaskStore;

/**
 * [WHAT] list_tasks: honest read-only listing of all persisted tasks (P2-AU, D17).
 */
public class ListTasksTool implements Tool {

    @Override
    public ToolSpec spec() {
        return new ToolSpec("list_tasks",
                "List all scheduled tasks with their type, schedule, enabled state and next fire time",
                "{}");
    }

    @Override
    public ToolResult execute(ToolArgs args, ToolCtx ctx) {
        Context appCtx = ctx.context.getApplicationContext();
        if (!AutomationSettings.get(appCtx).isTasksLoopsEnabled()) {
            return ToolResult.error("Tasks & Loops access is disabled in Automation settings");
        }

        try {
            List<TaskEntity> tasks = TaskStore.get(appCtx).list();
            long now = System.currentTimeMillis();
            JSONArray arr = new JSONArray();
            for (TaskEntity task : tasks) {
                JSONObject item = new JSONObject();
                item.put("id", task.id);
                item.put("name", task.name);
                item.put("type", task.isCron() ? "cron" : "one_shot");
                if (task.isCron()) item.put("cron", task.cronExpr);
                item.put("enabled", task.enabled);
                long next = TaskManager.nextFireTime(task, now);
                item.put("next_fire", task.enabled && next > 0 ? CreateTaskTool.format(next) : "none");
                arr.put(item);
            }
            JSONObject out = new JSONObject();
            out.put("ok", true);
            out.put("count", arr.length());
            out.put("tasks", arr);
            return ToolResult.success(out.toString());
        } catch (Exception e) {
            return ToolResult.error("list_tasks failed: " + e.getMessage());
        }
    }
}
