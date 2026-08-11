package pro.sketchware.ai.agent.tools.impl;

import android.content.Context;

import org.json.JSONObject;

import pro.sketchware.ai.agent.tools.Tool;
import pro.sketchware.ai.agent.tools.ToolArgs;
import pro.sketchware.ai.agent.tools.ToolCtx;
import pro.sketchware.ai.agent.tools.ToolResult;
import pro.sketchware.ai.agent.tools.ToolSpec;
import pro.sketchware.ai.memory.MemoryEntry;
import pro.sketchware.ai.memory.MemoryStore;

/**
 * [WHAT] edit_memory: updates ONLY the provided fields of a saved memory
 * (P2-MEM, D18). Honest not-found error otherwise.
 */
public class EditMemoryTool implements Tool {

    @Override
    public ToolSpec spec() {
        return new ToolSpec("edit_memory",
                "Edit an existing saved memory by id or title; provide only the fields to change (title, description, content)",
                "{\"id_or_title\": \"string\", \"title\": \"string (optional)\", \"description\": \"string (optional)\", \"content\": \"string (optional)\"}");
    }

    @Override
    public ToolResult execute(ToolArgs args, ToolCtx ctx) {
        Context appCtx = ctx.context.getApplicationContext();
        MemoryStore store = MemoryStore.get(appCtx);
        if (!store.isSavedAccessEnabled()) {
            return ToolResult.error("Access to saved memories is disabled in TAJ Memory settings");
        }

        String key = args.getString("id_or_title");
        if (key == null || key.trim().isEmpty()) return ToolResult.error("id_or_title is required");
        key = key.trim();

        MemoryEntry entry = store.findById(key);
        if (entry == null) entry = store.findByTitle(key);
        if (entry == null) {
            return ToolResult.error("no memory found with id or title '" + key + "' — use list_memories to see what exists");
        }

        String title = args.getString("title");
        String description = args.getString("description");
        String content = args.getString("content");
        boolean changed = false;
        if (title != null && !title.trim().isEmpty()) { entry.title = title.trim(); changed = true; }
        if (description != null) { entry.description = description.trim(); changed = true; }
        if (content != null && !content.trim().isEmpty()) { entry.content = content.trim(); changed = true; }
        if (!changed) {
            return ToolResult.error("nothing to edit — provide at least one of title, description, content");
        }
        entry.updatedAt = System.currentTimeMillis();
        store.update(entry);

        try {
            JSONObject out = new JSONObject();
            out.put("ok", true);
            out.put("id", entry.id);
            out.put("title", entry.title);
            out.put("updated_at", entry.updatedAt);
            return ToolResult.success(out.toString());
        } catch (Exception e) {
            return ToolResult.error("memory was edited but the result could not be serialized: " + e.getMessage());
        }
    }
}
