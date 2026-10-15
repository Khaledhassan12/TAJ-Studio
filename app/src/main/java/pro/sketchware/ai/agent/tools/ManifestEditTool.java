package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.tools.DomainToolHelper;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.GsonUtils;
import pro.sketchware.utility.FilePathUtil;
import mod.hey.studios.util.Helper;
import a.a.a.yq;
import a.a.a.jC;
import pro.sketchware.SketchApplication;
import pro.sketchware.ai.live.LiveRegistry;

public final class ManifestEditTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("op", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"add_permission", "set_application_attribute", "add_activity_entry"})));
            props.put("name", new JSONObject().put("type", "string"));
            props.put("value", new JSONObject().put("type", "string"));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("op"));
            return new ToolSpec("manifest_edit", "Edits the manifest injection model.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.MANIFEST;
    }

    @Override public RiskLevel riskLevel(JSONObject args) { return RiskLevel.DESTRUCTIVE; }
    @Override public String approvalSummary(JSONObject args, ToolContext ctx) {
        String op = args == null ? "(operation missing)" : args.optString("op", "(operation missing)");
        String path = ProjectPaths.manifestInjectionPath(ctx.sc_id);
        if ("add_permission".equals(op)) path = new FilePathUtil().getPathPermission(ctx.sc_id);
        return "Active-project manifest/permission metadata: " + path + "; requested operation: " + op
                + "; name: " + (args == null ? "" : args.optString("name", ""));
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String op = args.getString("op");
        String sc_id = ctx.sc_id;
        String injPath = ProjectPaths.manifestInjectionPath(sc_id);

        if ("add_permission".equals(op)) {
            String perm = args.getString("name");
            if (perm == null || !perm.matches("[A-Za-z0-9_.]+")) {
                return ToolResult.structured("manifest_edit", "INVALID_ARGUMENTS", null,
                        "Permission must use a fully qualified Android permission name.", null, true);
            }
            String permissionPath = new FilePathUtil().getPathPermission(sc_id);
            File permissionFile = new File(permissionPath);
            ArrayList<String> perms = new ArrayList<>();
            if (permissionFile.isFile()) {
                perms = GsonUtils.getGson().fromJson(DomainToolHelper.readUtf8(permissionFile),
                        new com.google.gson.reflect.TypeToken<ArrayList<String>>(){}.getType());
            }
            if (perms == null) perms = new ArrayList<>();
            boolean changed = false;
            if (!perms.contains(perm)) { perms.add(perm); changed = true; }
            if (!changed) return verify(ctx, sc_id, perm);
            if (!ctx.checkPermission(permissionPath, permissionFile.exists())) {
                return ToolResult.structured("manifest_edit", "DENIED", permissionPath, "Permission was denied.", null, true);
            }
            String beforeHash = permissionFile.isFile() ? DomainToolHelper.sha256(permissionFile) : null;
            if (permissionFile.isFile()) DomainToolHelper.backup(permissionPath);
            String serialized = GsonUtils.getGson().toJson(perms);
            DomainToolHelper.atomicWrite(permissionFile, serialized, beforeHash);
            if (!serialized.equals(DomainToolHelper.readUtf8(permissionFile))) {
                return ToolResult.structured("manifest_edit", "VERIFICATION_FAILED", permissionPath,
                        "Permission metadata read-back did not match the requested update.", null, true);
            }
            return verify(ctx, sc_id, perm);
        }

        ArrayList<HashMap<String, Object>> data = new ArrayList<>();
        if (FileUtil.isExistFile(injPath)) {
            data = GsonUtils.getGson().fromJson(FileUtil.readFile(injPath), Helper.TYPE_MAP_LIST);
        }
        if (data == null) data = new ArrayList<>();

        if ("set_application_attribute".equals(op)) {
            String key = args.getString("name");
            String val = args.getString("value");
            HashMap<String, Object> item = new HashMap<>();
            item.put("name", "_application_attrs");
            item.put("value", key + "=\"" + val + "\"");
            data.add(item);
        } else if ("add_activity_entry".equals(op)) {
            String actName = args.getString("name");
            String entry = args.getString("value");
            HashMap<String, Object> item = new HashMap<>();
            item.put("name", actName);
            item.put("value", entry);
            data.add(item);
        }

        File injectionFile = new File(injPath);
        if (!ctx.checkPermission(injPath, injectionFile.exists())) {
            return ToolResult.structured("manifest_edit", "DENIED", injPath, "Permission was denied.", null, true);
        }
        String beforeHash = injectionFile.isFile() ? DomainToolHelper.sha256(injectionFile) : null;
        if (injectionFile.isFile()) DomainToolHelper.backup(injPath);
        String serialized = GsonUtils.getGson().toJson(data);
        DomainToolHelper.atomicWrite(injectionFile, serialized, beforeHash);
        if (!serialized.equals(DomainToolHelper.readUtf8(injectionFile))) {
            return ToolResult.structured("manifest_edit", "VERIFICATION_FAILED", injPath,
                    "Manifest injection data did not match after read-back.", null, true);
        }
        LiveRegistry.notifyChanged("manifest", injPath);
        return verify(ctx, sc_id, args.optString("name", ""));
    }

    private ToolResult verify(ToolContext ctx, String sc_id, String marker) {
        yq gen = new yq(SketchApplication.getContext(), sc_id);
        String generated = gen.getFileSrc("AndroidManifest.xml", jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));
        if (generated.contains(marker)) {
            return new ToolResult("verified: '" + marker + "' present in generated manifest.", false);
        }
        return ToolResult.structured("manifest_edit", "VERIFICATION_FAILED", ProjectPaths.manifestFile(sc_id),
                "Metadata write completed, but the requested marker was not found in the generated AndroidManifest.xml. The operation is not considered successful.", null, true);
    }
}
