package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.utility.FileUtil;

public final class CreateJavaFileTool implements Tool {

    private static final String ACTIVITY_TEMPLATE = "package %s;\n\nimport android.app.Activity;\nimport android.os.Bundle;\n\npublic class %s extends Activity {\n\n    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n        super.onCreate(savedInstanceState);\n    }\n}\n";
    private static final String CLASS_TEMPLATE = "package %s;\n\npublic class %s {\n    \n}\n";
    private static final String KT_ACTIVITY_TEMPLATE = "package %s\n\nimport android.app.Activity\nimport android.os.Bundle\n\nclass %s : Activity() {\n\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n    }\n}\n";
    private static final String KT_CLASS_TEMPLATE = "package %s\n\nclass %s {\n    \n}\n";

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            
            JSONObject target = new JSONObject();
            target.put("type", "string");
            target.put("enum", new JSONArray(new String[]{"manager", "project"}));
            
            JSONObject name = new JSONObject();
            name.put("type", "string");
            name.put("description", "File name without extension (e.g. MyUtils).");

            JSONObject type = new JSONObject();
            type.put("type", "string");
            type.put("enum", new JSONArray(new String[]{"folder", "java_class", "java_activity", "kotlin_class", "kotlin_activity"}));

            JSONObject packagePath = new JSONObject();
            packagePath.put("type", "string");
            packagePath.put("description", "Dot-separated package or relative folder path (e.g. com.example.utils). Defaults to project package.");

            JSONObject content = new JSONObject();
            content.put("type", "string");
            content.put("description", "Full file content. If omitted, a template based on 'type' is used.");

            JSONObject overwriteConfirmed = new JSONObject();
            overwriteConfirmed.put("type", "boolean");
            overwriteConfirmed.put("description", "Must be true to overwrite an existing file.");

            props.put("target", target);
            props.put("name", name);
            props.put("type", type);
            props.put("packagePath", packagePath);
            props.put("content", content);
            props.put("overwriteConfirmed", overwriteConfirmed);
            
            params.put("properties", props);
            params.put("required", new JSONArray(new String[]{"target", "name", "type"}));
            
            return new ToolSpec("create_java_file", 
                "Creates a new folder, Java/Kotlin class or Activity.", 
                params);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String target = args.getString("target");
        String name = args.getString("name");
        String type = args.getString("type");
        String packagePath = args.optString("packagePath", "");
        String content = args.has("content") ? args.getString("content") : null;
        boolean overwriteConfirmed = args.optBoolean("overwriteConfirmed", false);

        String root;
        String finalPackageName;
        
        if ("manager".equals(target)) {
            root = JavaToolHelper.getManagerPath(ctx.sc_id);
            finalPackageName = packagePath.isEmpty() ? JavaToolHelper.getPackageName(ctx.sc_id) : packagePath;
        } else {
            root = JavaToolHelper.getProjectPath(ctx.sc_id);
            finalPackageName = packagePath.isEmpty() ? JavaToolHelper.getPackageName(ctx.sc_id) : packagePath;
            String subDir = finalPackageName.replace(".", File.separator);
            root = root + File.separator + subDir;
        }

        File targetFile;
        if ("folder".equals(type)) {
            targetFile = new File(root, name);
            if (targetFile.exists()) return new ToolResult("Folder already exists: " + targetFile.getAbsolutePath(), false);
            FileUtil.makeDir(targetFile.getAbsolutePath());
            return new ToolResult("Folder created: " + targetFile.getAbsolutePath(), false);
        }

        String ext = (type.startsWith("java")) ? ".java" : ".kt";
        targetFile = new File(root, name + ext);

        if (targetFile.exists() && !overwriteConfirmed) {
            return new ToolResult("File already exists. Set 'overwriteConfirmed': true to overwrite: " + targetFile.getAbsolutePath(), true);
        }

        String finalContent = content;
        if (finalContent == null) {
            switch (type) {
                case "java_activity": finalContent = String.format(ACTIVITY_TEMPLATE, finalPackageName, name); break;
                case "java_class": finalContent = String.format(CLASS_TEMPLATE, finalPackageName, name); break;
                case "kotlin_activity": finalContent = String.format(KT_ACTIVITY_TEMPLATE, finalPackageName, name); break;
                case "kotlin_class": finalContent = String.format(KT_CLASS_TEMPLATE, finalPackageName, name); break;
            }
        }

        if (targetFile.exists()) {
            if (!ctx.checkPermission(targetFile.getAbsolutePath(), true)) {
                return new ToolResult("Overwrite denied by user.", true);
            }
            JavaToolHelper.backup(targetFile.getAbsolutePath());
        } else {
            if (!ctx.checkPermission(targetFile.getAbsolutePath(), false)) {
                return new ToolResult("Create denied by user.", true);
            }
        }

        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) FileUtil.makeDir(parent.getAbsolutePath());
        
        FileUtil.writeFile(targetFile.getAbsolutePath(), finalContent);
        return new ToolResult("File created: " + targetFile.getAbsolutePath() + " (" + (finalContent != null ? finalContent.length() : 0) + " bytes)", false);
    }
}
