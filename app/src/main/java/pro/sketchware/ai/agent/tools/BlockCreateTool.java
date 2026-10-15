package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.utility.GsonUtils;
import mod.hey.studios.util.Helper;
import mod.hey.studios.editor.manage.block.v2.BlockLoader;
import pro.sketchware.ai.live.LiveRegistry;

public final class BlockCreateTool implements Tool {
    @Override public ToolSpec spec() {
        try {
            JSONObject props = new JSONObject().put("spec", new JSONObject().put("type", "object"));
            JSONObject schema = new JSONObject(); schema.put("type", "object"); schema.put("properties", props);
            schema.put("required", new org.json.JSONArray().put("spec"));
            return new ToolSpec("block_create", "Creates a custom block in the configured shared block registry; explicit confirmation is required.", schema);
        } catch (Exception e) { throw new IllegalStateException("Could not construct block_create schema.", e); }
    }
    @Override public Domain domain() { return Domain.BLOCK; }
    @Override public RiskLevel riskLevel(JSONObject args) { return RiskLevel.DESTRUCTIVE; }
    @Override public String approvalSummary(JSONObject args, ToolContext ctx) {
        JSONObject spec = args == null ? null : args.optJSONObject("spec");
        return "Shared custom-block registry: " + ProjectPaths.blocksRoot(ctx.sc_id)
                + "; create block: " + (spec == null ? "(name not supplied)" : spec.optString("name", "(name missing)"));
    }

    @Override public ToolResult run(JSONObject args, ToolContext ctx) {
        String path = null;
        File registry = null;
        String serialized = null;
        String beforeHash = null;
        try {
            JSONObject newSpec = args.getJSONObject("spec");
            String id = newSpec.optString("name", "").trim();
            if (id.isEmpty()) return ToolResult.structured("block_create", "INVALID_ARGUMENTS", null, "Block name is required.", null, true);

            path = ProjectPaths.blocksRoot(ctx.sc_id);
            registry = new File(path);
            if (registry.exists() && !registry.isFile()) {
                return ToolResult.structured("block_create", "ERROR", path, "The configured block registry is not a regular file.", null, true);
            }
            beforeHash = registry.isFile() ? DomainToolHelper.sha256(registry) : null;
            ArrayList<HashMap<String, Object>> blocks = new ArrayList<>();
            if (registry.isFile()) {
                String current = DomainToolHelper.readUtf8(registry);
                if (!beforeHash.equals(DomainToolHelper.sha256(registry))) {
                    return ToolResult.structured("block_create", "CONFLICT", path, "The shared block registry changed while being read; no update was applied.", null, true);
                }
                blocks = GsonUtils.getGson().fromJson(current, Helper.TYPE_MAP_LIST);
            }
            if (blocks == null) blocks = new ArrayList<>();
            for (HashMap<String, Object> block : blocks) {
                if (block != null && id.equals(String.valueOf(block.get("name")))) {
                    return ToolResult.structured("block_create", "CONFLICT", path,
                            "A custom block with this name already exists; it was not overwritten.", null, true);
                }
            }
            if (!ctx.checkPermission(path, registry.exists())) {
                return ToolResult.structured("block_create", "DENIED", path, "Write permission was denied.", null, true);
            }

            HashMap<String, Object> map = GsonUtils.getGson().fromJson(newSpec.toString(), Helper.TYPE_MAP);
            if (map == null) return ToolResult.structured("block_create", "INVALID_ARGUMENTS", path, "Block specification could not be parsed.", null, true);
            blocks.add(map);
            serialized = GsonUtils.getGson().toJson(blocks);
            if (registry.isFile()) DomainToolHelper.backup(path);
            DomainToolHelper.atomicWrite(registry, serialized, beforeHash);
            if (!serialized.equals(DomainToolHelper.readUtf8(registry))) {
                boolean restored = registry.isFile() && DomainToolHelper.restoreIfUnchanged(path, DomainToolHelper.contentSha256(serialized));
                return ToolResult.structured("block_create", "VERIFICATION_FAILED", path,
                        "Registry read-back did not match. Original restoration " + (restored ? "was verified." : "could not be safely verified."), null, true);
            }
            BlockLoader.refresh();
            LiveRegistry.notifyChanged("blocks", path);
            return ToolResult.structured("block_create", "SUCCESS", path,
                    "Registry JSON read-back verified and BlockLoader.refresh completed. Visual availability in the block picker was not device-tested.", null, false);
        } catch (Exception e) {
            boolean restored = false;
            try {
                if (registry != null && registry.isFile() && serialized != null
                        && DomainToolHelper.contentSha256(serialized).equals(DomainToolHelper.sha256(registry))) {
                    restored = DomainToolHelper.restoreIfUnchanged(path, DomainToolHelper.contentSha256(serialized));
                    if (restored) BlockLoader.refresh();
                }
            } catch (Exception ignored) {}
            String message = e.getMessage() == null ? "Custom block creation failed." : e.getMessage();
            if (restored) message += " Original registry restoration was verified.";
            return ToolResult.structured("block_create", "ERROR", path, message, null, true);
        }
    }
}
