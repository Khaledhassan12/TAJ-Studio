package pro.sketchware.upgrades.scan;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.upgrades.model.Finding;
import pro.sketchware.utility.FileUtil;

/**
 * WHAT: EmptyArtifactDetector - Finds empty directories, empty Java classes, and abandoned APKs.
 * (عربي) مكتشف المصنوعات الفارغة - يجد المجلدات الفارغة، كلاسات جافا الفارغة، وملفات APK المهجورة.
 */
public class EmptyArtifactDetector {

    public List<Finding> scan(String projectPath) {
        List<Finding> findings = new ArrayList<>();
        File root = new File(projectPath);
        if (!root.exists()) return findings;

        scanDir(root, findings);
        return findings;
    }

    private void scanDir(File dir, List<Finding> findings) {
        File[] files = dir.listFiles();
        if (files == null) return;

        String dirName = dir.getName();
        // G6: Skip backups and mysc
        if (dirName.equals(".upgrade_backup") || dirName.contains("mysc")) return;

        if (files.length == 0) {
            // G4: Whitelist standard folders
            if (!isStandardFolder(dirName)) {
                findings.add(new Finding(
                    "empty_dir_" + dirName,
                    Finding.Category.EMPTY_ARTIFACT,
                    "Empty Directory",
                    "Non-standard empty directory: " + dir.getAbsolutePath(),
                    List.of(dir.getAbsolutePath()),
                    true,
                    "Delete empty directory"
                ));
            }
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanDir(file, findings);
            } else {
                String name = file.getName();
                if (name.endsWith(".apk")) {
                    findings.add(new Finding(
                        "abandoned_apk_" + name,
                        Finding.Category.EMPTY_ARTIFACT,
                        "Abandoned APK File",
                        "Old build result in project folder: " + name,
                        List.of(file.getAbsolutePath()),
                        true,
                        "Delete old APK file"
                    ));
                } else if (name.endsWith(".java")) {
                    checkEmptyJava(file, findings);
                }
            }
        }
    }

    private boolean isStandardFolder(String name) {
        return name.equals("files") || name.equals("java") || name.equals("resource") || 
               name.equals("assets") || name.equals("values") || name.equals("layout") || 
               name.equals("xml") || name.equals("raw") || name.equals("anim") || 
               name.equals("animator") || name.equals("menu") || name.equals("font") ||
               name.startsWith("drawable") || name.startsWith("mipmap");
    }

    private void checkEmptyJava(File file, List<Finding> findings) {
        String content = FileUtil.readFile(file.getAbsolutePath());
        // Remove package, imports, comments, and whitespace
        String body = content.replaceAll("(?s)/\\*.*?\\*/", "")
                             .replaceAll("//.*", "")
                             .replaceAll("package\\s+.*?;", "")
                             .replaceAll("import\\s+.*?;", "")
                             .trim();
        
        // G2: If body is empty or doesn't look like a class (missing '{'), it's abandoned/empty
        if (body.isEmpty() || !body.contains("{")) {
            findings.add(new Finding(
                "abandoned_java_" + file.getName(),
                Finding.Category.EMPTY_ARTIFACT,
                "Empty/Abandoned Java file",
                "File " + file.getName() + " contains no valid class body or logic.",
                List.of(file.getAbsolutePath()),
                true, // G4: autoFixable=true
                "Delete empty or abandoned Java file"
            ));
            return;
        }

        if (body.contains("{") && body.endsWith("}")) {
            String inside = body.substring(body.indexOf("{") + 1, body.lastIndexOf("}")).trim();
            if (inside.isEmpty()) {
                findings.add(new Finding(
                    "empty_class_" + file.getName(),
                    Finding.Category.EMPTY_ARTIFACT,
                    "Empty Java Class",
                    "Class " + file.getName() + " has no logic inside the braces",
                    List.of(file.getAbsolutePath()),
                    true, // G4: autoFixable=true
                    "Delete empty class file"
                ));
            }
        }
    }
}
