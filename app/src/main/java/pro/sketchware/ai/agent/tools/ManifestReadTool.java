package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.ai.agent.FormatRegistry;
import pro.sketchware.utility.FileUtil;
import a.a.a.yq;
import a.a.a.jC;
import pro.sketchware.SketchApplication;

public final class ManifestReadTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            params.put("properties", new JSONObject().put("sc_id", new JSONObject().put("type", "string")));
            return new ToolSpec("manifest_read", "Reads the generated manifest and injection model.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.MANIFEST;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String sc_id = args.optString("sc_id", ctx.sc_id);
        
        yq gen = new yq(SketchApplication.getContext(), sc_id);
        String generated = gen.getFileSrc("AndroidManifest.xml", jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));
        
        JSONObject res = new JSONObject();
        res.put("generated", generated);
        
        String injPath = ProjectPaths.manifestInjectionPath(sc_id);
        if (FileUtil.isExistFile(injPath)) {
            res.put("injection", new org.json.JSONArray(FileUtil.readFile(injPath)));
        }
        
        return new ToolResult(res.toString(), false);
    }
}
