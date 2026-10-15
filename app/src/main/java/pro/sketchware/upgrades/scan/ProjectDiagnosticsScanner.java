package pro.sketchware.upgrades.scan;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

import pro.sketchware.upgrades.model.Finding;

/**
 * Conservative, read-only diagnostics for common project defects.
 * Findings from this scanner are advisory and are never auto-fixed.
 */
public final class ProjectDiagnosticsScanner {
    private static final int MAX_FILES = 12000;
    private static final long MAX_XML_BYTES = 4L * 1024L * 1024L;
    private static final Pattern LIFECYCLE_METHOD = Pattern.compile(
            "@Override\\s+protected\\s+void\\s+(onPause|onStop|onDestroy|onResume|onStart)\\s*\\([^)]*\\)\\s*\\{");
    private static final Pattern DEBUG_MARKER = Pattern.compile(
            "(?m)^\\s*(?:TODO|FIXME|XXX)\\b|\\bLog\\.(?:v|d|i)\\s*\\(");
    private static final Pattern DYNAMIC_GRADLE_VERSION = Pattern.compile(
            "(?m)(?:implementation|api|classpath)\\s*(?:\\(|\\s).*?(?:\\+|latest\\.(?:release|integration)|[0-9]+\\.\\+)");
    private static final Pattern TARGET_SDK = Pattern.compile(
            "(?m)\\btargetSdk(?:Version)?\\s*(?:=\\s*|\\s+)([0-9]+)");
    private static final Pattern COMPILE_SDK = Pattern.compile(
            "(?m)\\bcompileSdk(?:Version)?\\s*(?:=\\s*|\\s+)([0-9]+)");

    public List<Finding> scan(String projectPath) {
        if (projectPath == null || projectPath.trim().isEmpty()) return Collections.emptyList();
        File root = new File(projectPath);
        if (!root.isDirectory()) return Collections.emptyList();

        List<Finding> findings = new ArrayList<>();
        int[] visited = {0};
        scanDirectory(root, root, findings, visited);
        return findings;
    }

