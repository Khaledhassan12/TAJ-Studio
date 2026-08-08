package pro.sketchware.ai.agent.tools.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import a.a.a.wq;
import pro.sketchware.ai.agent.tools.*;
import pro.sketchware.utility.FileUtil;

public class FileTools {

    public static class ReadFileTool implements Tool {
        @Override
        public ToolSpec spec() {
            return new ToolSpec("readFile", "Read content of a project file", "{\"path\": \"string\"}");
        }

        @Override
        public ToolResult execute(ToolArgs args, ToolCtx ctx) {
            String path = args.getString("path");
            if (path == null) return ToolResult.error("Path required");
            File f = resolve(ctx.scId, path);
            if (!f.exists()) return ToolResult.error("File not found: " + path);
            return ToolResult.success(FileUtil.readFile(f.getAbsolutePath()));
        }
    }

    public static class ListFilesTool implements Tool {
        @Override
        public ToolSpec spec() {
            return new ToolSpec("listFiles", "List files in a project directory", "{\"path\": \"string\", \"ext\": \"string\"}");
        }

        @Override
        public ToolResult execute(ToolArgs args, ToolCtx ctx) {
            String path = args.getString("path", ".");
            String ext = args.getString("ext");
            File dir = resolve(ctx.scId, path);
            if (!dir.exists() || !dir.isDirectory()) return ToolResult.error("Directory not found: " + path);

            File[] files = dir.listFiles();
            if (files == null) return ToolResult.success("[]");
            
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < files.length; i++) {
                if (ext != null && !files[i].getName().endsWith(ext)) continue;
                sb.append("\"").append(files[i].getName()).append(files[i].isDirectory() ? "/" : "").append("\"");
                if (i < files.length - 1) sb.append(", ");
            }
            sb.append("]");
            return ToolResult.success(sb.toString());
        }
    }

    public static class WriteFileTool implements Tool {
        @Override
        public ToolSpec spec() {
            return new ToolSpec("writeFile", "Write content to a file", "{\"path\": \"string\", \"content\": \"string\"}");
        }

        @Override
        public ToolResult execute(ToolArgs args, ToolCtx ctx) {
            String path = args.getString("path");
            String content = args.getString("content");
            if (path == null || content == null) return ToolResult.error("Path and content required");
            
            File f = resolve(ctx.scId, path);
            if (f.exists()) {
                backup(ctx.scId, f);
            } else {
                f.getParentFile().mkdirs();
            }
            FileUtil.writeFile(f.getAbsolutePath(), content);
            return ToolResult.success("Written: " + path);
        }
    }

    private static File resolve(String scId, String path) {
        String root = wq.b(scId);
        // Normalize path to prevent escaping root
        if (path.startsWith("/")) path = path.substring(1);
        return new File(root, path);
    }

    private static void backup(String scId, File f) {
        String root = wq.b(scId);
        String backupRoot = root + "/.upgrade_backup/" + System.currentTimeMillis();
        String rel = f.getAbsolutePath().replace(root, "");
        File dest = new File(backupRoot, rel);
        dest.getParentFile().mkdirs();
        FileUtil.copyFile(f.getAbsolutePath(), dest.getAbsolutePath());
    }
}
