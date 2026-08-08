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
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
            }
            FileUtil.writeFile(f.getAbsolutePath(), content);
            return ToolResult.success("Written: " + path);
        }
    }

    public static class CreateFileTool implements Tool {
        @Override
        public ToolSpec spec() {
            return new ToolSpec("createFile", "Create a new file", "{\"path\": \"string\", \"content\": \"string\"}");
        }

        @Override
        public ToolResult execute(ToolArgs args, ToolCtx ctx) {
            String path = args.getString("path");
            String content = args.getString("content", "");
            if (path == null) return ToolResult.error("Path required");
            
            File f = resolve(ctx.scId, path);
            if (f.exists()) return ToolResult.error("File already exists");
            
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            FileUtil.writeFile(f.getAbsolutePath(), content);
            return ToolResult.success("Created: " + path);
        }
    }

    public static class PatchFileTool implements Tool {
        @Override
        public ToolSpec spec() {
            return new ToolSpec("patchFile", "Patch an existing file by replacing oldText with newText", 
                "{\"path\": \"string\", \"oldText\": \"string\", \"newText\": \"string\"}");
        }

        @Override
        public ToolResult execute(ToolArgs args, ToolCtx ctx) {
            String path = args.getString("path");
            String oldText = args.getString("oldText");
            String newText = args.getString("newText");
            if (path == null || oldText == null || newText == null) return ToolResult.error("Args required");

            File f = resolve(ctx.scId, path);
            if (!f.exists()) return ToolResult.error("File not found: " + path);

            String content = FileUtil.readFile(f.getAbsolutePath());
            if (!content.contains(oldText)) return ToolResult.error("Old text not found in file");

            backup(ctx.scId, f);
            String patched = content.replace(oldText, newText);
            FileUtil.writeFile(f.getAbsolutePath(), patched);
            return ToolResult.success("Patched: " + path);
        }
    }

    public static class DeleteFileTool implements Tool {
        @Override
        public ToolSpec spec() {
            return new ToolSpec("deleteFile", "Delete a project file (requires confirmation)", "{\"path\": \"string\"}");
        }

        @Override
        public ToolResult execute(ToolArgs args, ToolCtx ctx) {
            if (!ctx.confirmDestructive()) return ToolResult.error("Destructive action requires confirmation");
            String path = args.getString("path");
            if (path == null) return ToolResult.error("Path required");

            File f = resolve(ctx.scId, path);
            if (!f.exists()) return ToolResult.error("File not found");

            backup(ctx.scId, f);
            if (f.delete()) return ToolResult.success("Deleted: " + path);
            return ToolResult.error("Failed to delete: " + path);
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
