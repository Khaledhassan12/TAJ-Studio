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
        if (FileUtil.isExistFile(path)) {
            FileUtil.copyFile(path, path + ".bak");
        }
    }

    public static void restore(String path) {
        if (FileUtil.isExistFile(path + ".bak")) {
            FileUtil.moveFile(path + ".bak", path);
        }
    }

    public static boolean verifyXml(String content) {
        try {
            org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser().setInput(new java.io.StringReader(content));
            return true;
        } catch (Exception e) {
            return false;
        }
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
