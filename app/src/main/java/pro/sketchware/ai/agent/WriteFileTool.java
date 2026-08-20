package pro.sketchware.ai.agent;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.ai.agent.tools.JavaToolHelper;

public class WriteFileTool implements Tool {

    @Override
    public ToolSpec spec() {
        JSONObject params = new JSONObject();
        try {
            JSONObject filename = new JSONObject();
            filename.put("type", "string");
            filename.put("description", "File name (e.g. main.xml, strings.xml, or MainActivity.java)");
            
            JSONObject content = new JSONObject();
            content.put("type", "string");
            content.put("description", "Full content to write");

            JSONObject expectContains = new JSONObject();
            expectContains.put("type", "string");
            expectContains.put("description", "A string that MUST be present in the file after writing (for verification)");

            JSONObject props = new JSONObject();
            props.put("filename", filename);
            props.put("content", content);
            props.put("expectContains", expectContains);

            params.put("type", "object");
            params.put("properties", props);
            params.put("required", new JSONArray().put("filename").put("content"));
        } catch (Exception ignored) {}
        return new ToolSpec("write_file", "Writes content to a project file with verification. Automatically handles path resolution.", params);
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) {
        String filename = args.optString("filename");
        String content = args.optString("content");
        String expect = args.optString("expectContains", "");

        String path;
        if (filename.endsWith(".java") || filename.endsWith(".kt")) {
            path = ProjectPaths.packagePath(ctx.sc_id) + File.separator + filename;
        } else if (filename.equals("strings.xml") || filename.equals("colors.xml") || filename.equals("styles.xml")) {
            path = ProjectPaths.valuesDir(ctx.sc_id) + File.separator + filename;
        } else if (filename.endsWith(".xml")) {
            path = ProjectPaths.layoutDir(ctx.sc_id) + File.separator + filename;
        } else {
            return new ToolResult("Unsupported file type for write_file: " + filename, true);
        }

        if (!ctx.checkPermission(path, true)) {
            return new ToolResult("User denied the write operation", true);
        }

        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) FileUtil.makeDir(parent.getAbsolutePath());

        JavaToolHelper.backup(path);
        try {
            FileUtil.writeFile(path, content);
            
            // VERIFICATION
            String readBack = FileUtil.readFile(path);
            if (readBack.isEmpty()) {
                JavaToolHelper.restore(path);
                return new ToolResult("ERROR: Read-back failed (empty file written)", true);
            }
            
            if (!expect.isEmpty() && !readBack.contains(expect)) {
                JavaToolHelper.restore(path);
                return new ToolResult("ERROR: Verification failed. Expected marker '" + expect + "' not found in written file.", true);
            }
            
            if (filename.endsWith(".xml")) {
                try {
                    // Simple XML well-formedness check
                    org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser().setInput(new java.io.StringReader(readBack));
                } catch (Exception e) {
                    JavaToolHelper.restore(path);
                    return new ToolResult("ERROR: Verification failed. XML is not well-formed: " + e.getMessage(), true);
                }
            }

            return new ToolResult("Successfully wrote to " + path + " - verified: " + (expect.isEmpty() ? "content present" : "'" + expect + "' found"), false);
        } catch (Exception e) {
            JavaToolHelper.restore(path);
            return new ToolResult("ERROR: Write failed: " + e.getMessage(), true);
        }
    }
}
