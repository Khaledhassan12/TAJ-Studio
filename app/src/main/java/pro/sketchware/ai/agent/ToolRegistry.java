package pro.sketchware.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import pro.sketchware.ai.agent.tools.*;
import pro.sketchware.ai.agent.tools.fs.*;

/**
 * Single source of truth for tools exposed to model providers and executed by the runtime.
 * Stable ordering makes provider payloads, logs, and tests deterministic.
 */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry() {
        // Project Tools
        register(new BuildProjectTool());
        register(new ListProjectStructureTool());
        register(new OpenManagerTool());
        register(new ListProjectsTool());
        register(new ProjectInfoTool());
        register(new RefreshUiTool());

        // Filesystem Tools
        register(new FsListTool());
        register(new FsReadTool());
        register(new FsWriteTool());
        register(new FsDeleteTool());
        register(new FsCopyTool());
        register(new FsMoveTool());
        register(new FsMkdirTool());
        register(new FsInfoTool());
        register(new FsSearchTool());

        // Java/Kotlin Tools
        register(new JavaListTool());
        register(new JavaReadTool());
        register(new JavaCreateTool());
        register(new JavaEditTool());

        // Resource Tools
        register(new ResListTool());
        register(new ResReadTool());
        register(new ResCreateTool());
        register(new ResEditTool());
        register(new ResDeleteTool());
        register(new ResMoveCopyTool());
        register(new InsertWidgetTool());

        // Asset Tools
        register(new AssetListTool());
        register(new AssetReadTool());
        register(new AssetCreateTool());
        register(new AssetEditTool());
        register(new AssetDeleteTool());

        // Block Tools
        register(new BlocksListTool());
        register(new BlockReadTool());
        register(new BlockCreateTool());
        register(new BlockEditTool());

        // Manifest Tools
        register(new ManifestReadTool());
        register(new ManifestEditTool());

        // Library Tools
        register(new LibsListTool());
        register(new LibAddTool());
        register(new LibRemoveTool());
    }

    /**
     * Fail fast during registry construction rather than silently exposing an incomplete
     * or ambiguous tool catalog to the model.
     */
    public synchronized void register(Tool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("Cannot register a null tool.");
        }

        Tool.ToolSpec spec = tool.spec();
        if (spec == null) {
            throw new IllegalArgumentException("Tool returned a null specification: "
                    + tool.getClass().getName());
        }
        if (spec.name == null || spec.name.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool name must not be empty: "
                    + tool.getClass().getName());
        }
        if (spec.description == null || spec.description.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool description must not be empty: " + spec.name);
        }
        JSONObject schema = spec.parameters;
        if (schema == null || !"object".equals(schema.optString("type"))
                || schema.optJSONObject("properties") == null) {
            throw new IllegalArgumentException("Tool must expose an object JSON schema with properties: "
                    + spec.name);
        }
        if (tools.containsKey(spec.name)) {
            throw new IllegalArgumentException("Duplicate agent tool name: " + spec.name);
        }
        tools.put(spec.name, tool);
    }

    public synchronized Tool get(String name) {
        return tools.get(name);
    }

    public synchronized List<Tool> all() {
        return new ArrayList<>(tools.values());
    }
}
