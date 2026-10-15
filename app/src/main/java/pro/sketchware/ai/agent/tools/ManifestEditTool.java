package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.HashMap;
import pro.sketchware.ai.agent.Tool;
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
            params.put("properties", props);
            params.put("required", new JSONArray().put("op"));
            return new ToolSpec("manifest_edit", "Edits the manifest injection model.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.MANIFEST;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String op = args.getString("op");
        String sc_id = ctx.sc_id;
        String injPath = ProjectPaths.manifestInjectionPath(sc_id);

        if ("add_permission".equals(op)) {
            String perm = args.optString("name", "").trim();
            if (!perm.matches("[A-Za-z_$][A-Za-z0-9_$.]*")) {
                return new ToolResult("A valid permission name is required.", true);
            }
            String path = new FilePathUtil().getPathPermission(sc_id);
            ArrayList<String> perms = new ArrayList<>();
            if (FileUtil.isExistFile(path)) {
                perms = GsonUtils.getGson().fromJson(FileUtil.readFile(path), new com.google.gson.reflect.TypeToken<ArrayList<String>>(){}.getType());
            }
            if (perms == null) perms = new ArrayList<>();
            if (!perms.contains(perm)) perms.add(perm);
            if (!ctx.checkPermission(path, FileUtil.isExistFile(path))) return new ToolResult("Denied.", true);
            String nextPermissions = GsonUtils.getGson().toJson(perms);
            DomainToolHelper.backup(path);
            FileUtil.writeFile(path, nextPermissions);
            if (!nextPermissions.equals(FileUtil.readFile(path))) {
                DomainToolHelper.restore(path);
                return new ToolResult("Permission list persistence verification failed.", true);
            }
            ToolResult permissionResult = verify(ctx, sc_id, perm);
            if (permissionResult.error) DomainToolHelper.restore(path);
            else LiveRegistry.notifyChanged("manifest", path);
            return permissionResult;
        }

        ArrayList<HashMap<String, Object>> data = new ArrayList<>();
        if (FileUtil.isExistFile(injPath)) {
            data = GsonUtils.getGson().fromJson(FileUtil.readFile(injPath), Helper.TYPE_MAP_LIST);
        }
        if (data == null) data = new ArrayList<>();

        if ("set_application_attribute".equals(op)) {
            String key = args.optString("name", "").trim();
            String val = args.optString("value", "");
            if (!key.matches("[A-Za-z_][A-Za-z0-9_.:-]*")) {
                return new ToolResult("A valid Android application attribute name is required.", true);
            }
            String attribute = key + "=\"" + escapeXmlAttribute(val) + "\"";
            boolean replaced = false;
            for (HashMap<String, Object> item : data) {
                if ("_application_attrs".equals(item.get("name"))) {
                    String existing = String.valueOf(item.get("value"));
                    String[] attributes = existing.split("\\s+(?=[A-Za-z_][A-Za-z0-9_.:-]*=)");
                    StringBuilder merged = new StringBuilder();
                    for (String candidate : attributes) {
                        if (candidate.startsWith(key + "=")) continue;
                        if (!candidate.trim().isEmpty()) {
                            if (merged.length() > 0) merged.append(' ');
                            merged.append(candidate.trim());
                        }
                    }
                    if (merged.length() > 0) merged.append(' ');
                    merged.append(attribute);
                    item.put("value", merged.toString());
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                HashMap<String, Object> item = new HashMap<>();
                item.put("name", "_application_attrs");
                item.put("value", attribute);
                data.add(item);
            }
        } else if ("add_activity_entry".equals(op)) {
            String actName = args.optString("name", "").trim();
            String entry = args.optString("value", "");
            if (actName.isEmpty() || entry.trim().isEmpty()) {
                return new ToolResult("Activity name and manifest entry are required.", true);
            }
            boolean replaced = false;
            for (HashMap<String, Object> item : data) {
                if (actName.equals(item.get("name"))) {
                    item.put("value", entry);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                HashMap<String, Object> item = new HashMap<>();
                item.put("name", actName);
                item.put("value", entry);
                data.add(item);
            }
        }

        if (!("set_application_attribute".equals(op) || "add_activity_entry".equals(op))) {
            return new ToolResult("Unsupported manifest operation: " + op, true);
        }
        if (!ctx.checkPermission(injPath, FileUtil.isExistFile(injPath))) return new ToolResult("Denied.", true);
        String nextData = GsonUtils.getGson().toJson(data);
        DomainToolHelper.backup(injPath);
        FileUtil.writeFile(injPath, nextData);
        if (!nextData.equals(FileUtil.readFile(injPath))) {
            DomainToolHelper.restore(injPath);
            return new ToolResult("Manifest injection persistence verification failed.", true);
        }
        ToolResult manifestResult = verify(ctx, sc_id, args.optString("name", ""));
        if (manifestResult.error) DomainToolHelper.restore(injPath);
        else LiveRegistry.notifyChanged("manifest", injPath);
        return manifestResult;
    }

    private static String escapeXmlAttribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private ToolResult verify(ToolContext ctx, String sc_id, String marker) {
        if (marker == null || marker.trim().isEmpty()) {
            return new ToolResult("Manifest verification requires a non-empty marker.", true);
        }
        yq gen = new yq(SketchApplication.getContext(), sc_id);
        String generated = gen.getFileSrc("AndroidManifest.xml", jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));
        if (generated != null && generated.contains(marker)) {
            return new ToolResult("verified: '" + marker + "' present in generated manifest.", false);
        }
        return new ToolResult("Manifest update was not verified in the generated manifest; the operation was rolled back.", true);
    }
}
