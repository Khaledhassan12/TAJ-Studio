package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.ai.live.LiveRegistry;
import android.util.Log;

public final class InsertWidgetTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("layout", new JSONObject().put("type", "string").put("description", "Layout filename or alias (e.g. main.xml or res/layout/main.xml)"));
            props.put("parentId", new JSONObject().put("type", "string").put("description", "ID of the parent widget (e.g. linear1)"));
            props.put("widgetXml", new JSONObject().put("type", "string").put("description", "Full XML of the widget to insert"));
            props.put("position", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"start", "end"})).put("default", "end"));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("layout").put("parentId").put("widgetXml"));
            return new ToolSpec("insert_widget", "Inserts a widget into a layout safely.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.RES;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String layoutName = args.getString("layout");
        String parentId = args.getString("parentId");
        String widgetXml = args.getString("widgetXml");
        String position = args.optString("position", "end");
        String sc_id = args.optString("sc_id", ctx.sc_id);

        File f = PathResolver.resolve(sc_id, layoutName);
        if (!f.exists()) return new ToolResult("Layout not found: " + f.getAbsolutePath(), true);
        String path = f.getAbsolutePath();

        if (!ctx.checkPermission(path, true)) return new ToolResult("Write denied.", true);

        String original = FileUtil.readFile(path);
        
        // Find parent tag and its close tag. 
        // For simplicity and since we can't easily get offsets from XmlPullParser, 
        // we'll look for the android:id attribute.
        
        String parentMarker = "android:id=\"@+id/" + parentId + "\"";
        int markerIdx = original.indexOf(parentMarker);
        if (markerIdx == -1) return new ToolResult("Parent ID '" + parentId + "' not found in layout.", true);
        
        // Find the end of the start tag
        int startTagEnd = original.indexOf(">", markerIdx);
        if (startTagEnd == -1) return new ToolResult("Malformed XML: parent tag not closed.", true);
        
        String next;
        if (original.charAt(startTagEnd - 1) == '/') {
            // Self-closing tag. Convert to open/close.
            String base = original.substring(0, startTagEnd - 1);
            int tagStart = original.lastIndexOf("<", markerIdx);
            String tagName = original.substring(tagStart + 1, original.indexOf(" ", tagStart + 1));
            next = base + ">\n" + widgetXml + "\n</" + tagName + ">" + original.substring(startTagEnd + 1);
        } else {
            if (position.equals("start")) {
                next = original.substring(0, startTagEnd + 1) + "\n" + widgetXml + original.substring(startTagEnd + 1);
            } else {
                // Find matching close tag. This is hard without real parsing.
                // Simple search for next close tag of same name at same level would be better.
                // For now, we'll try to find the LAST close tag before the next sibling or end.
                int tagStart = original.lastIndexOf("<", markerIdx);
                String tagName = original.substring(tagStart + 1).split("[ >]")[0];
                String closeTag = "</" + tagName + ">";
                int closeIdx = findMatchingCloseTag(original, tagStart, tagName);
                if (closeIdx == -1) return new ToolResult("Could not locate close tag for " + tagName, true);
                next = original.substring(0, closeIdx) + "\n" + widgetXml + "\n" + original.substring(closeIdx);
            }
        }

        if (!DomainToolHelper.verifyXml(next)) return new ToolResult("Insertion resulted in malformed XML.", true);

        DomainToolHelper.backup(path);
        try {
            FileUtil.writeFile(path, next);
            DomainToolHelper.syncLayoutToDesigner(sc_id, f.getName(), next);
            LiveRegistry.notifyChanged("layout", path);
            
            // Verify ID in live model
            String newId = extractId(widgetXml);
            if (newId != null && !DomainToolHelper.verifyLiveModel(sc_id, f.getName(), newId)) {
                Log.w("AGENT", "Widget inserted but not found in live model verification.");
            }
            
            return ToolResult.structured("insert_widget", "SUCCESS", path, "verified: widget inserted", null, false);
        } catch (Exception e) {
            DomainToolHelper.restore(path);
            return ToolResult.structured("insert_widget", "ERROR", path, "Insertion failed: " + e.getMessage(), null, true);
        }
    }

    private int findMatchingCloseTag(String xml, int startIdx, String tagName) {
        String open = "<" + tagName;
        String close = "</" + tagName + ">";
        int depth = 0;
        int cur = startIdx;
        while (cur < xml.length()) {
            int nextOpen = -1;
            int search = xml.indexOf(open, cur + 1);
            if (search != -1) {
                char nextChar = xml.charAt(search + open.length());
                if (nextChar == ' ' || nextChar == '>' || nextChar == '/') {
                    nextOpen = search;
                }
            }
            
            int nextClose = xml.indexOf(close, cur + 1);
            
            if (nextClose == -1) return -1;
            
            if (nextOpen != -1 && nextOpen < nextClose) {
                depth++;
                cur = nextOpen;
            } else {
                if (depth == 0) return nextClose;
                depth--;
                cur = nextClose;
            }
        }
        return -1;
    }

    private String extractId(String xml) {
        int idx = xml.indexOf("android:id=\"@+id/");
        if (idx == -1) return null;
        int start = idx + 17;
        int end = xml.indexOf("\"", start);
        if (end == -1) return null;
        return xml.substring(start, end);
    }
}
