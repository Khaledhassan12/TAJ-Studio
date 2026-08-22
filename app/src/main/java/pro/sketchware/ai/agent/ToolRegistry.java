package pro.sketchware.ai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONObject;

import pro.sketchware.ai.agent.tools.*;
import pro.sketchware.ai.agent.tools.fs.*;

public final class ToolRegistry {

    private final Map<String, Tool> tools = new HashMap<>();

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

    public void register(Tool tool) {
        tools.put(tool.spec().name, tool);
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public List<Tool> all() {
        return new ArrayList<>(tools.values());
    }
}
