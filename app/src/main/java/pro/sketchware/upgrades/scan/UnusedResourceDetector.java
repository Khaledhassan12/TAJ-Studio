package pro.sketchware.upgrades.scan;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.upgrades.model.Finding;

/**
 * WHAT: UnusedResourceDetector - Detects resources that are not referenced in source code or layouts.
 * (عربي) مكتشف الموارد غير المستخدمة - يكتشف الموارد التي لم يتم الرجوع إليها في الكود المصدري أو التصميمات.
 */
public class UnusedResourceDetector {

    public List<Finding> scan(String resPath, ReferenceIndexBuilder index) {
        List<Finding> findings = new ArrayList<>();
        File root = new File(resPath);
        if (!root.exists()) return findings;

        File[] subDirs = root.listFiles();
        if (subDirs == null) return findings;

        for (File dir : subDirs) {
            String dirName = dir.getName();
            if (dirName.startsWith("drawable") || dirName.startsWith("mipmap") || 
                dirName.equals("raw") || dirName.equals("anim") || 
                dirName.equals("animator") || dirName.equals("menu") || dirName.equals("font")) {
                
                String type = dirName.split("-")[0];
                checkDirectory(dir, type, index, findings);
            }
        }
        return findings;
    }

    private void checkDirectory(File dir, String type, ReferenceIndexBuilder index, List<Finding> findings) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String fullName = file.getName();
            String nameWithoutExt = fullName.contains(".") ? fullName.substring(0, fullName.lastIndexOf('.')) : fullName;
            
            if (!index.isReferenced(type, nameWithoutExt)) {
                findings.add(new Finding(
                    "unused_" + type + "_" + nameWithoutExt,
                    Finding.Category.UNUSED_RESOURCE,
                    "Unused " + type + " resource",
                    "File: " + fullName + " is not referenced anywhere",
                    List.of(file.getAbsolutePath()),
                    true,
                    "Delete unused resource file"
                ));
            }
        }
    }
}
