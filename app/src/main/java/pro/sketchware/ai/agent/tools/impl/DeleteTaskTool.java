package pro.sketchware.ai.agent.tools.impl;

import android.content.Context;

import org.json.JSONObject;

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
 * [WHAT] delete_task: removes a task by id or name and cancels its alarm
 * (P2-AU, D17). Honest error when nothing matches.
 */
public class DeleteTaskTool implements Tool {

    @Override
    public ToolSpec spec() {
        return new ToolSpec("delete_task",
                "Delete a scheduled task by its id or name and cancel its pending alarm",
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

        TaskStore store = TaskStore.get(appCtx);
        TaskEntity task = store.findById(key);
        if (task == null) task = store.findByName(key);
        if (task == null) {
            return ToolResult.error("no task found with id or name '" + key + "' — use list_tasks to see what exists");
        }

        TaskManager.cancel(appCtx, task.id);
        store.delete(task.id);

        try {
            JSONObject out = new JSONObject();
            out.put("ok", true);
            out.put("deleted", task.name);
            out.put("id", task.id);
            return ToolResult.success(out.toString());
        } catch (Exception e) {
            return ToolResult.error("task was deleted but the result could not be serialized: " + e.getMessage());
        }
    }
}
