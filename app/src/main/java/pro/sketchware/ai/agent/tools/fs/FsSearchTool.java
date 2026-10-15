package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.agent.tools.DomainToolHelper;

public final class FsSearchTool implements Tool {
    private static final int MAX_DEPTH = 8;
    private static final int MAX_ENTRIES = 2000;
    private static final int MAX_MATCHES = 100;
    private static final long MAX_SCANNED_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_TEXT_FILE_BYTES = 512L * 1024L;

    @Override public ToolSpec spec() {
        try {
            JSONObject props = new JSONObject();
            props.put("pattern", new JSONObject().put("type", "string").put("maxLength", 4096));
            props.put("path", new JSONObject().put("type", "string").put("maxLength", 4096)
                    .put("description", "Base path to search. Defaults to the active project's data root."));
            JSONObject schema = new JSONObject();
            schema.put("type", "object"); schema.put("properties", props);
            schema.put("required", new JSONArray().put("pattern"));
            return new ToolSpec("fs_search", "Bounded, deterministic filename and text search. Reports partial results explicitly when limits or read errors prevent a complete search.", schema);
        } catch (Exception e) { throw new IllegalStateException("Could not construct fs_search schema.", e); }
    }
    @Override public Domain domain() { return Domain.FS; }

    @Override public ToolResult run(JSONObject args, ToolContext ctx) {
        String pattern;
        String rootPath = null;
        try {
            pattern = args.getString("pattern");
            if (pattern.trim().isEmpty()) return ToolResult.structured("fs_search", "INVALID_ARGUMENTS", null,
                    "Search pattern must not be empty.", null, true);
            File root = PathResolver.resolve(ctx.sc_id, args.optString("path", ""));
            rootPath = root.getAbsolutePath();
            if (!root.isDirectory()) return ToolResult.structured("fs_search", "NOT_FOUND", rootPath,
                    "Search root does not exist or is not a directory.", null, true);

            SearchState state = new SearchState();
            walk(root, pattern, state, 0);
            JSONObject result = new JSONObject();
            result.put("tool", "fs_search");
            result.put("root", rootPath);
            result.put("matches", state.matches);
            result.put("scannedFiles", state.scannedFiles);
            result.put("scannedBytes", state.scannedBytes);
            result.put("skippedFiles", state.skippedFiles);
            result.put("truncated", state.truncated);
            boolean partial = state.truncated || state.skippedFiles > 0;
            result.put("status", partial ? "PARTIAL" : "SUCCESS");
            if (partial) {
                result.put("message", "Search is incomplete because one or more safety limits, symbolic links, binary/large files, or filesystem errors were encountered. Do not treat absence of a match as proof that no match exists.");
            } else if (state.matches.length() == 0) {
                result.put("message", "No matches were found within the searched project root.");
            }
            return new ToolResult(result.toString(), partial);
        } catch (Exception e) {
            return ToolResult.structured("fs_search", "ERROR", rootPath,
                    e.getMessage() == null ? "Search failed before completion." : e.getMessage(), null, true);
        }
    }

    private static final class SearchState {
        final JSONArray matches = new JSONArray();
        int scannedFiles;
        int skippedFiles;
        long scannedBytes;
        boolean truncated;
    }

    private void walk(File directory, String pattern, SearchState state, int depth) {
        if (state.truncated) return;
        if (depth > MAX_DEPTH || state.scannedFiles >= MAX_ENTRIES
                || state.matches.length() >= MAX_MATCHES
                || state.scannedBytes >= MAX_SCANNED_BYTES) {
            state.truncated = true;
            return;
        }
        try {
            if (Files.isSymbolicLink(directory.toPath())) { state.skippedFiles++; return; }
        } catch (Exception e) { state.skippedFiles++; return; }

        File[] children = directory.listFiles();
        if (children == null) { state.skippedFiles++; return; }
        List<File> ordered = new ArrayList<>();
        Collections.addAll(ordered, children);
        ordered.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        String needle = pattern.toLowerCase(Locale.ROOT);

        for (File file : ordered) {
            if (state.truncated) return;
            String name = file.getName();
            if (name.endsWith(".bak") || name.contains(".agent-backup-")
                    || name.contains(".agent_trash") || name.equals(".git")) continue;
            try {
                if (Files.isSymbolicLink(file.toPath())) { state.skippedFiles++; continue; }
            } catch (Exception e) { state.skippedFiles++; continue; }

            if (name.toLowerCase(Locale.ROOT).contains(needle)) {
                state.matches.put(file.getAbsolutePath());
                if (state.matches.length() >= MAX_MATCHES) { state.truncated = true; return; }
            }
            if (file.isDirectory()) {
                walk(file, pattern, state, depth + 1);
                continue;
            }
            if (!file.isFile()) { state.skippedFiles++; continue; }
            state.scannedFiles++;
            if (!isTextFile(name)) continue;
            long size = file.length();
            if (size > MAX_TEXT_FILE_BYTES || state.scannedBytes + size > MAX_SCANNED_BYTES) {
                state.skippedFiles++;
                if (state.scannedBytes + size > MAX_SCANNED_BYTES) state.truncated = true;
                continue;
            }
            try {
                String text = DomainToolHelper.readUtf8(file);
                state.scannedBytes += size;
                if (text.toLowerCase(Locale.ROOT).contains(needle)
                        && !containsPath(state.matches, file.getAbsolutePath())) {
                    state.matches.put(file.getAbsolutePath());
                    if (state.matches.length() >= MAX_MATCHES) { state.truncated = true; return; }
                }
            } catch (Exception e) {
                state.skippedFiles++;
            }
            if (state.scannedFiles >= MAX_ENTRIES) { state.truncated = true; return; }
        }
    }

    private static boolean containsPath(JSONArray paths, String value) {
        for (int i = 0; i < paths.length(); i++) if (value.equals(paths.optString(i))) return true;
        return false;
    }

    private static boolean isTextFile(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        String[] extensions = {".java", ".kt", ".kts", ".xml", ".json", ".md", ".txt", ".properties",
                ".gradle", ".html", ".css", ".yaml", ".yml", ".csv", ".pro", ".cfg", ".ini", ".sh",
                ".cpp", ".c", ".h", ".hpp", ".cmake", ".xml", ".js", ".ts", ".sql", ".svg"};
        for (String extension : extensions) if (n.endsWith(extension)) return true;
        return false;
    }
}
