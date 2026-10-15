package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.utility.GsonUtils;
import mod.hey.studios.util.Helper;
import mod.hey.studios.editor.manage.block.v2.BlockLoader;
import pro.sketchware.ai.live.LiveRegistry;

public final class BlockEditTool implements Tool {
    @Override public ToolSpec spec() {
        try {
            JSONObject props = new JSONObject();
            props.put("id", new JSONObject().put("type", "string").put("maxLength", 256));
            props.put("spec", new JSONObject().put("type", "object"));
            JSONObject schema = new JSONObject(); schema.put("type", "object"); schema.put("properties", props);
            schema.put("required", new JSONArray().put("id").put("spec"));
            return new ToolSpec("block_edit", "Edits one block in the configured shared block registry; explicit confirmation is required.", schema);
        } catch (Exception e) { throw new IllegalStateException("Could not construct block_edit schema.", e); }
    }
    @Override public Domain domain() { return Domain.BLOCK; }
    @Override public RiskLevel riskLevel(JSONObject args) { return RiskLevel.DESTRUCTIVE; }
    @Override public String approvalSummary(JSONObject args, ToolContext ctx) {
        String id = args == null ? "(id missing)" : args.optString("id", "(id missing)");
        return "Shared custom-block registry: " + ProjectPaths.blocksRoot(ctx.sc_id) + "; edit block: " + id;
    }

    @Override public ToolResult run(JSONObject args, ToolContext ctx) {
        String path = null;
        File registry = null;
        String serialized = null;
        String beforeHash = null;
        try {
            String id = args.getString("id");
            JSONObject newSpec = args.getJSONObject("spec");
            if (!id.equals(newSpec.optString("name", id))) {
                return ToolResult.structured("block_edit", "INVALID_ARGUMENTS", null,
                        "The edited specification's name must remain equal to the selected block ID.", null, true);
            }

            path = ProjectPaths.blocksRoot(ctx.sc_id);
            registry = new File(path);
            if (!registry.isFile()) return ToolResult.structured("block_edit", "NOT_FOUND", path, "The shared custom-block registry does not exist.", null, true);
            beforeHash = DomainToolHelper.sha256(registry);
            String current = DomainToolHelper.readUtf8(registry);
            if (!beforeHash.equals(DomainToolHelper.sha256(registry))) {
                return ToolResult.structured("block_edit", "CONFLICT", path, "The block registry changed while being read; no update was applied.", null, true);
            }
            ArrayList<HashMap<String, Object>> blocks = GsonUtils.getGson().fromJson(current, Helper.TYPE_MAP_LIST);
            if (blocks == null) return ToolResult.structured("block_edit", "INVALID_DATA", path, "The block registry could not be decoded as a list.", null, true);
            if (!ctx.checkPermission(path, true)) return ToolResult.structured("block_edit", "DENIED", path, "Write permission was denied.", null, true);

            HashMap<String, Object> nextMap = GsonUtils.getGson().fromJson(newSpec.toString(), Helper.TYPE_MAP);
            if (nextMap == null) return ToolResult.structured("block_edit", "INVALID_ARGUMENTS", path, "The new block specification could not be parsed.", null, true);
            boolean found = false;
            for (int i = 0; i < blocks.size(); i++) {
                HashMap<String, Object> block = blocks.get(i);
                if (block != null && id.equals(String.valueOf(block.get("name")))) {
                    blocks.set(i, nextMap);
                    found = true;
                    break;
                }
            }
            if (!found) return ToolResult.structured("block_edit", "NOT_FOUND", path, "Block ID was not found in the shared registry.", null, true);

            serialized = GsonUtils.getGson().toJson(blocks);
            DomainToolHelper.backup(path);
            DomainToolHelper.atomicWrite(registry, serialized, beforeHash);
            if (!serialized.equals(DomainToolHelper.readUtf8(registry))) {
                boolean restored = DomainToolHelper.restoreIfUnchanged(path, DomainToolHelper.contentSha256(serialized));
                return ToolResult.structured("block_edit", "VERIFICATION_FAILED", path,
                        "Registry read-back did not match. Original restoration " + (restored ? "was verified." : "could not be safely verified."), null, true);
            }
            BlockLoader.refresh();
            LiveRegistry.notifyChanged("blocks", path);
            return ToolResult.structured("block_edit", "SUCCESS", path,
                    "Registry JSON read-back verified and BlockLoader.refresh completed. Visual rendering was not device-tested.", null, false);
        } catch (Exception e) {
            boolean restored = false;
            try {
                if (registry != null && registry.isFile() && serialized != null
                        && DomainToolHelper.contentSha256(serialized).equals(DomainToolHelper.sha256(registry))) {
                    restored = DomainToolHelper.restoreIfUnchanged(path, DomainToolHelper.contentSha256(serialized));
                    if (restored) BlockLoader.refresh();
                }
            } catch (Exception ignored) {}
            String message = e.getMessage() == null ? "Custom block edit failed." : e.getMessage();
            if (restored) message += " Original registry restoration was verified.";
            return ToolResult.structured("block_edit", "ERROR", path, message, null, true);
        }
    }
}
