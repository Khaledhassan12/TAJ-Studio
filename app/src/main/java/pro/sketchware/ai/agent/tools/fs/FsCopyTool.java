package pro.sketchware.ai.agent.tools.fs;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.utility.FileUtil;

public final class FsCopyTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            params.put("properties", new JSONObject().put("src", new JSONObject().put("type", "string")).put("dst", new JSONObject().put("type", "string")));
            params.put("required", new JSONArray().put("src").put("dst"));
            return new ToolSpec("fs_copy", "Copies a file or folder.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.FS;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        File source = PathResolver.resolve(ctx.sc_id, args.getString("src"));
        File destination = PathResolver.resolve(ctx.sc_id, args.getString("dst"));
        String src = source.getAbsolutePath();
        String dst = destination.getAbsolutePath();
        if (!source.exists()) return new ToolResult("Source not found.", true);
        if (src.equals(dst)) return new ToolResult("Source and destination are identical.", true);
        if (source.isDirectory() && dst.startsWith(src + File.separator)) {
            return new ToolResult("Cannot copy a directory into itself or one of its descendants.", true);
        }
        if (!ctx.checkPermission(dst, destination.exists())) return new ToolResult("Denied.", true);

        try {
            if (source.isDirectory()) {
                FileUtil.copyDirectory(source, destination);
            } else {
                File parent = destination.getParentFile();
                if (parent != null && !parent.exists()) FileUtil.makeDir(parent.getAbsolutePath());
                FileUtil.copyFile(src, dst);
            }
            if (!verifyTree(source, destination)) {
                return new ToolResult("Copy verification failed: destination does not match source.", true);
            }
            return new ToolResult("verified: copied to " + dst, false);
        } catch (Exception e) {
            return new ToolResult("Copy failed: " + e.getMessage(), true);
        }
    }

    private static boolean verifyTree(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            if (!destination.isDirectory()) return false;
            File[] sourceChildren = source.listFiles();
            File[] destinationChildren = destination.listFiles();
            if (sourceChildren == null || destinationChildren == null || sourceChildren.length != destinationChildren.length) return false;
            java.util.Map<String, File> destinationByName = new java.util.HashMap<>();
            for (File child : destinationChildren) destinationByName.put(child.getName(), child);
            for (File child : sourceChildren) {
                File copy = destinationByName.get(child.getName());
                if (copy == null || !verifyTree(child, copy)) return false;
            }
            return true;
        }
        if (!source.isFile() || !destination.isFile() || source.length() != destination.length()) return false;
        try (BufferedInputStream a = new BufferedInputStream(new FileInputStream(source));
             BufferedInputStream b = new BufferedInputStream(new FileInputStream(destination))) {
            byte[] left = new byte[8192];
            byte[] right = new byte[8192];
            while (true) {
                int n = a.read(left);
                int m = b.read(right);
                if (n != m) return false;
                if (n == -1) return true;
                if (!Arrays.equals(Arrays.copyOf(left, n), Arrays.copyOf(right, m))) return false;
            }
        }
    }
}
