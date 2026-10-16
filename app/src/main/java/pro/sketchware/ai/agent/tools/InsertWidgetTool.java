package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
            props.put("parentId", new JSONObject().put("type", "string").put("description", "Parent widget ID (e.g. linear1), or the literal root to insert directly into the root layout children"));
            props.put("widgetXml", new JSONObject().put("type", "string").put("description", "Full XML of the widget to insert"));
            props.put("position", new JSONObject().put("type", "string").put("enum", new JSONArray(new String[]{"start", "end"})).put("default", "end"));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("layout").put("parentId").put("widgetXml"));
            return new ToolSpec("insert_widget", "Inserts a widget into a Sketchware layout and verifies the live designer model. Use parentId='root' for a direct child of the root layout; otherwise pass the existing parent widget ID.", params);
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
        String sc_id = ctx.sc_id;

        File f = PathResolver.resolve(sc_id, layoutName);
        if (!f.exists()) return new ToolResult("Layout not found: " + f.getAbsolutePath(), true);
        String path = f.getAbsolutePath();

        if (!ctx.checkPermission(path, true)) return new ToolResult("Write denied.", true);

        String beforeHash = DomainToolHelper.sha256(f);
        String original = DomainToolHelper.readUtf8(f);
        if (!beforeHash.equals(DomainToolHelper.sha256(f))) {
            return ToolResult.structured("insert_widget", "CONFLICT", path,
                    "The layout changed while it was being read. No widget was inserted.", null, true);
        }
        
        // The root layout is represented as parent "root" by ViewBeanParser and
        // intentionally does not need an android:id attribute. Handle it explicitly.
        String next;
        if ("root".equalsIgnoreCase(parentId)) {
            int rootStart = findFirstStartTag(original);
            if (rootStart < 0) return new ToolResult("Layout has no root start tag.", true);
            int rootEnd = findTagEnd(original, rootStart);
            String rootName = readTagName(original, rootStart);
            if (rootEnd < 0 || rootName.isEmpty()) {
                return new ToolResult("Malformed XML: could not identify the root layout tag.", true);
            }
            if (isSelfClosingTag(original, rootEnd)) {
                String openingTag = original.substring(rootStart, rootEnd).replaceFirst("/\\s*$", "");
                next = original.substring(0, rootStart) + openingTag + ">\n"
                        + widgetXml + "\n</" + rootName + ">"
                        + original.substring(rootEnd + 1);
            } else if ("start".equals(position)) {
                next = original.substring(0, rootEnd + 1) + "\n"
                        + widgetXml + "\n" + original.substring(rootEnd + 1);
            } else {
                int closeIdx = findMatchingCloseTag(original, rootStart, rootName);
                if (closeIdx < 0) return new ToolResult("Could not locate the root layout's closing tag.", true);
                next = original.substring(0, closeIdx) + "\n"
                        + widgetXml + "\n" + original.substring(closeIdx);
            }
        } else {
            String parentMarker = "android:id=\"@+id/" + parentId + "\"";
            int markerIdx = original.indexOf(parentMarker);
            if (markerIdx == -1) {
                java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile(
                        "android:id\\s*=\\s*([\"'])@\\+id/"
                                + java.util.regex.Pattern.quote(parentId) + "\\1");
                java.util.regex.Matcher matcher = idPattern.matcher(original);
                if (matcher.find()) markerIdx = matcher.start();
            }
            if (markerIdx == -1) return new ToolResult("Parent ID '" + parentId + "' not found in layout.", true);

            int tagStart = original.lastIndexOf("<", markerIdx);
            int startTagEnd = findTagEnd(original, tagStart);
            String tagName = readTagName(original, tagStart);
            if (tagStart < 0 || startTagEnd < 0 || tagName.isEmpty()) {
                return new ToolResult("Malformed XML: parent start tag could not be parsed.", true);
            }
            if (isSelfClosingTag(original, startTagEnd)) {
                String base = original.substring(0, startTagEnd);
                next = base + ">\n" + widgetXml + "\n</" + tagName + ">"
                        + original.substring(startTagEnd + 1);
            } else if ("start".equals(position)) {
                next = original.substring(0, startTagEnd + 1) + "\n"
                        + widgetXml + original.substring(startTagEnd + 1);
            } else {
                int closeIdx = findMatchingCloseTag(original, tagStart, tagName);
                if (closeIdx < 0) return new ToolResult("Could not locate close tag for " + tagName, true);
                next = original.substring(0, closeIdx) + "\n"
                        + widgetXml + "\n" + original.substring(closeIdx);
            }
        }

        if (!DomainToolHelper.verifyXml(next)) return new ToolResult("Insertion resulted in malformed XML.", true);

        DomainToolHelper.backup(path);
        try {
            DomainToolHelper.atomicWrite(f, next, beforeHash);
            String actual = DomainToolHelper.readUtf8(f);
            if (!next.equals(actual)) throw new java.io.IOException("Saved layout does not match the requested XML.");
            DomainToolHelper.syncLayoutToDesigner(sc_id, f.getName(), next);
            if (!DomainToolHelper.verifyLiveLayout(sc_id, f.getName(), next)) {
                throw new java.io.IOException("The layout file was written, but the live Sketchware designer model did not match the saved XML.");
            }

            String newId = extractId(widgetXml);
            if (newId != null && !DomainToolHelper.verifyLiveModel(sc_id, f.getName(), newId)) {
                throw new java.io.IOException("The layout model was updated, but the newly requested widget ID could not be found in the live model.");
            }

            LiveRegistry.notifyChanged("layout", path);
            return ToolResult.structured("insert_widget", "SUCCESS", path,
                    "Exact XML read-back, live layout model parity, and requested widget identity verified.", null, false);
        } catch (Exception e) {
            boolean diskRestored = false;
            boolean modelRestored = false;
            try {
                if (f.isFile() && DomainToolHelper.contentSha256(next).equals(DomainToolHelper.sha256(f))) {
                    diskRestored = DomainToolHelper.restoreIfUnchanged(path, DomainToolHelper.contentSha256(next));
                    if (diskRestored) {
                        DomainToolHelper.syncLayoutToDesigner(sc_id, f.getName(), original);
                        modelRestored = DomainToolHelper.verifyLiveLayout(sc_id, f.getName(), original);
                    }
                }
            } catch (Exception ignored) {}
            String message = e.getMessage() == null ? "Widget insertion failed." : e.getMessage();
            message += " Disk rollback " + (diskRestored ? "verified" : "not verified")
                    + "; designer rollback " + (modelRestored ? "verified." : "not verified.");
            return ToolResult.structured("insert_widget", "ERROR", path, message, null, true);
        }
    }

    private int findFirstStartTag(String xml) {
        int cursor = 0;
        while ((cursor = xml.indexOf('<', cursor)) >= 0) {
            if (cursor + 1 >= xml.length()) return -1;
            char next = xml.charAt(cursor + 1);
            if (next != '/' && next != '!' && next != '?') return cursor;
            cursor++;
        }
        return -1;
    }

    /** Finds '>' while respecting quoted attribute values. */
    private int findTagEnd(String xml, int startIdx) {
        if (startIdx < 0 || startIdx >= xml.length() || xml.charAt(startIdx) != '<') return -1;
        char quote = 0;
        for (int i = startIdx + 1; i < xml.length(); i++) {
            char c = xml.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private String readTagName(String xml, int tagStart) {
        if (tagStart < 0 || tagStart >= xml.length() || xml.charAt(tagStart) != '<') return "";
        int start = tagStart + 1;
        while (start < xml.length() && Character.isWhitespace(xml.charAt(start))) start++;
        int end = start;
        while (end < xml.length()) {
            char c = xml.charAt(end);
            if (Character.isWhitespace(c) || c == '/' || c == '>') break;
            end++;
        }
        return end > start ? xml.substring(start, end) : "";
    }

    private boolean isSelfClosingTag(String xml, int tagEnd) {
        if (tagEnd < 0 || tagEnd >= xml.length()) return false;
        int i = tagEnd - 1;
        while (i >= 0 && Character.isWhitespace(xml.charAt(i))) i--;
        return i >= 0 && xml.charAt(i) == '/';
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
                int openEnd = findTagEnd(xml, nextOpen);
                if (openEnd >= 0 && isSelfClosingTag(xml, openEnd)) {
                    cur = openEnd;
                } else {
                    depth++;
                    cur = nextOpen;
                }
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
