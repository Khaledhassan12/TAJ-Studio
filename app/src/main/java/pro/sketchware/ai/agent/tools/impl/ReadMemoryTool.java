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
 * [WHAT] read_memory: returns the FULL content of one saved memory (P2-MEM, D18).
 */
public class ReadMemoryTool implements Tool {

    @Override
    public ToolSpec spec() {
        return new ToolSpec("read_memory",
                "Read the full content of one saved memory by its id or title",
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

        try {
            JSONObject out = new JSONObject();
            out.put("id", entry.id);
            out.put("title", entry.title);
            if (entry.description != null && !entry.description.isEmpty()) out.put("description", entry.description);
            out.put("content", entry.content);
            out.put("updated_at", entry.updatedAt);
            return ToolResult.success(out.toString());
        } catch (Exception e) {
            return ToolResult.error("read_memory failed: " + e.getMessage());
        }
    }
}
