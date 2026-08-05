package pro.sketchware.upgrades.scan;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.upgrades.model.Finding;
import pro.sketchware.utility.FileUtil;

/**
 * WHAT: ImportCleaner - Detects unused imports in Java files.
 * (عربي) منظف الاستيرادات - يكتشف الاستيرادات غير المستخدمة في ملفات جافا.
 */
public class ImportCleaner {

    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+([a-zA-Z0-9_.]+);");

    public List<Finding> scan(String javaPath) {
        List<Finding> findings = new ArrayList<>();
        File root = new File(javaPath);
        if (!root.exists()) return findings;

        scanDir(root, findings);
        return findings;
    }

    private void scanDir(File dir, List<Finding> findings) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDir(file, findings);
            } else if (file.getName().endsWith(".java")) {
                Finding f = checkFile(file);
                if (f != null) findings.add(f);
            }
        }
    }

    private Finding checkFile(File file) {
        String content = FileUtil.readFile(file.getAbsolutePath());
        String[] lines = content.split("\n");
        List<String> unusedImports = new ArrayList<>();
        
        // Basic logic: if the simple name of the imported class doesn't appear in the rest of the file
        for (String line : lines) {
            Matcher m = IMPORT_PATTERN.matcher(line.trim());
            if (m.find()) {
                String fullImport = m.group(1);
                if (fullImport.endsWith("*")) continue;
                
                String simpleName = fullImport.substring(fullImport.lastIndexOf('.') + 1);
                // Search for simpleName in content excluding the import line itself
                // (Very simplified check for efficiency)
                Pattern usagePattern = Pattern.compile("\\b" + simpleName + "\\b");
                Matcher usageMatcher = usagePattern.matcher(content);
                int count = 0;
                while (usageMatcher.find()) count++;
                
                if (count <= 1) { // Only found in the import statement
                    unusedImports.add(line.trim());
                }
            }
        }

        if (!unusedImports.isEmpty()) {
            return new Finding(
                "unused_import_" + file.getName(),
                Finding.Category.UNUSED_IMPORT,
                "Unused Imports in " + file.getName(),
                "Found " + unusedImports.size() + " unused import(s)",
                List.of(file.getAbsolutePath()),
                true,
                "Remove unused import statements"
            );
        }
        return null;
    }
}
