package pro.sketchware.ai.agent.tools.impl;

import android.content.Context;

import org.json.JSONObject;

import pro.sketchware.ai.agent.tools.Tool;
import pro.sketchware.ai.agent.tools.ToolArgs;
import pro.sketchware.ai.agent.tools.ToolCtx;
import pro.sketchware.ai.agent.tools.ToolResult;
import pro.sketchware.ai.agent.tools.ToolSpec;
import pro.sketchware.ai.memory.MemoryStore;

/**
 * [WHAT] update_active_memory: sets the ALWAYS-INCLUDED active memory text
 * (P2-MEM, D18). Empty content clears it honestly.
 */
public class UpdateActiveMemoryTool implements Tool {

    @Override
    public ToolSpec spec() {
        return new ToolSpec("update_active_memory",
                "Replace the active memory — a short text included in EVERY conversation and API call. Use for facts, preferences, or context the user should always be remembered for. Empty content clears it.",
                "{\"content\": \"string\"}");
    }

    @Override
    public ToolResult execute(ToolArgs args, ToolCtx ctx) {
        Context appCtx = ctx.context.getApplicationContext();
        MemoryStore store = MemoryStore.get(appCtx);
        if (!store.isActiveAccessEnabled()) {
            return ToolResult.error("Access to active memory is disabled in TAJ Memory settings");
        }

        String content = args.getString("content");
        if (content == null) content = "";
        store.setActiveContent(content);

        try {
            boolean set = !content.trim().isEmpty();
            JSONObject out = new JSONObject();
            out.put("ok", true);
            out.put("active_memory_set", set);
            out.put("note", set
                    ? "active memory now included in every call"
                    : "active memory cleared — nothing will be injected");
            return ToolResult.success(out.toString());
        } catch (Exception e) {
            return ToolResult.error("active memory was updated but the result could not be serialized: " + e.getMessage());
        }
    }
}
