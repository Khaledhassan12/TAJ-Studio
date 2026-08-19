package pro.sketchware.upgrades.scan;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.utility.FileUtil;

/**
 * WHAT: ReferenceIndexBuilder - Scans project source files to build an index of all referenced resources.
 * WHY: To accurately identify unused resources, we must know which names are actually mentioned in code/XML.
 * (عربي) باني فهرس المراجع - يفحص ملفات مصدر المشروع لبناء فهرس لجميع الموارد المُشار إليها.
 * لضمان تحديد الموارد غير المستخدمة بدقة، يجب أن نعرف الأسماء المذكورة فعلياً في الكود أو ملفات XML.
 */
public class ReferenceIndexBuilder {

    private static final Pattern JAVA_R_PATTERN = Pattern.compile("R\\.(drawable|mipmap|raw|anim|animator|menu|font|string|color|dimen|style)\\.([a-zA-Z0-9_]+)");
    private static final Pattern XML_REF_PATTERN = Pattern.compile("@(drawable|mipmap|raw|anim|animator|menu|font|string|color|dimen|style)/([a-zA-Z0-9_]+)");

    private final Set<String> referencedDrawables = new HashSet<>();
    private final Set<String> referencedMipmaps = new HashSet<>();
    private final Set<String> referencedStrings = new HashSet<>();
    private final Set<String> referencedColors = new HashSet<>();
    private final Set<String> referencedStyles = new HashSet<>();
    private final List<String> haystacks = new ArrayList<>();

    public void buildIndex(String projectPath, List<String> javaDirs, List<String> resDirs) {
        // Clear previous state
        referencedDrawables.clear();
        referencedMipmaps.clear();
        referencedStrings.clear();
        referencedColors.clear();
        referencedStyles.clear();
        haystacks.clear();

        // 1. Scan for specific patterns in Java & XML
        for (String javaDir : javaDirs) scanDirectory(new File(javaDir), true);
        for (String resDir : resDirs) scanDirectory(new File(resDir), true);

        // 2. Scan for generic content in ALL files < 2MB (G7: Capture Designer usages)
        scanGenericContent(new File(projectPath));
        
        // Whitelist basics (R2)
        referencedMipmaps.add("ic_launcher");
        referencedMipmaps.add("ic_launcher_round");
    }

    private void scanDirectory(File dir, boolean recursive) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory() && recursive) {
                scanDirectory(file, true);
            } else {
                String name = file.getName();
                if (name.endsWith(".java") || name.endsWith(".xml")) {
                    processFile(file);
                }
            }
        }
    }

    private void scanGenericContent(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String name = file.getName();
            // G6: Skip backups and mysc
            if (name.equals(".upgrade_backup") || name.contains("mysc")) continue;

            if (file.isDirectory()) {
                scanGenericContent(file);
            } else if (file.length() > 0 && file.length() < 2 * 1024 * 1024) { // < 2MB
                if (!isBinaryFile(file)) {
                    haystacks.add(FileUtil.readFile(file.getAbsolutePath()));
                }
            }
        }
    }

    private boolean isBinaryFile(File file) {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[8000];
            int read = fis.read(buffer);
            for (int i = 0; i < read; i++) {
                if (buffer[i] == 0) return true; // NUL byte check (R2)
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void processFile(File file) {
        String content = FileUtil.readFile(file.getAbsolutePath());
        
        Matcher javaMatcher = JAVA_R_PATTERN.matcher(content);
        while (javaMatcher.find()) {
            String type = javaMatcher.group(1);
            String name = javaMatcher.group(2);
            addToIndex(type, name);
        }

        Matcher xmlMatcher = XML_REF_PATTERN.matcher(content);
        while (xmlMatcher.find()) {
            String type = xmlMatcher.group(1);
            String name = xmlMatcher.group(2);
            addToIndex(type, name);
        }
    }

    private void addToIndex(String type, String name) {
        switch (type) {
            case "drawable": referencedDrawables.add(name); break;
            case "mipmap": referencedMipmaps.add(name); break;
            case "string": referencedStrings.add(name); break;
            case "color": referencedColors.add(name); break;
            case "style": referencedStyles.add(name); break;
        }
    }

    public boolean isReferenced(String type, String name) {
        // Whitelist (R2)
        if (name.startsWith("ic_launcher") || name.startsWith("default_")) return true;

        boolean specificRef = switch (type) {
            case "drawable" -> referencedDrawables.contains(name);
            case "mipmap" -> referencedMipmaps.contains(name);
            case "string" -> referencedStrings.contains(name);
            case "color" -> referencedColors.contains(name);
            case "style" -> referencedStyles.contains(name);
            default -> false;
        };

        if (specificRef) return true;

        // Substring check in haystacks (G7)
        for (String haystack : haystacks) {
            if (haystack.contains(name)) return true;
        }

        return false;
    }
}
