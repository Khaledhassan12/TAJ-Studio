package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;

public final class FsListTool implements Tool {
    private static final int MAX_ENTRIES = 500;
    private static final int MAX_DEPTH = 12;

    @Override public ToolSpec spec() {
        try {
            JSONObject props = new JSONObject();
            props.put("path", new JSONObject().put("type", "string").put("maxLength", 4096));
            props.put("recursive", new JSONObject().put("type", "boolean"));
            JSONObject schema = new JSONObject(); schema.put("type", "object"); schema.put("properties", props);
            schema.put("required", new JSONArray().put("path"));
            return new ToolSpec("fs_list", "Lists project files with bounded output and explicit partial-result reporting.", schema);
        } catch (Exception e) { throw new IllegalStateException("Could not construct fs_list schema.", e); }
    }
    @Override public Domain domain() { return Domain.FS; }

    @Override public ToolResult run(JSONObject args, ToolContext ctx) {
        String path = null;
        try {
            File root = PathResolver.resolve(ctx.sc_id, args.getString("path"));
            path = root.getAbsolutePath();
            if (!root.isDirectory()) return ToolResult.structured("fs_list", "NOT_FOUND", path,
                    "Path does not exist or is not a directory.", null, true);
            ListState state = new ListState();
            collect(root, state, args.optBoolean("recursive", false), 0);
            JSONObject result = new JSONObject();
            JSONArray entries = new JSONArray();
            for (String entry : state.entries) entries.put(entry);
            result.put("tool", "fs_list");
            result.put("root", path);
            result.put("entries", entries);
            result.put("count", state.entries.size());
            result.put("truncated", state.truncated);
            result.put("status", state.truncated ? "PARTIAL" : "SUCCESS");
            if (state.truncated) result.put("message", "Listing was truncated at the configured safety limit; not all entries are shown.");
            return new ToolResult(result.toString(), state.truncated);
        } catch (Exception e) {
            return ToolResult.structured("fs_list", "ERROR", path,
                    e.getMessage() == null ? "Listing failed before completion." : e.getMessage(), null, true);
        }
    }

    private static final class ListState {
        final List<String> entries = new ArrayList<>();
        boolean truncated;
    }

    private void collect(File directory, ListState state, boolean recursive, int depth) {
        if (state.truncated) return;
        if (depth > MAX_DEPTH || state.entries.size() >= MAX_ENTRIES) {
            state.truncated = true; return;
        }
        try {
            if (Files.isSymbolicLink(directory.toPath())) { state.truncated = true; return; }
        } catch (Exception e) { state.truncated = true; return; }
        File[] children = directory.listFiles();
        if (children == null) { state.truncated = true; return; }
        List<File> ordered = new ArrayList<>();
        Collections.addAll(ordered, children);
        ordered.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File child : ordered) {
            String name = child.getName();
            if (name.endsWith(".bak") || name.contains(".agent-backup-") || name.contains(".agent_trash")) continue;
            try {
                if (Files.isSymbolicLink(child.toPath())) { state.truncated = true; continue; }
            } catch (Exception e) { state.truncated = true; continue; }
            state.entries.add(child.getAbsolutePath());
            if (state.entries.size() >= MAX_ENTRIES) {
                state.truncated = true; return;
            }
            if (recursive && child.isDirectory()) collect(child, state, true, depth + 1);
            if (state.truncated) return;
        }
    }
}
