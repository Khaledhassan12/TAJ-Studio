package pro.sketchware.ai.agent.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import pro.sketchware.ai.agent.Tool;
import a.a.a.lC;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public final class ProjectInfoTool implements Tool {

    private static final Set<String> INTERNAL_FIELDS = new HashSet<>();
    static {
        INTERNAL_FIELDS.add("sc_id");
        INTERNAL_FIELDS.add("my_sc_id");
        INTERNAL_FIELDS.add("my_sc_reg_dt");
        INTERNAL_FIELDS.add("agent_bound_project_id");
        INTERNAL_FIELDS.add("is_active_agent_project");
    }

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            params.put("properties", new JSONObject().put("sc_id",
                    new JSONObject().put("type", "string")
                            .put("description", "Optional internal project selector. Normally omit it; this agent is already bound to the project open in the editor.")));
            return new ToolSpec("project_info",
                    "Reads metadata for the project already bound to this agent task. The returned metadata uses human-readable field labels, not Sketchware database variable names. Use these facts to answer questions about the active project; do not expose internal database keys, internal project IDs, class/method names, source code, or implementation identifiers unless the user explicitly asks for technical internals. Omit sc_id.",
                    params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.PROJECT;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String requestedId = args.optString("sc_id", ctx.sc_id);
        if (requestedId == null || requestedId.trim().isEmpty()) {
            return ToolResult.structured("project_info", "NO_ACTIVE_PROJECT", null,
                    "No active project is bound to this agent task.", null, true);
        }
        if (ctx.sc_id == null || !ctx.sc_id.equals(requestedId)) {
            return ToolResult.structured("project_info", "PROJECT_MISMATCH", null,
                    "The requested project does not match the project bound to this agent task.", null, true);
        }

        HashMap<String, Object> raw = lC.b(ctx.sc_id);
        if (raw == null) return ToolResult.structured("project_info", "NOT_FOUND", null,
                "Metadata for the project open in the editor could not be found.", null, true);

        JSONObject metadata = new JSONObject();
        JSONArray omittedInternalFields = new JSONArray();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = entry.getKey();
            if (key == null || isInternalField(key)) {
                if (key != null) omittedInternalFields.put(humanize(key));
                continue;
            }
            Object value = entry.getValue();
            if (value == null) continue;
            String label = humanize(key);
            try {
                metadata.put(label, value);
            } catch (org.json.JSONException ignored) {
                metadata.put(label, String.valueOf(value));
            }
        }

        JSONObject result = new JSONObject();
        result.put("status", "SUCCESS");
        result.put("scope", "Project currently open in the editor");
        result.put("metadata", metadata);
        result.put("reporting_guidance",
                "Present metadata labels and values in natural language. Never repeat raw database field names, internal project IDs, class or method names, source code, or implementation identifiers in a normal project overview. Do not infer file contents from filenames or metadata. Clearly separate metadata from files actually inspected.");
        return new ToolResult(result.toString(), false);
    }

    private static boolean isInternalField(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return INTERNAL_FIELDS.contains(normalized)
                || normalized.equals("id")
                || normalized.endsWith("_id")
                || normalized.contains("reg_dt")
                || normalized.contains("internal");
    }

    private static String humanize(String key) {
        String value = key.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .trim()
                .toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return "Other metadata";
        StringBuilder out = new StringBuilder();
        boolean capitalize = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c)) {
                out.append(c);
                capitalize = true;
            } else {
                out.append(capitalize ? Character.toUpperCase(c) : c);
                capitalize = false;
            }
        }
        return out.toString();
    }
}
