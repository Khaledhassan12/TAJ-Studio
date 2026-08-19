package pro.sketchware.marketplace.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.marketplace.catalog.LibraryCatalog;
import pro.sketchware.marketplace.models.MarketplaceLibrary;

/**
 * مستخرج الإحداثيات الذكي - يدعم صيغاً متعددة وحل الإصدارات من الكتالوج.
 * Smart Coordinate Extractor - Supports multiple formats and catalog version resolution.
 */
public class CoordinateExtractor {

    // نموذج نتيجة الاستخراج
    public static class Extracted {
        public final String group, artifact;
        public String version;          // null إن فُقد/متغيّر
        public final String raw;        // النص الأصلي المطابق (للعرض)
        public boolean resolvedFromCatalog;

        public Extracted(String group, String artifact, String version, String raw) {
            this.group = group;
            this.artifact = artifact;
            this.version = version;
            this.raw = raw;
        }

        public String coordinate() {
            return group + ":" + artifact + (version != null ? ":" + version : "");
        }

        public String groupArtifact() {
            return group + ":" + artifact;
        }
    }

    // WHAT: Multi-format Regex patterns.
    // HOW: Specialized patterns for PURL, JitPack, GitHub, Gradle/Maven, and Repo paths.
    
    // محوّل 1 — PURL: pkg:maven/<group-with-dots-or-slashes>/<artifact>@<version>
    private static final Pattern PURL = Pattern.compile(
        "pkg:maven/([\\w.-]+(?:/[\\w.-]+)*)/([\\w.-]+)@([\\w.\\-+]+)");
    
    // محوّل 2 — JitPack hash: jitpack.io/#user/repo/version
    private static final Pattern JITPACK = Pattern.compile(
        "jitpack\\.io/?#?([\\w.-]+)/([\\w.-]+)/([\\w.\\-+]+)");
    
    // محوّل 3 — GitHub repo: github.com/user/repo
    private static final Pattern GITHUB = Pattern.compile(
        "github\\.com/([\\w.-]+)/([\\w.-]+?)(?:\\.git)?(?:/|$|[?#\\s])");
    
    // محوّل 4 — Maven/Gradle coordinate، يدعم إصداراً متغيّراً
    private static final Pattern COORD = Pattern.compile(
        "([\\w][\\w.-]*:[\\w][\\w.-]*)(?::([\\w.\\-+${}A-Z_]*))?");
    
    // محوّل 5 — مسار مستودع
    private static final Pattern REPO_PATH = Pattern.compile(
        "(?:maven2|/repo(?:sitories)?|/release)/([\\w.-]+(?:/[\\w.-]+)+)/(\\d[\\w.\\-+]+)(?:/|[?#]|$)");

    /**
     * extract: يقوم بمسح النص واستخراج كافة الإحداثيات المحتملة وتوحيدها.
     * WHAT: Multi-format smart coordinate extraction.
     * HOW: Running multiple regex layers and unifying the results.
     */
    public static List<Extracted> extract(String input) {
        List<Extracted> out = new ArrayList<>();
        if (input == null || input.isEmpty()) return out;

        // 1) PURL
        Matcher m = PURL.matcher(input);
        while (m.find()) {
            out.add(new Extracted(m.group(1).replace('/', '.'), m.group(2), m.group(3), m.group(0)));
        }

        // 2) JitPack
        m = JITPACK.matcher(input);
        while (m.find()) {
            out.add(new Extracted("com.github." + m.group(1), m.group(2), m.group(3), m.group(0)));
        }

        // 3) GitHub Repo
        m = GITHUB.matcher(input);
        while (m.find()) {
            String g = m.group(1), a = m.group(2);
            if (!isNoiseRepo(a)) {
                out.add(new Extracted("com.github." + g, a, null, m.group(0)));
            }
        }

        // 4) Coordinate
        m = COORD.matcher(input);
        while (m.find()) {
            String[] ga = m.group(1).split(":", 2);
            String g = ga[0], a = ga[1];
            if (!(g.contains(".") || g.contains("-"))) continue; // Noise filter
            if (isKeyword(g)) continue;
            String v = m.group(2);
            out.add(new Extracted(g, a, v, m.group(0)));
        }

        // 5) Repo Path
        m = REPO_PATH.matcher(input);
        while (m.find()) {
            String[] parts = m.group(1).split("/");
            if (parts.length >= 2) {
                String a = parts[parts.length - 1];
                StringBuilder gBuilder = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (i > 0) gBuilder.append(".");
                    gBuilder.append(parts[i]);
                }
                String g = gBuilder.toString();
                if (g.contains(".") || g.contains("-")) {
                    out.add(new Extracted(g, a, m.group(2), m.group(0)));
                }
            }
        }

        return dedup(out);
    }

    /**
     * resolveVersionsFromCatalog: يحل الإصدارات المفقودة/المتغيرة بالاستعانة بالكتالوج.
     * WHAT: Catalog version resolver.
     * HOW: Mapping group:artifact to stable versions from curated libraries.
     */
    public static void resolveVersionsFromCatalog(List<Extracted> list) {
        Map<String, String> catalog = new HashMap<>();
        for (MarketplaceLibrary lib : LibraryCatalog.getCuratedLibraries()) {
            String c = lib.getCoordinate();
            String[] p = c.split(":");
            if (p.length >= 3) {
                catalog.put(p[0] + ":" + p[1], p[2]);
            }
        }
        for (Extracted e : list) {
            if (needsResolution(e.version)) {
                String v = catalog.get(e.groupArtifact());
                if (v != null) {
                    e.version = v;
                    e.resolvedFromCatalog = true;
                }
            }
        }
    }

    public static boolean needsResolution(String v) {
        return v == null || v.isEmpty() || v.startsWith("$") || v.startsWith("{")
                || v.matches("[A-Z_][A-Z0-9_]*") // CONSTANT_NAME
                || v.equalsIgnoreCase("latest") || v.equals("+");
    }

    private static boolean isKeyword(String s) {
        String k = s.toLowerCase();
        return k.equals("implementation") || k.equals("api") || k.equals("compileonly")
                || k.equals("runtimeonly") || k.equals("testimplementation")
                || k.equals("kapt") || k.equals("annotationprocessor")
                || k.equals("classpath") || k.equals("add") || k.equals("http")
                || k.equals("https") || k.equals("android") || k.equals("xmlns");
    }

    private static boolean isNoiseRepo(String a) {
        return a.equalsIgnoreCase("google.com") || a.equalsIgnoreCase("github.com");
    }

    private static List<Extracted> dedup(List<Extracted> list) {
        Map<String, Extracted> map = new LinkedHashMap<>();
        for (Extracted e : list) {
            String key = e.groupArtifact();
            if (!map.containsKey(key) || needsResolution(map.get(key).version)) {
                map.put(key, e);
            }
        }
        return new ArrayList<>(map.values());
    }
}
