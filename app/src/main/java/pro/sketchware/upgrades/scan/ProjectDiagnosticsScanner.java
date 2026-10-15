package pro.sketchware.upgrades.scan;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.upgrades.model.Finding;

/**
 * Bounded, read-only diagnostics for common Android project defects.
 *
 * This scanner intentionally reports review hints rather than claiming to be a compiler or
 * semantic static analyzer. It never edits user project files.
 */
public final class ProjectDiagnosticsScanner {
    private static final int MAX_FILES = 12000;
    private static final int MAX_DEPTH = 32;
    private static final int MAX_FINDINGS = 2000;
    private static final long MAX_XML_BYTES = 4L * 1024L * 1024L;
    private static final long MAX_SOURCE_BYTES = 2L * 1024L * 1024L;
    private static final long MAX_GRADLE_BYTES = 1024L * 1024L;

    private static final Pattern LIFECYCLE_METHOD = Pattern.compile(
            "(?s)(?:@Override\\s*)?(?:@[\\w.]+(?:\\([^)]*\\))?\\s*)*" +
            "(?:public|protected)\\s+void\\s+" +
            "(onCreate|onStart|onResume|onPause|onStop|onDestroy|onSaveInstanceState|onDestroyView)" +
            "\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w., ]+\\s*)?\\{");
    private static final Pattern KOTLIN_LIFECYCLE_METHOD = Pattern.compile(
            "(?s)(?:override\\s+)?fun\\s+" +
            "(onCreate|onStart|onResume|onPause|onStop|onDestroy|onSaveInstanceState|onDestroyView)" +
            "\\s*\\([^)]*\\)\\s*(?::\\s*[\\w?.<>]+\\s*)?\\{");
    private static final Pattern DEBUG_MARKER = Pattern.compile(
            "(?m)^\\s*(?://|/\\*|\\*|#)?\\s*(?:TODO|FIXME|XXX)\\b|" +
            "\\bLog\\.(?:v|d|i)\\s*\\(|\\bSystem\\.out\\.print(?:ln)?\\s*\\(");
    private static final Pattern DYNAMIC_GRADLE_VERSION = Pattern.compile(
            "(?i)['\"][^'\"]+:[^'\"]+:(?:[^'\"]*\\+[^'\"]*|latest\\.release|latest\\.integration|\\[[^]]*,[^]]*\\])['\"]");
    private static final Pattern GRADLE_WRAPPER_VERSION = Pattern.compile(
            "gradle-([0-9]+(?:\\.[0-9]+){1,2})-(?:all|bin)\\.zip");
    private static final Pattern AGP_VERSION = Pattern.compile(
            "(?:com\\.android\\.tools\\.build:gradle:|id\\s*\\(\\s*['\"]com\\.android\\.application['\"]\\s*\\)\\s*version\\s*['\"])([0-9]+(?:\\.[0-9]+){1,2})");
    private static final Pattern TARGET_SDK = Pattern.compile(
            "(?m)\\btargetSdk(?:Version)?\\s*(?:=\\s*|\\s+)([0-9]+)");
    private static final Pattern COMPILE_SDK = Pattern.compile(
            "(?m)\\bcompileSdk(?:Version)?\\s*(?:=\\s*|\\s+)([0-9]+)");
    private static final Pattern STATIC_ANDROID_REFERENCE = Pattern.compile(
            "\\bstatic\\s+(?:(?:final|volatile|transient)\\s+)*(?:android\\.app\\.)?"+
            "(?:Activity|Application|View|Context|Fragment|Dialog|Drawable)\\s+\\w+");
    private static final Pattern KOTLIN_STATIC_ANDROID_REFERENCE = Pattern.compile(
            "(?s)companion\\s+object\\s*\\{[^}]*:\\s*(?:Activity|Application|View|Context|Fragment|Dialog)\\b");
    private static final Pattern BLOCKING_CALL = Pattern.compile(
            "\\bThread\\.sleep\\s*\\(|\\.execute\\s*\\(\\s*\\)\\s*\\.get\\s*\\(|\\brunBlocking\\s*\\{|\\bStrictMode\\.setThreadPolicy\\s*\\(");

