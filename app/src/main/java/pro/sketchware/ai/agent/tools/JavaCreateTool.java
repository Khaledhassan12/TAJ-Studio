package pro.sketchware.ai.agent.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;

import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.live.LiveRegistry;

public final class JavaCreateTool implements Tool {
    @Override public ToolSpec spec() {
        try {
            JSONObject props = new JSONObject();
            props.put("target", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"manager", "project"})).put("description", "Use manager for new user-editable Java/Kotlin source files that must appear in ManageJavaActivity; use project only when explicitly modifying the generated project Java source root."));
            props.put("name", new JSONObject().put("type", "string").put("maxLength", 256));
            props.put("type", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"folder", "java_class", "java_activity", "kotlin_class", "kotlin_activity"})));
            props.put("packagePath", new JSONObject().put("type", "string").put("maxLength", 512).put("description", "Optional full package name. Must be the active project package or a subpackage beginning with it; omit to use the active project package automatically."));
            props.put("content", new JSONObject().put("type", "string").put("maxLength", 262144));
            JSONObject schema = new JSONObject(); schema.put("type", "object"); schema.put("properties", props);
            schema.put("required", new JSONArray().put("target").put("name").put("type"));
            return new ToolSpec("java_create", "Creates a Java/Kotlin class, Activity or folder without overwriting existing files. Use target=manager for user-editable files shown in ManageJavaActivity; target=project only for the generated project Java source root.", schema);
        } catch (Exception e) { throw new IllegalStateException("Could not construct java_create schema.", e); }
    }
    @Override public Domain domain() { return Domain.JAVA; }

    @Override public ToolResult run(JSONObject args, ToolContext ctx) {
        String path = null;
        File targetFile = null;
        String content = null;
        try {
            String target = args.getString("target");
            String name = args.getString("name");
            String type = args.getString("type");
            String packagePath = args.optString("packagePath", "").trim();
            if (!"manager".equals(target) && !"project".equals(target)) {
                return ToolResult.structured("java_create", "INVALID_ARGUMENTS", null, "Unsupported Java target.", null, true);
            }
            if (name.trim().isEmpty() || name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
                return ToolResult.structured("java_create", "INVALID_ARGUMENTS", null,
                        "Name must be a single file/class/folder name without path separators.", null, true);
            }
            if (!packagePath.isEmpty() && !packagePath.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")) {
                return ToolResult.structured("java_create", "INVALID_ARGUMENTS", null,
                        "packagePath must be a dot-separated Java/Kotlin package name.", null, true);
            }
            boolean folder = "folder".equals(type);
            if (!folder && !name.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
                return ToolResult.structured("java_create", "INVALID_ARGUMENTS", null,
                        "Class/Activity name must be a valid Java/Kotlin identifier.", null, true);
            }

            String root;
            String projectPackage = JavaToolHelper.getVerifiedPackageName(ctx.sc_id);
            if (projectPackage == null || projectPackage.trim().isEmpty()
                    || !projectPackage.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")) {
                return ToolResult.structured("java_create", "PACKAGE_UNVERIFIED", null,
                        "The active project's package name could not be verified. Inspect project metadata/manifest before creating this source file.", null, true);
            }
            String declaredPackage = projectPackage;
            if (!packagePath.isEmpty()) {
                // packagePath may name the project package itself or a subpackage.
                // Never allow the model to accidentally replace the active project's package.
                if (packagePath.equals(projectPackage) || packagePath.startsWith(projectPackage + ".")) {
                    declaredPackage = packagePath;
                } else {
                    return ToolResult.structured("java_create", "PACKAGE_MISMATCH", null,
                            "packagePath must equal the active project's package or be a subpackage of it: " + projectPackage, null, true);
                }
            }
            if ("project".equals(target)) {
                // Generated project source is laid out by the full Java/Kotlin package.
                root = ProjectPaths.projectJavaRoot(ctx.sc_id) + File.separator
                        + declaredPackage.replace(".", File.separator);
            } else {
                // ManageJavaActivity already starts at the user-managed Java/Kotlin root
                // and derives the package from its pkgName extra plus folders the user opens.
                // Putting the *full* package below this root hides a base-package file inside
                // artificial com/... folders and makes subpackages resolve to duplicated names.
                // Keep base-package files at the visible manager root; create only the suffix
                // folders for an explicitly requested subpackage.
                String packageSuffix = declaredPackage.equals(projectPackage)
                        ? ""
                        : declaredPackage.substring(projectPackage.length() + 1)
                                .replace(".", File.separator);
                root = ProjectPaths.javaManagerRoot(ctx.sc_id);
                if (!packageSuffix.isEmpty()) {
                    root = root + File.separator + packageSuffix;
                }
            }
            String suffix = folder ? "" : (type.startsWith("java_") ? ".java" : ".kt");
            targetFile = PathResolver.validateProjectPath(ctx.sc_id, new File(root, name + suffix));
            path = targetFile.getAbsolutePath();

            if (targetFile.exists()) return ToolResult.structured("java_create", "CONFLICT", path,
                    "Destination already exists; no content was overwritten.", null, true);

            if (folder) {
                if (!ctx.checkPermission(path, false)) return ToolResult.structured("java_create", "DENIED", path, "Folder creation was denied.", null, true);
                if (!targetFile.mkdirs() && !targetFile.isDirectory()) {
                    return ToolResult.structured("java_create", "ERROR", path, "Folder creation failed; inspect for partial parent directories.", null, true);
                }
                return ToolResult.structured("java_create", "SUCCESS", path, "Directory exists and was verified.", null, false);
            }

            content = args.optString("content", "");
            String pkg = declaredPackage;
            if (!content.isEmpty()) {
                // Enforce agreement between the file path and package declaration even
                // when the model supplies a complete custom source body.
                if (type.startsWith("java_")) {
                    content = content.replaceFirst("(?m)^\\s*package\\s+[A-Za-z_$][A-Za-z0-9_$.]*\\s*;\\s*\\r?\\n?", "");
                    content = "package " + pkg + ";\n\n" + content;
                } else if (type.startsWith("kotlin_")) {
                    content = content.replaceFirst("(?m)^\\s*package\\s+[A-Za-z_$][A-Za-z0-9_$.]*\\s*\\r?\\n?", "");
                    content = "package " + pkg + "\n\n" + content;
                }
            }
            if (content.isEmpty()) {
                String prefix = pkg.isEmpty() ? "" : "package " + pkg + ";\n\n";
                if ("java_activity".equals(type)) {
                    content = prefix + "import android.app.Activity;\nimport android.os.Bundle;\n\n"
                            + "public class " + name + " extends Activity {\n"
                            + "    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n"
                            + "        super.onCreate(savedInstanceState);\n    }\n}\n";
                } else if ("java_class".equals(type)) {
                    content = prefix + "public class " + name + " {\n}\n";
                } else if ("kotlin_activity".equals(type)) {
                    String kotlinPrefix = pkg.isEmpty() ? "" : "package " + pkg + "\n\n";
                    content = kotlinPrefix + "import android.app.Activity\nimport android.os.Bundle\n\n"
                            + "class " + name + " : Activity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n"
                            + "        super.onCreate(savedInstanceState)\n    }\n}\n";
                } else {
                    String kotlinPrefix = pkg.isEmpty() ? "" : "package " + pkg + "\n\n";
                    content = kotlinPrefix + "class " + name + " {\n}\n";
                }
            }

            if (!ctx.checkPermission(path, false)) return ToolResult.structured("java_create", "DENIED", path, "File creation was denied.", null, true);
            pro.sketchware.ai.agent.tools.DomainToolHelper.atomicWrite(targetFile, content, null);
            if (!targetFile.isFile() || !content.equals(pro.sketchware.ai.agent.tools.DomainToolHelper.readUtf8(targetFile))) {
                return ToolResult.structured("java_create", "VERIFICATION_FAILED", path,
                        "The new source file did not match the requested content after read-back.", null, true);
            }
            LiveRegistry.notifyChanged("manager".equals(target) ? "java_manager" : "project_java", path);
            return ToolResult.structured("java_create", "SUCCESS", path,
                    "Exact source read-back verified. Compilation and Android runtime behavior were not tested by this tool.", null, false);
        } catch (Exception e) {
            return ToolResult.structured("java_create", "ERROR", path,
                    e.getMessage() == null ? "Source creation failed; inspect the destination before retrying." : e.getMessage(), null, true);
        }
    }
}
