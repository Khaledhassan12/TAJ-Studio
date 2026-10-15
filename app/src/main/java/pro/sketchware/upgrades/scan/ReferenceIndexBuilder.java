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

    private static final int MAX_FILES = 12000;
    private static final int MAX_DEPTH = 32;
    private static final long MAX_HAYSTACK_BYTES = 8L * 1024L * 1024L;

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
        int[] scannedFiles = {0};
        Set<String> scannedDirectories = new HashSet<>();
        for (String javaDir : javaDirs) {
            scanDirectory(new File(javaDir), true, scannedDirectories, scannedFiles, 0);
        }
        for (String resDir : resDirs) {
            scanDirectory(new File(resDir), true, scannedDirectories, scannedFiles, 0);
        }

        // 2. Scan a bounded sample of generic content to catch designer-generated references.
        scanGenericContent(new File(projectPath), new HashSet<>(), new int[]{0}, new long[]{0L}, 0);
        
        // Whitelist basics (R2)
        referencedMipmaps.add("ic_launcher");
        referencedMipmaps.add("ic_launcher_round");
    }

    private void scanDirectory(File dir, boolean recursive, Set<String> visitedDirectories,
                               int[] visitedFiles, int depth) {
        if (Thread.currentThread().isInterrupted() || depth > MAX_DEPTH ||
                visitedFiles[0] >= MAX_FILES || dir == null) return;
        String canonical;
        try {
            canonical = dir.getCanonicalPath();
            if (!visitedDirectories.add(canonical)) return;
        } catch (Exception ignored) {
            return;
        }
        if (shouldSkipDirectory(dir.getName())) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (Thread.currentThread().isInterrupted() || visitedFiles[0] >= MAX_FILES) break;
            if (file.isDirectory()) {
                if (recursive) scanDirectory(file, true, visitedDirectories, visitedFiles, depth + 1);
                continue;
            }
            visitedFiles[0]++;
            String name = file.getName();
            if (name.endsWith(".java") || name.endsWith(".xml") || name.endsWith(".kt")) {
                processFile(file);
            }
        }
    }

    private void scanGenericContent(File dir, Set<String> visitedDirectories, int[] visitedFiles,
                                    long[] totalBytes, int depth) {
        if (Thread.currentThread().isInterrupted() || depth > MAX_DEPTH ||
                visitedFiles[0] >= MAX_FILES || totalBytes[0] >= MAX_HAYSTACK_BYTES || dir == null) return;
        String canonical;
        try {
            canonical = dir.getCanonicalPath();
            if (!visitedDirectories.add(canonical)) return;
        } catch (Exception ignored) {
            return;
        }
        if (shouldSkipDirectory(dir.getName())) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (Thread.currentThread().isInterrupted() || visitedFiles[0] >= MAX_FILES ||
                    totalBytes[0] >= MAX_HAYSTACK_BYTES) break;
            if (file.isDirectory()) {
                scanGenericContent(file, visitedDirectories, visitedFiles, totalBytes, depth + 1);
                continue;
            }
            visitedFiles[0]++;
            long length = file.length();
            if (length <= 0L || length >= 2L * 1024L * 1024L ||
                    totalBytes[0] + length > MAX_HAYSTACK_BYTES || isBinaryFile(file)) continue;
            String content = FileUtil.readFile(file.getAbsolutePath());
            if (content != null) {
                haystacks.add(content);
                totalBytes[0] += length;
            }
        }
    }

    private boolean shouldSkipDirectory(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.equals(".upgrade_backup") || lower.equals(".git") ||
                lower.equals(".gradle") || lower.equals("build") || lower.equals("outputs") ||
                lower.equals("generated") || lower.equals("node_modules") || lower.equals(".cxx") ||
                lower.equals("captures") || lower.equals("builds") || lower.equals("mysc");
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