    private void scanDirectory(File root, File dir, List<Finding> out, int[] visited) {
        if (visited[0] >= MAX_FILES) return;
        String name = dir.getName();
        if (name.equals(".upgrade_backup") || name.equals(".git") ||
                name.equals("build") || name.equals(".gradle") || name.contains("mysc")) return;

        File[] children = dir.listFiles();
        if (children == null) return;
        for (File file : children) {
            if (visited[0] >= MAX_FILES) break;
            if (file.isDirectory()) {
                scanDirectory(root, file, out, visited);
                continue;
            }
            visited[0]++;
            String lower = file.getName().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".xml") || lower.endsWith(".svg")) inspectXml(file, out);
            if (lower.endsWith(".java")) inspectJava(file, out);
            if (lower.equals("build.gradle") || lower.equals("build.gradle.kts") ||
                    lower.equals("gradle.properties")) inspectGradle(file, out);
        }
    }

    private void inspectXml(File file, List<Finding> out) {
        if (file.length() == 0L) {
            add(out, "empty_xml", "Empty XML/SVG file",
                    "The file is zero bytes; it may break resource processing.", file);
            return;
        }
        if (file.length() > MAX_XML_BYTES) return;
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xml") && !lower.endsWith(".svg")) return;

        try (FileInputStream input = new FileInputStream(file)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(input);
            if (document.getDocumentElement() == null) {
                add(out, "missing_xml_root", "XML/SVG has no root element",
                        "The document parsed but contains no document element.", file);
            } else if (lower.endsWith(".svg") &&
                    !"svg".equalsIgnoreCase(document.getDocumentElement().getLocalName() == null
                            ? document.getDocumentElement().getNodeName()
                            : document.getDocumentElement().getLocalName())) {
                add(out, "invalid_svg_root", "SVG root element is not <svg>",
                        "Check whether this file is a valid SVG document.", file);
            }
        } catch (Exception e) {
            add(out, "malformed_xml", "Malformed XML/SVG",
                    "The parser could not read this document (" +
                            e.getClass().getSimpleName() + "). Review its syntax and namespaces.", file);
        }
    }

    private void inspectJava(File file, List<Finding> out) {
        if (file.length() > 2L * 1024L * 1024L) return;
        String source;
        try {
            byte[] bytes = new byte[(int) file.length()];
            try (FileInputStream input = new FileInputStream(file)) {
                int offset = 0, read;
                while (offset < bytes.length && (read = input.read(bytes, offset, bytes.length - offset)) > 0) offset += read;
                if (offset != bytes.length) return;
            }
            source = new String(bytes, "UTF-8");
        } catch (Exception ignored) {
            return;
        }

        Matcher lifecycle = LIFECYCLE_METHOD.matcher(source);
        while (lifecycle.find()) {
            String method = lifecycle.group(1);
            int end = findMethodEnd(source, lifecycle.end() - 1);
            if (end > lifecycle.end()) {
                String body = source.substring(lifecycle.end(), end);
                if (!body.contains("super." + method + "(")) {
                    add(out, "lifecycle_super_missing",
                            "Lifecycle callback may omit super call",
                            method + "() override does not visibly call super." + method +
                                    "(). Verify the Android superclass contract before changing it.", file);
                }
            }
        }

        if (source.contains("static ") &&
                (source.matches("(?s).*static\\s+(?:final\\s+)?(?:Activity|View|Context)\\s+\\w+.*"))) {
            add(out, "possible_static_context_leak", "Possible static Android reference leak",
                    "A static Activity/View/Context field can retain an Activity or view beyond its lifecycle. Confirm the field's actual ownership.", file);
        }

        Matcher debug = DEBUG_MARKER.matcher(source);
        if (debug.find()) {
            add(out, "debug_marker", "Debug logging or TODO marker",
                    "Review TODO/FIXME/XXX markers and verbose Log calls before release; these are review hints, not proof of a defect.", file);
        }
    }

    private int findMethodEnd(String source, int openBrace) {
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
            if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private void inspectGradle(File file, List<Finding> out) {
        if (file.length() > 1024L * 1024L) return;
        String source;
        try {
            byte[] bytes = new byte[(int) file.length()];
            try (FileInputStream input = new FileInputStream(file)) {
                int offset = 0, read;
                while (offset < bytes.length && (read = input.read(bytes, offset, bytes.length - offset)) > 0) offset += read;
                if (offset != bytes.length) return;
            }
            source = new String(bytes, "UTF-8");
        } catch (Exception ignored) { return; }

        if (DYNAMIC_GRADLE_VERSION.matcher(source).find()) {
            add(out, "dynamic_dependency_version", "Non-reproducible dependency version",
                    "A dynamic dependency version can resolve differently across builds. Pin a tested version after compatibility review.", file);
        }
        Matcher target = TARGET_SDK.matcher(source);
        if (target.find()) {
            try {
                if (Integer.parseInt(target.group(1)) <  targetSdkReviewThreshold()) {
                    add(out, "old_target_sdk", "Target SDK may be outdated",
                            "Configured targetSdk is " + target.group(1) +
                                    ". Check current Play policy and plugin compatibility before upgrading.", file);
                }
            } catch (NumberFormatException ignored) { }
        }
        Matcher compile = COMPILE_SDK.matcher(source);
        if (compile.find()) {
            try {
                if (Integer.parseInt(compile.group(1)) <  29) {
                    add(out, "old_compile_sdk", "Compile SDK is very old",
                            "Configured compileSdk is " + compile.group(1) +
                                    ". Review plugin and Android Gradle Plugin compatibility before upgrading.", file);
                }
            } catch (NumberFormatException ignored) { }
        }
    }

    private int targetSdkReviewThreshold() {
        // Conservative review threshold, not a claim about the latest platform or store policy.
        return 33;
    }

    private void add(List<Finding> out, String id, String title, String detail, File file) {
        out.add(new Finding(id + "_" + file.getAbsolutePath().hashCode(),
                Finding.Category.UPGRADE, title, detail,
                Collections.singletonList(file.getAbsolutePath()), false,
                "Review manually; no automatic edit is available"));
    }
}
