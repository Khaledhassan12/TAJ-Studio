package pro.sketchware.upgrades.scan;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.upgrades.model.Finding;
import pro.sketchware.utility.FileUtil;

/**
 * WHAT: UnusedValuesDetector - Analyzes XML values (strings, colors, styles) for unused entries.
 * (عربي) مكتشف القيم غير المستخدمة - يحلل قيم XML (النصوص، الألوان، الأنماط) للبحث عن المداخل غير المستخدمة.
 */
public class UnusedValuesDetector {

    private static final Pattern NAME_PATTERN = Pattern.compile("name=\"([a-zA-Z0-9_.]+)\"");

    public List<Finding> scan(String resPath, ReferenceIndexBuilder index) {
        List<Finding> findings = new ArrayList<>();
        File valuesDir = new File(resPath, "values");
        if (!valuesDir.exists()) return findings;

        File[] files = valuesDir.listFiles();
        if (files == null) return findings;

        for (File file : files) {
            if (file.getName().endsWith(".xml")) {
                checkFile(file, index, findings);
            }
        }
        return findings;
    }

    private void checkFile(File file, ReferenceIndexBuilder index, List<Finding> findings) {
        String content = FileUtil.readFile(file.getAbsolutePath());
        String fileName = file.getName();
        String type = fileName.replace(".xml", "");
        
        // Map common files to resource types
        String resType = switch (type) {
            case "strings" -> "string";
            case "colors" -> "color";
            case "styles" -> "style";
            default -> null;
        };

        if (resType == null) return;

        Matcher m = NAME_PATTERN.matcher(content);
        List<String> unusedNames = new ArrayList<>();
        while (m.find()) {
            String name = m.group(1);
            if (!index.isReferenced(resType, name)) {
                // Whitelist 'app_name'
                if (resType.equals("string") && name.equals("app_name")) continue;
                unusedNames.add(name);
            }
        }

        if (!unusedNames.isEmpty()) {
            findings.add(new Finding(
                "unused_value_" + type,
                Finding.Category.UNUSED_RESOURCE,
                "Unused " + type + " in " + fileName,
                "Found " + unusedNames.size() + " entries not referenced in code/layouts",
                List.of(file.getAbsolutePath()),
                false, // Manual removal recommended for values to avoid XML corruption
                "Open " + fileName + " and remove: " + String.join(", ", unusedNames)
            ));
        }
    }
}
