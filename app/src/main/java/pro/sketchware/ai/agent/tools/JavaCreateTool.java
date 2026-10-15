package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.ai.live.LiveRegistry;

public final class JavaCreateTool implements Tool {

    private static final String ACTIVITY_TEMPLATE = "package %s;\n\nimport android.app.Activity;\nimport android.os.Bundle;\n\npublic class %s extends Activity {\n\n    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n        super.onCreate(savedInstanceState);\n    }\n}\n";
    private static final String CLASS_TEMPLATE = "package %s;\n\npublic class %s {\n}\n";
    private static final String KOTLIN_ACTIVITY_TEMPLATE = "package %s\n\nimport android.app.Activity\nimport android.os.Bundle\n\nclass %s : Activity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n    }\n}\n";
    private static final String KOTLIN_CLASS_TEMPLATE = "package %s\n\nclass %s\n";

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
            props;
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
        String sc_id = ctx.sc_id;

        if (!("manager".equals(target) || "project".equals(target))) {
            return new ToolResult("Invalid Java target.", true);
        }
        if (!("folder".equals(type) || "java_class".equals(type) || "java_activity".equals(type)
                || "kotlin_class".equals(type) || "kotlin_activity".equals(type))) {
            return new ToolResult("Unsupported Java/Kotlin creation type.", true);
        }
        if (name.isEmpty() || name.contains("/") || name.contains("\\")
                || name.equals(".") || name.equals("..")) {
            return new ToolResult("Name must be a single file or folder name.", true);
        }
        if (!"folder".equals(type) && !name.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            return new ToolResult("Class names must be valid Java/Kotlin identifiers.", true);
        }
        if (!packagePath.isEmpty() && !packagePath.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\\\.[A-Za-z_$][A-Za-z0-9_$]*)*")) {
            return new ToolResult("packagePath must be a dot-separated package name, not a filesystem path.", true);
        }

        String root = "manager".equals(target) ? ProjectPaths.javaManagerRoot(sc_id) : ProjectPaths.projectJavaRoot(sc_id);
        if (!packagePath.isEmpty()) root += File.separator + packagePath.replace(".", File.separator);

        File targetFile = PathResolver.validateProjectPath(sc_id,
                new File(root, name + ("folder".equals(type) ? "" : (type.startsWith("java_") ? ".java" : ".kt"))));
        String path = targetFile.getAbsolutePath();

        if ("folder".equals(type)) {
            if (targetFile.exists()) return new ToolResult("Folder already exists: " + path, true);
            if (!ctx.checkPermission(path, false)) return new ToolResult("Denied.", true);
            FileUtil.makeDir(path);
            return new ToolResult(targetFile.isDirectory() ? "verified: folder created @ " + path : "Failed to create folder.", !targetFile.isDirectory());
        }

        if (targetFile.exists()) {
            return new ToolResult("File already exists: " + path + ". Use java_edit to modify it without replacing it accidentally.", true);
        }

        String content = args.optString("content", "");
        if (content.isEmpty()) {
            String pkg = packagePath.isEmpty() ? "com.my.app" : packagePath;
            if ("java_activity".equals(type)) content = String.format(ACTIVITY_TEMPLATE, pkg, name);
            else if ("kotlin_activity".equals(type)) content = String.format(KOTLIN_ACTIVITY_TEMPLATE, pkg, name);
            else if ("kotlin_class".equals(type)) content = String.format(KOTLIN_CLASS_TEMPLATE, pkg, name);
            else content = String.format(CLASS_TEMPLATE, pkg, name);
        }

        if (!ctx.checkPermission(path, false)) return new ToolResult("Denied.", true);
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) FileUtil.makeDir(parent.getAbsolutePath());

        FileUtil.writeFile(path, content);
        if (targetFile.isFile() && content.equals(FileUtil.readFile(path))) {
            LiveRegistry.notifyChanged("manager".equals(target) ? "java_manager" : "project_java", path);
            return new ToolResult("verified: file created @ " + path, false);
        }
        return new ToolResult("File creation verification failed: " + path, true);
    }
}
