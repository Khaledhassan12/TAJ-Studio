package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.ai.live.LiveRegistry;

public final class ResMoveCopyTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("fromFolder", new JSONObject().put("type", "string"));
            props.put("fromName", new JSONObject().put("type", "string"));
            props.put("toFolder", new JSONObject().put("type", "string"));
            props.put("toName", new JSONObject().put("type", "string"));
            props.put("op", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"move", "copy"})));
            props;
            params.put("properties", props);
            params.put("required", new JSONArray().put("fromFolder").put("fromName").put("toFolder").put("toName").put("op"));
            return new ToolSpec("res_move_copy", "Moves or copies a resource file.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.RES;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String sc_id = ctx.sc_id;
        File source = PathResolver.resolve(sc_id, "res/" + args.getString("fromFolder") + "/" + args.getString("fromName"));
        File destination = PathResolver.resolve(sc_id, "res/" + args.getString("toFolder") + "/" + args.getString("toName"));
        String fromPath = source.getAbsolutePath();
        String toPath = destination.getAbsolutePath();
        String op = args.getString("op");

        if (!source.isFile()) return new ToolResult("Resource source not found or is not a regular file.", true);
        if (!("move".equals(op) || "copy".equals(op))) return new ToolResult("Unsupported resource operation.", true);
        if (fromPath.equals(toPath)) return new ToolResult("Source and destination are identical.", true);
        if (destination.exists()) return new ToolResult("Destination already exists. Choose a new destination to avoid overwriting a resource.", true);
        if ("move".equals(op) && !ctx.checkPermission(fromPath, true)) return new ToolResult("Denied.", true);
        if (!ctx.checkPermission(toPath, false)) return new ToolResult("Denied.", true);

        File parent = destination.getParentFile();
        if (parent != null && !parent.exists()) FileUtil.makeDir(parent.getAbsolutePath());
        if ("copy".equals(op)) {
            try {
                FileUtil.copyFile(fromPath, toPath);
                if (!sameBytes(source, destination)) {
                    destination.delete();
                    return new ToolResult("Resource copy verification failed; the partial destination was removed.", true);
                }
            } catch (Exception e) {
                if (destination.exists()) destination.delete();
                return new ToolResult("Resource copy failed: " + e.getMessage(), true);
            }
        } else {
            FileUtil.moveFile(fromPath, toPath);
            if (source.exists() || !destination.isFile()) {
                return new ToolResult("Resource move verification failed; inspect the source and destination before retrying.", true);
            }
            LiveRegistry.notifyChanged(args.getString("fromFolder"), fromPath);
        }
        LiveRegistry.notifyChanged(args.getString("toFolder"), toPath);
        return new ToolResult("verified: " + op + " done to " + toPath, false);
    }
    private static boolean sameBytes(File source, File destination) throws IOException {
        if (!source.isFile() || !destination.isFile() || source.length() != destination.length()) return false;
        try (BufferedInputStream a = new BufferedInputStream(new FileInputStream(source));
             BufferedInputStream b = new BufferedInputStream(new FileInputStream(destination))) {
            byte[] left = new byte[8192];
            byte[] right = new byte[8192];
            while (true) {
                int n = readChunk(a, left);
                int m = readChunk(b, right);
                if (n != m) return false;
                if (n == -1) return true;
                for (int i = 0; i < n; i++) if (left[i] != right[i]) return false;
            }
        }
    }

    private static int readChunk(java.io.InputStream input, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int count = input.read(buffer, total, buffer.length - total);
            if (count < 0) return total == 0 ? -1 : total;
            total += count;
        }
        return total;
    }

}
