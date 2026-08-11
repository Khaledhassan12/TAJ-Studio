package pro.sketchware.ai.agent.tools.impl;

import android.content.Context;

import org.json.JSONObject;

import java.util.UUID;

import pro.sketchware.ai.agent.tools.Tool;
import pro.sketchware.ai.agent.tools.ToolArgs;
import pro.sketchware.ai.agent.tools.ToolCtx;
import pro.sketchware.ai.agent.tools.ToolResult;
import pro.sketchware.ai.agent.tools.ToolSpec;
import pro.sketchware.ai.memory.MemoryEntry;
import pro.sketchware.ai.memory.MemoryStore;

/**
 * [WHAT] create_memory: persists a NEW saved memory (P2-MEM, D18).
 * Honest errors: missing fields, duplicate title.
 */
public class CreateMemoryTool implements Tool {

    @Override
    public ToolSpec spec() {
        return new ToolSpec("create_memory",
                "Create and save a new memory (facts, preferences, or context) that persists across conversations",
                "{\"title\": \"string\", \"content\": \"string\", \"description\": \"string (optional short summary)\"}");
    }

    @Override
    public ToolResult execute(ToolArgs args, ToolCtx ctx) {
        Context appCtx = ctx.context.getApplicationContext();
        MemoryStore store = MemoryStore.get(appCtx);
        if (!store.isSavedAccessEnabled()) {
            return ToolResult.error("Access to saved memories is disabled in TAJ Memory settings");
        }

        String title = args.getString("title");
        String content = args.getString("content");
        String description = args.getString("description");
        if (title == null || title.trim().isEmpty()) return ToolResult.error("title is required");
        if (content == null || content.trim().isEmpty()) return ToolResult.error("content is required");
        title = title.trim();

        if (store.findByTitle(title) != null) {
            return ToolResult.error("a memory titled '" + title + "' already exists — use edit_memory to change it");
        }

        MemoryEntry entry = new MemoryEntry();
        entry.id = UUID.randomUUID().toString();
        entry.title = title;
        entry.description = description == null ? null : description.trim();
        entry.content = content.trim();
        entry.updatedAt = System.currentTimeMillis();

        store.add(entry);

        try {
            JSONObject out = new JSONObject();
            out.put("ok", true);
            out.put("id", entry.id);
            out.put("title", entry.title);
            return ToolResult.success(out.toString());
        } catch (Exception e) {
            return ToolResult.error("memory was created but the result could not be serialized: " + e.getMessage());
        }
    }
}
