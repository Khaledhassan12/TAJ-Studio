package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.ai.live.LiveRegistry;

public final class JavaCreateTool implements Tool {

    private static final String ACTIVITY_TEMPLATE = "package %s;\n\nimport android.app.Activity;\nimport android.os.Bundle;\n\npublic class %s extends Activity {\n\n    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n        super.onCreate(savedInstanceState);\n    }\n}\n";
    private static final String CLASS_TEMPLATE = "package %s;\n\npublic class %s {\n    \n}\n";

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("target", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"manager", "project"})));
            props.put("name", new JSONObject().put("type", "string"));
            props.put("type", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"folder", "java_class", "java_activity", "kotlin_class", "kotlin_activity"})));
            props.put("packagePath", new JSONObject().put("type", "string"));
            props.put("content", new JSONObject().put("type", "string"));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("target").put("name").put("type"));
            return new ToolSpec("java_create", "Creates a Java/Kotlin file or folder.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.JAVA;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String target = args.getString("target");
        String name = args.getString("name");
        String type = args.getString("type");
        String packagePath = args.optString("packagePath", "");
        String sc_id = args.optString("sc_id", ctx.sc_id);

        String root = "manager".equals(target) ? ProjectPaths.javaManagerRoot(sc_id) : ProjectPaths.projectJavaRoot(sc_id);
        if (!packagePath.isEmpty()) {
            root += File.separator + packagePath.replace(".", File.separator);
        }
        
        File targetFile = new File(root, name + (type.equals("folder") ? "" : (type.contains("java") ? ".java" : ".kt")));
        String path = targetFile.getAbsolutePath();

        if (type.equals("folder")) {
            FileUtil.makeDir(path);
            return new ToolResult("verified: folder created @ " + path, false);
        }

        String content = args.optString("content", "");
        if (content.isEmpty()) {
            String pkg = packagePath.isEmpty() ? "com.my.app" : packagePath;
            if (type.equals("java_activity")) content = String.format(ACTIVITY_TEMPLATE, pkg, name);
            else content = String.format(CLASS_TEMPLATE, pkg, name);
        }

        if (!ctx.checkPermission(path, false)) return new ToolResult("Denied.", true);

        FileUtil.writeFile(path, content);
        if (FileUtil.isExistFile(path)) {
            LiveRegistry.notifyChanged("manager".equals(target) ? "java_manager" : "project_java", path);
            return new ToolResult("verified: file created @ " + path, false);
        }
        return new ToolResult("Failed to create file.", true);
    }
}
