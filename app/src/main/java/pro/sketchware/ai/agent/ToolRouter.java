package pro.sketchware.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import pro.sketchware.ai.core.AIMessage;

/**
 * Selects a bounded, task-relevant tool catalog for each model turn.
 *
 * Discovery/read tools remain available so routing never prevents the agent from
 * inspecting the active project. Mutation tools are included only for likely task
 * domains; the registry remains the source of truth for actual execution.
 */
public final class ToolRouter {
    // Preserve earlier task intent across long assistant/tool exchanges without unbounded prompts.
    private static final int MAX_TASK_CONTEXT_CHARS = 16_000;

    private ToolRouter() {}

    public static List<Tool> select(ToolRegistry registry, List<AIMessage> conversation) {
        String task = recentTaskText(conversation).toLowerCase(Locale.ROOT);
        Set<String> wanted = new LinkedHashSet<>();

        // Keep project discovery and safe inspection available in every task.
        add(wanted,
                "project_info", "list_projects", "list_project_structure", "open_manager",
                "fs_list", "fs_info", "fs_read", "fs_search",
                "java_list", "java_read",
                "res_list", "res_read",
                "asset_list", "asset_read",
                "blocks_list", "block_read",
                "manifest_read", "libs_list");

        boolean code = hasAny(task, "kotlin", "java", "class", "source", "function", "method",
                "login screen", "screen", "activity", "fragment", "compose", ".kt", ".java",
                "edit code", "modify code", "create file");
        boolean resources = hasAny(task, "layout", "xml", "string resource", "color resource",
                "drawable", "resource", "view", "widget", "button", "textview");
        boolean assets = hasAny(task, "image", "asset", "font", "icon", "svg", "png", "jpg");
        boolean blocks = hasAny(task, "block", "logic block", "event", "component");
        boolean manifest = hasAny(task, "manifest", "permission", "application id", "package name");
        boolean libraries = hasAny(task, "library", "libraries", "dependency", "maven", "gradle dependency");
        boolean filesystem = hasAny(task, "file", "folder", "directory", "path", "copy", "move",
                "rename", "delete", "search", "find", "read", "write", "create", "remove");
        boolean build = hasAny(task, "build", "compile", "compilation", "gradle", "apk", "build error");
        boolean refresh = hasAny(task, "refresh", "reload", "reopen", "show in editor", "open screen");

        if (code) add(wanted, "java_create", "java_edit", "java_list", "java_read",
                "fs_write", "fs_mkdir", "fs_info");
        if (resources) add(wanted, "res_create", "res_edit", "res_delete", "res_move_copy",
                "insert_widget", "fs_write", "fs_mkdir");
        if (assets) add(wanted, "asset_create", "asset_edit", "asset_delete", "fs_copy", "fs_move");
        if (blocks) add(wanted, "block_create", "block_edit", "blocks_list", "block_read");
        if (manifest) add(wanted, "manifest_edit");
        if (libraries) add(wanted, "lib_add", "lib_remove");
        if (filesystem) add(wanted, "fs_write", "fs_delete", "fs_copy", "fs_move", "fs_mkdir",
                "fs_list", "fs_info", "fs_read", "fs_search");
        if (build) add(wanted, "build_project", "project_info", "list_project_structure");
        if (refresh) add(wanted, "refresh_ui", "open_manager");

        // A generic or ambiguous request gets the full catalog rather than an
        // overconfident guess that could hide a required capability.
        if (!(code || resources || assets || blocks || manifest || libraries || filesystem || build || refresh)) {
            return registry.all();
        }

        List<Tool> selected = new ArrayList<>();
        for (Tool tool : registry.all()) {
            Tool.ToolSpec spec = registry.spec(tool);
            if (spec == null) {
                throw new IllegalStateException("A registered tool has no validated schema snapshot.");
            }
            String name = spec.name.toLowerCase(Locale.ROOT);
            if (wanted.contains(name)) selected.add(tool);
        }

        // If tool naming changes or the classifier yields no useful catalog,
        // fail open. The runtime still validates every call against the registry.
        return selected.isEmpty() ? registry.all() : selected;
    }

    private static String recentTaskText(List<AIMessage> conversation) {
        StringBuilder task = new StringBuilder();
        if (conversation == null) return "";
        // Scan the complete conversation for user intent. A fixed count of the last
        // messages is unsafe because assistant/tool messages can push the original
        // request out of the window during multi-step tasks.
        for (AIMessage message : conversation) {
            if (message == null || !AIMessage.ROLE_USER.equals(message.role)
                    || message.text == null || message.text.isEmpty()) {
                continue;
            }
            if (task.length() > 0) task.append(' ');
            task.append(message.text);
            if (task.length() > MAX_TASK_CONTEXT_CHARS) {
                task.delete(0, task.length() - MAX_TASK_CONTEXT_CHARS);
            }
        }
        return task.toString();
    }

    private static void add(Set<String> names, String... values) {
        for (String value : values) names.add(value);
    }

    private static boolean hasAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) return true;
        }
        return false;
    }
}
