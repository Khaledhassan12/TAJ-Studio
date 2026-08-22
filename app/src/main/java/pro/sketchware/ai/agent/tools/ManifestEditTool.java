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

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String op = args.getString("op");
        String sc_id = args.optString("sc_id", ctx.sc_id);
        String injPath = ProjectPaths.manifestInjectionPath(sc_id);

        if ("add_permission".equals(op)) {
            String perm = args.getString("name");
            String path = new FilePathUtil().getPathPermission(sc_id);
            ArrayList<String> perms = new ArrayList<>();
            if (FileUtil.isExistFile(path)) {
                perms = GsonUtils.getGson().fromJson(FileUtil.readFile(path), new com.google.gson.reflect.TypeToken<ArrayList<String>>(){}.getType());
            }
            if (perms == null) perms = new ArrayList<>();
            if (!perms.contains(perm)) perms.add(perm);
            FileUtil.writeFile(path, GsonUtils.getGson().toJson(perms));
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

        DomainToolHelper.backup(injPath);
        FileUtil.writeFile(injPath, GsonUtils.getGson().toJson(data));
        LiveRegistry.notifyChanged("manifest", injPath);
        return verify(ctx, sc_id, args.optString("name", ""));
    }

    private ToolResult verify(ToolContext ctx, String sc_id, String marker) {
        yq gen = new yq(SketchApplication.getContext(), sc_id);
        String generated = gen.getFileSrc("AndroidManifest.xml", jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));
        if (generated.contains(marker)) {
            return new ToolResult("verified: '" + marker + "' present in generated manifest.", false);
        }
        return new ToolResult("Write successful, but verification failed in generated view.", false);
    }
}
