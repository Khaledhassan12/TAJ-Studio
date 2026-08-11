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
import pro.sketchware.ai.memory.MemoryEntry;
import pro.sketchware.ai.memory.MemoryStore;

/**
 * [WHAT] list_memories: honest read-only listing of saved memories (P2-MEM, D18).
 */
public class ListMemoriesTool implements Tool {

    @Override
    public ToolSpec spec() {
        return new ToolSpec("list_memories",
                "List all saved memories (id, title, description, updated_at) without their full content",
                "{}");
    }

    @Override
    public ToolResult execute(ToolArgs args, ToolCtx ctx) {
        Context appCtx = ctx.context.getApplicationContext();
        MemoryStore store = MemoryStore.get(appCtx);
        if (!store.isSavedAccessEnabled()) {
            return ToolResult.error("Access to saved memories is disabled in TAJ Memory settings");
        }

        try {
            List<MemoryEntry> memories = store.list();
            JSONArray arr = new JSONArray();
            for (MemoryEntry e : memories) {
                JSONObject item = new JSONObject();
                item.put("id", e.id);
                item.put("title", e.title);
                if (e.description != null && !e.description.isEmpty()) item.put("description", e.description);
                item.put("updated_at", e.updatedAt);
                arr.put(item);
            }
            JSONObject out = new JSONObject();
            out.put("count", arr.length());
            out.put("memories", arr);
            return ToolResult.success(out.toString());
        } catch (Exception e) {
            return ToolResult.error("list_memories failed: " + e.getMessage());
        }
    }
}
