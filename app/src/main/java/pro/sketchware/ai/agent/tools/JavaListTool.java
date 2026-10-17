package pro.sketchware.ai.agent.tools;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.ai.agent.Tool;

public final class JavaListTool implements Tool {
    private static final int MAX_FILES = 500;
    private static final int MAX_DEPTH = 12;

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("target", new JSONObject().put("type", "string")
                    .put("enum", new JSONArray(new String[]{"manager", "project"})));
            props.put("sc_id", new JSONObject().put("type", "string")
                    .put("description", "Optional; the active project is selected automatically."));
            params.put("properties", props);
            params.put("required", new JSONArray().put("target"));
            return new ToolSpec("java_list",
                    "Lists Java/Kotlin source files recursively, including subpackages, with bounded results and explicit partial status.",
                    params);
        } catch (Exception e) {
            throw new IllegalStateException("Could not construct java_list schema.", e);
        }
    }

    @Override
    public Domain domain() {
        return Domain.JAVA;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) {
        String target = args.optString("target", "");
        if (!"manager".equals(target) && !"project".equals(target)) {
            return ToolResult.structured("java_list", "INVALID_ARGUMENTS", null,
                    "target must be manager or project.", null, true);
        }

        String rootPath = "manager".equals(target)
                ? ProjectPaths.javaManagerRoot(ctx.sc_id)
                : ProjectPaths.projectJavaRoot(ctx.sc_id);
        File root = new File(rootPath);
        if (!root.isDirectory()) {
            return ToolResult.structured("java_list", "NOT_FOUND", rootPath,
                    "The selected Java/Kotlin source directory does not exist.", null, true);
        }

        ListState state = new ListState();
        collect(ctx.sc_id, root, state, 0);

        JSONObject result = new JSONObject();
        try {
            JSONArray files = new JSONArray();
            for (String file : state.files) files.put(file);
            result.put("tool", "java_list");
            result.put("target", target);
            result.put("root", root.getAbsolutePath());
            result.put("files", files);
            result.put("count", state.files.size());
            result.put("truncated", state.truncated);
            result.put("status", state.truncated ? "PARTIAL" : "SUCCESS");
            if (state.truncated) {
                result.put("message", "Source listing reached its safety limit; results are incomplete.");
            }
        } catch (org.json.JSONException e) {
            return ToolResult.structured("java_list", "ERROR", rootPath,
                    "Could not serialize the source listing.", null, true);
        }
        return new ToolResult(result.toString(), state.truncated);
    }

    private static final class ListState {
        final List<String> files = new ArrayList<>();
        boolean truncated;
    }

    private void collect(String scId, File directory, ListState state, int depth) {
        if (state.truncated) return;
        if (depth > MAX_DEPTH || state.files.size() >= MAX_FILES) {
            state.truncated = true;
            return;
        }

        try {
            if (Files.isSymbolicLink(directory.toPath())) {
                state.truncated = true;
                return;
            }
            PathResolver.validateProjectPath(scId, directory);
        } catch (Exception e) {
            state.truncated = true;
            return;
        }

        File[] children = directory.listFiles();
        if (children == null) {
            state.truncated = true;
            return;
        }
        List<File> ordered = new ArrayList<>();
        Collections.addAll(ordered, children);
        ordered.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        for (File child : ordered) {
            if (state.truncated) return;
            String name = child.getName();
            if (name.endsWith(".bak") || name.contains(".agent-backup-")
                    || name.contains(".agent_trash")) continue;

            try {
                if (Files.isSymbolicLink(child.toPath())) {
                    state.truncated = true;
                    return;
                }
                PathResolver.validateProjectPath(scId, child);
            } catch (Exception e) {
                state.truncated = true;
                return;
            }

            if (child.isDirectory()) {
                collect(scId, child, state, depth + 1);
            } else if (child.isFile()
                    && (name.endsWith(".java") || name.endsWith(".kt"))) {
                state.files.add(child.getAbsolutePath());
                if (state.files.size() >= MAX_FILES) {
                    state.truncated = true;
                    return;
                }
            }
        }
    }
}
