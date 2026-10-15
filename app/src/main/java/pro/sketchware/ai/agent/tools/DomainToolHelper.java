package pro.sketchware.ai.agent.tools;

import android.os.Handler;
import android.os.Looper;
import java.io.File;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.ai.live.UiPoster;
import pro.sketchware.utility.FileUtil;
import com.besome.sketch.design.DesignActivity;

import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.managers.inject.InjectRootLayoutManager;
import com.besome.sketch.beans.ViewBean;
import a.a.a.jC;
import android.util.Pair;
import java.util.ArrayList;
import java.util.Map;

public final class DomainToolHelper {

    public static void backup(String path) {
        File target = new File(path);
        File backup = new File(path + ".bak");
        File absentMarker = new File(path + ".bak.absent");
        if (absentMarker.exists()) absentMarker.delete();
        if (target.isFile()) {
            FileUtil.copyFile(path, backup.getAbsolutePath());
        } else if (!target.exists()) {
            if (backup.exists()) backup.delete();
            try {
                absentMarker.createNewFile();
            } catch (java.io.IOException ignored) {
                // If the marker cannot be written, callers still verify the operation before reporting success.
            }
        }
    }

    public static void restore(String path) {
        File target = new File(path);
        File backup = new File(path + ".bak");
        File absentMarker = new File(path + ".bak.absent");
        if (backup.isFile()) {
            FileUtil.moveFile(backup.getAbsolutePath(), path);
            if (absentMarker.exists()) absentMarker.delete();
        } else if (absentMarker.exists()) {
            if (target.isFile()) target.delete();
            absentMarker.delete();
        }
    }

    /** Finalize a successful transaction while retaining any pre-existing-file backup for manual recovery. */
    public static void commitBackup(String path) {
        File absentMarker = new File(path + ".bak.absent");
        if (absentMarker.exists()) absentMarker.delete();
    }

    public static boolean verifyXml(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        try {
            org.xmlpull.v1.XmlPullParser parser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(new java.io.StringReader(content));
            boolean seenRoot = false;
            int depth = 0;
            int event;
            while ((event = parser.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    depth++;
                    if (depth == 1) seenRoot = true;
                } else if (event == org.xmlpull.v1.XmlPullParser.END_TAG) {
                    depth--;
                    if (depth < 0) return false;
                }
            }
            return seenRoot && depth == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isKnownBinaryFileName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        String[] extensions = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp", ".ico",
                ".mp3", ".wav", ".ogg", ".m4a", ".mp4", ".webm", ".ttf", ".otf", ".woff",
                ".woff2", ".zip", ".jar", ".aar", ".class", ".dex", ".so", ".bin", ".pdf",
                ".db", ".sqlite", ".apk", ".aab", ".keystore", ".jks"};
        for (String extension : extensions) if (lower.endsWith(extension)) return true;
        return false;
    }

    public static boolean isTextEditableFileName(String name) {
        if (name == null || isKnownBinaryFileName(name)) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        String[] extensions = {".xml", ".json", ".txt", ".properties", ".html", ".htm", ".css",
                ".js", ".svg", ".md", ".csv", ".yaml", ".yml", ".gradle", ".java", ".kt",
                ".xml", ".pro", ".mf", ".toml", ".ini", ".sh"};
        for (String extension : extensions) if (lower.endsWith(extension)) return true;
        return false;
    }

    public static void refreshEditor(Tool.ToolContext ctx) {
        DesignActivity activity = ctx.activity.get();
        if (activity != null) {
            UiPoster.post(activity::onProjectUpdated);
        }
    }

    public static void syncLayoutToDesigner(String scId, String xmlName, String content) throws Exception {
        ViewBeanParser parser = new ViewBeanParser(content);
        parser.setSkipRoot(true);
        ArrayList<ViewBean> parsedLayout = parser.parse();
        Pair<String, Map<String, String>> root = parser.getRootAttributes();

        // 1. Update Root
        new InjectRootLayoutManager(scId).set(xmlName, InjectRootLayoutManager.toRoot(root));

        // 2. Update Widgets in Memory
        jC.a(scId).c.put(xmlName, parsedLayout);

        // 3. Save to disk (Persistent truth for Designer)
        jC.a(scId).i();
    }

    public static boolean verifyLiveModel(String scId, String xmlName, String expectedId) {
        if (expectedId == null || expectedId.isEmpty()) return false;
        ArrayList<ViewBean> liveBeans = jC.a(scId).c.get(xmlName);
        if (liveBeans != null) {
            for (ViewBean b : liveBeans) {
                if (expectedId.equals(b.id)) return true;
            }
        }
        return false;
    }

    private DomainToolHelper() {}
}