    public List<Finding> scan(String projectPath) {
        if (projectPath == null || projectPath.trim().isEmpty()) return Collections.emptyList();
        File root;
        try {
            root = new File(projectPath).getCanonicalFile();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        if (!root.isDirectory()) return Collections.emptyList();

        List<Finding> findings = new ArrayList<>();
        Set<String> visitedDirectories = new HashSet<>();
        int[] visitedFiles = {0};
        scanDirectory(root, root, findings, visitedFiles, visitedDirectories, 0);
        return findings;
    }

    private void scanDirectory(File root, File dir, List<Finding> out, int[] visitedFiles,
                               Set<String> visitedDirectories, int depth) {
        if (Thread.currentThread().isInterrupted() || visitedFiles[0] >= MAX_FILES || out.size() >= MAX_FINDINGS || depth > MAX_DEPTH) return;

        String canonical;
        try {
            canonical = dir.getCanonicalPath();
            if (!canonical.equals(root.getPath()) && !canonical.startsWith(root.getPath() + File.separator)) return;
        } catch (Exception ignored) {
            return;
        }
        if (!visitedDirectories.add(canonical)) return;

        String name = dir.getName().toLowerCase(Locale.ROOT);
        if (shouldSkipDirectory(name)) return;

        File[] children = dir.listFiles();
        if (children == null) return;
        for (File file : children) {
            if (Thread.currentThread().isInterrupted() || visitedFiles[0] >= MAX_FILES || out.size() >= MAX_FINDINGS) break;
            if (file.isDirectory()) {
                scanDirectory(root, file, out, visitedFiles, visitedDirectories, depth + 1);
                continue;
            }
            visitedFiles[0]++;
            String lower = file.getName().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".xml") || lower.endsWith(".svg")) inspectXml(file, out);
            else if (lower.endsWith(".java") || lower.endsWith(".kt")) inspectSource(file, out);
            else if (lower.equals("build.gradle") || lower.equals("build.gradle.kts") ||
                    lower.equals("gradle.properties") || lower.equals("gradle-wrapper.properties")) inspectGradle(file, out);
        }
    }

    private boolean shouldSkipDirectory(String name) {
        return name.equals(".upgrade_backup") || name.equals(".git") || name.equals(".gradle") ||
                name.equals("build") || name.equals("builds") || name.equals(".idea") ||
                name.equals("node_modules") || name.equals(".cxx") || name.equals("captures") ||
                name.equals("outputs") || name.equals("generated");
    }

    private void inspectXml(File file, List<Finding> out) {
        String extension = file.getName().toLowerCase(Locale.ROOT);
        if (file.length() == 0L) {
            add(out, "empty_xml", "Empty XML/SVG file",
                    "The file is zero bytes and may break resource processing.", file);
            return;
        }
        if (file.length() > MAX_XML_BYTES) {
            add(out, "xml_too_large_review", "XML/SVG skipped due to size",
                    "The file exceeds the safe 4 MiB diagnostic limit; it was not parsed.", file);
            return;
        }

        try (FileInputStream input = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(input, null);
            int event;
            String rootName = null;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    rootName = parser.getName();
                    break;
                }
            }
            if (rootName == null) {
                add(out, "missing_xml_root", "XML/SVG has no root element",
                        "No document root element was found.", file);
            } else if (extension.endsWith(".svg") && !"svg".equalsIgnoreCase(rootName)) {
                add(out, "invalid_svg_root", "SVG root element is not <svg>",
                        "The root element is <" + rootName + ">. Verify that this file is intended to be SVG.", file);
            }
            // Continue to END_DOCUMENT so malformed trailing XML is also detected.
            while (parser.next() != XmlPullParser.END_DOCUMENT) { /* parse-only */ }
            if ("androidmanifest.xml".equalsIgnoreCase(file.getName())) inspectManifest(file, out);
        } catch (Exception e) {
            add(out, "malformed_xml", "Malformed XML/SVG",
                    "Android's XML parser rejected this file (" + e.getClass().getSimpleName() +
                            "). Review syntax and encoding; parser failures can also indicate unsupported XML features.", file);
        }
    }

    private void inspectManifest(File file, List<Finding> out) {
        String source = readText(file, MAX_XML_BYTES);
        if (source == null) return;
        String withoutComments = source.replaceAll("(?s)<!--.*?-->", " ");
        if (Pattern.compile("android:debuggable\\s*=\\s*['\"]true['\"]", Pattern.CASE_INSENSITIVE).matcher(withoutComments).find()) {
            add(out, "manifest_debuggable", "Debuggable app manifest",
                    "android:debuggable is explicitly true. Remove it from production variants unless there is a reviewed reason.", file);
        }
        if (Pattern.compile("android:usesCleartextTraffic\\s*=\\s*['\"]true['\"]", Pattern.CASE_INSENSITIVE).matcher(withoutComments).find()) {
            add(out, "manifest_cleartext_traffic", "Cleartext network traffic is enabled",
                    "Review whether unencrypted HTTP traffic is required. Prefer HTTPS and a narrow network security configuration.", file);
        }
        if (Pattern.compile("android:allowBackup\\s*=\\s*['\"]true['\"]", Pattern.CASE_INSENSITIVE).matcher(withoutComments).find()) {
            add(out, "manifest_backup_enabled", "App data backup is enabled",
                    "Confirm that backed-up data is appropriate and sensitive data is excluded or protected. Backup may be intentional.", file);
        }
    }

    private void inspectSource(File file, List<Finding> out) {
        if (file.length() > MAX_SOURCE_BYTES) return;
        String source = readText(file, MAX_SOURCE_BYTES);
        if (source == null) return;
        boolean kotlin = file.getName().toLowerCase(Locale.ROOT).endsWith(".kt");

        Matcher lifecycle = (kotlin ? KOTLIN_LIFECYCLE_METHOD : LIFECYCLE_METHOD).matcher(source);
        while (lifecycle.find() && out.size() < MAX_FINDINGS) {
            String method = lifecycle.group(1);
            int openBrace = lifecycle.end() - 1;
            int end = findMatchingBrace(source, openBrace);
            if (end <= openBrace) continue;
            String body = source.substring(openBrace + 1, end);
            if (!body.contains("super." + method + "(")) {
                add(out, "lifecycle_super_missing",
                        "Lifecycle callback may omit super call",
                        method + "() does not visibly call super." + method +
                                "(). This is a heuristic: inspect the actual superclass contract before changing it.", file);
            }
        }

        if ((!kotlin && STATIC_ANDROID_REFERENCE.matcher(source).find()) ||
                (kotlin && KOTLIN_STATIC_ANDROID_REFERENCE.matcher(source).find())) {
            add(out, "possible_static_context_leak", "Possible static Android reference leak",
                    "A static or companion-object reference to an Activity/View/Context-like object can retain UI state. Confirm ownership and lifetime; this is not proof of a leak.", file);
        }

        if (DEBUG_MARKER.matcher(source).find()) {
            add(out, "debug_marker", "Debug logging or TODO marker",
                    "Review TODO/FIXME/XXX markers, verbose Log calls, and System.out output before release. These are review hints, not proof of a defect.", file);
        }

        if (BLOCKING_CALL.matcher(source).find()) {
            add(out, "possible_blocking_operation", "Potentially blocking operation",
                    "Thread.sleep, blocking Future.get, runBlocking, or StrictMode policy changes were found. Verify they cannot block the main thread or hide responsiveness issues.", file);
        }

        String compact = source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
        if (compact.matches("(?s).*\\b(?:onClick|onClickListener|onCreate|onResume|onStart)\\b.{0,240}\\b(?:readFile|writeFile|FileInputStream|FileOutputStream|\\.execute\\s*\\(|HttpURLConnection|\\.connect\\s*\\().*")) {
            add(out, "possible_main_thread_io", "Possible I/O in UI callback",
                    "A UI/lifecycle callback appears near file or network I/O. Regex analysis cannot prove execution thread; move expensive work off the main thread where appropriate.", file);
        }
    }

    private int findMatchingBrace(String source, int openBrace) {
        int depth = 0;
        boolean string = false, character = false, lineComment = false, blockComment = false, escape = false;
        for (int i = openBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            char n = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (lineComment) { if (c == '\n') lineComment = false; continue; }
            if (blockComment) { if (c == '*' && n == '/') { blockComment = false; i++; } continue; }
            if (string || character) {
                if (escape) { escape = false; continue; }
                if (c == '\\') { escape = true; continue; }
                if (string && c == '"') string = false;
                if (character && c == '\'') character = false;
                continue;
            }
            if (c == '/' && n == '/') { lineComment = true; i++; continue; }
            if (c == '/' && n == '*') { blockComment = true; i++; continue; }
            if (c == '"') { string = true; continue; }
            if (c == '\'') { character = true; continue; }
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private void inspectGradle(File file, List<Finding> out) {
        if (file.length() > MAX_GRADLE_BYTES) return;
        String source = readText(file, MAX_GRADLE_BYTES);
        if (source == null) return;

        String withoutComments = source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)^\\s*//.*$", " ");
        String fileName = file.getName().toLowerCase(Locale.ROOT);
        if (fileName.equals("gradle-wrapper.properties")) {
            Matcher wrapper = GRADLE_WRAPPER_VERSION.matcher(withoutComments);
            if (wrapper.find()) {
                try {
                    int major = Integer.parseInt(wrapper.group(1).split("\\.")[0]);
                    if (major < 8) {
                        add(out, "old_gradle_wrapper", "Gradle wrapper needs compatibility review",
                                "The wrapper declares Gradle " + wrapper.group(1) +
                                        ". Review Gradle/AGP/JDK compatibility before updating; no build version was changed.", file);
                    }
                } catch (NumberFormatException ignored) { }
            } else {
                add(out, "gradle_wrapper_unrecognized", "Gradle wrapper version could not be identified",
                        "Review distributionUrl and ensure the wrapper points to a trusted Gradle distribution.", file);
            }
        }
        Matcher agp = AGP_VERSION.matcher(withoutComments);
        if (agp.find()) {
            try {
                int major = Integer.parseInt(agp.group(1).split("\\.")[0]);
                if (major < 8) {
                    add(out, "old_android_gradle_plugin", "Android Gradle Plugin needs compatibility review",
                            "The build declares Android Gradle Plugin " + agp.group(1) +
                                    ". Check required JDK and Gradle versions before upgrading.", file);
                }
            } catch (NumberFormatException ignored) { }
        }
        if (DYNAMIC_GRADLE_VERSION.matcher(withoutComments).find()) {
            add(out, "dynamic_dependency_version", "Non-reproducible dependency version",
                    "A dynamic or ranged dependency version can resolve differently between builds. Pin a tested version after compatibility review.", file);
        }
        Matcher target = TARGET_SDK.matcher(withoutComments);
        if (target.find()) {
            try {
                int value = Integer.parseInt(target.group(1));
                if (value < 35) {
                    add(out, "old_target_sdk", "Target SDK needs compatibility review",
                            "Configured targetSdk is " + value +
                                    ". Review current Android platform/store requirements and library compatibility before changing it; no version was changed automatically.", file);
                }
            } catch (NumberFormatException ignored) { }
        }
        Matcher compile = COMPILE_SDK.matcher(withoutComments);
        if (compile.find()) {
            try {
                int value = Integer.parseInt(compile.group(1));
                if (value < 35) {
                    add(out, "old_compile_sdk", "Compile SDK needs compatibility review",
                            "Configured compileSdk is " + value +
                                    ". Check Android Gradle Plugin, JDK, and dependency compatibility before upgrading; no version was changed automatically.", file);
                }
            } catch (NumberFormatException ignored) { }
        }
        if (file.getName().equalsIgnoreCase("gradle.properties")) {
            if (withoutComments.matches("(?s).*org\\.gradle\\.jvmargs\\s*=\\s*[^\\n]*-Xmx[0-9]+[kmgKMG].*")) {
                // Memory settings are environment-dependent, so only surface clearly tiny heaps.
                Matcher heap = Pattern.compile("-Xmx([0-9]+)([kmgKMG])").matcher(withoutComments);
                if (heap.find()) {
                    try {
                        long amount = Long.parseLong(heap.group(1));
                        String unit = heap.group(2).toLowerCase(Locale.ROOT);
                        long mb = "g".equals(unit) ? amount * 1024L : ("k".equals(unit) ? amount / 1024L : amount);
                        if (mb < 1024L) {
                            add(out, "small_gradle_heap", "Gradle heap may be undersized",
                                    "Gradle JVM heap is configured below 1 GiB. Adjust only after considering device/CI memory limits and project size.", file);
                        }
                    } catch (NumberFormatException ignored) { }
                }
            }
        }
    }

    private String readText(File file, long maxBytes) {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
             BufferedReader buffered = new BufferedReader(reader)) {
            StringBuilder text = new StringBuilder((int) Math.min(file.length(), 65536L));
            char[] buffer = new char[8192];
            int count;
            while ((count = buffered.read(buffer)) != -1) {
                text.append(buffer, 0, count);
                if (text.length() > maxBytes) return null;
            }
            return text.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void add(List<Finding> out, String id, String title, String detail, File file) {
        if (out.size() >= MAX_FINDINGS) return;
        out.add(new Finding(id + "_" + file.getAbsolutePath().hashCode(),
                Finding.Category.UPGRADE, title, detail,
                Collections.singletonList(file.getAbsolutePath()), false,
                "Review manually; this diagnostic never edits project files"));
    }
}
