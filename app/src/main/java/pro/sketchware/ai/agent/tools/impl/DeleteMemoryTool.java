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
 * [WHAT] delete_memory: removes one saved memory by id or title (P2-MEM, D18).
 * Honest not-found error otherwise.
 */
public class DeleteMemoryTool implements Tool {

    @Override
    public ToolSpec spec() {
        return new ToolSpec("delete_memory",
                "Delete a saved memory by its id or title",
                "{\"id_or_title\": \"string\"}");
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

        store.delete(entry.id);

        try {
            JSONObject out = new JSONObject();
            out.put("ok", true);
            out.put("deleted", entry.title);
            out.put("id", entry.id);
            return ToolResult.success(out.toString());
        } catch (Exception e) {
            return ToolResult.error("memory was deleted but the result could not be serialized: " + e.getMessage());
        }
    }
}
