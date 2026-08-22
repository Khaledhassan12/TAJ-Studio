package pro.sketchware.ai.agent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import pro.sketchware.utility.FilePathUtil;
import a.a.a.yq;
import pro.sketchware.SketchApplication;
import mod.hey.studios.activity.managers.assets.ManageAssetsActivity;
import pro.sketchware.utility.FileUtil;

public final class PathResolver {

    public static String dataRoot(String scId) {
        return ProjectPaths.dataRoot(scId);
    }

    public static String resourceRoot(String scId) {
        return ProjectPaths.resourceRoot(scId);
    }

    public static String assetsRoot(String scId) {
        return ProjectPaths.assetsRoot(scId);
    }

    public static String javaManagerRoot(String scId) {
        return ProjectPaths.javaManagerRoot(scId);
    }

    public static String projectJavaRoot(String scId) {
        return ProjectPaths.projectJavaRoot(scId);
    }

    public static File resolve(String scId, String userPath) {
        if (userPath == null || userPath.isEmpty()) return new File(dataRoot(scId));

        File f = new File(userPath);
        if (f.isAbsolute() && f.exists()) return f;

        String path = userPath.replace("./", "");
        if (path.startsWith("/")) path = path.substring(1);

        if (path.startsWith("res/") || path.startsWith("resource/")) {
            String rest = path.substring(path.indexOf("/") + 1);
            return new File(resourceRoot(scId), rest);
        }

        String[] knownFolders = {"layout/", "values/", "drawable/", "anim/", "menu/", "drawable-xhdpi/", "values-night/"};
        for (String folder : knownFolders) {
            if (path.startsWith(folder)) return new File(resourceRoot(scId), path);
        }

        if (path.startsWith("assets/")) {
            return new File(assetsRoot(scId), path.substring(7));
        }

        if (path.startsWith("java/")) {
            return new File(projectJavaRoot(scId), path.substring(5));
        }

        // Bare filename: search order
        String[] searchOrder = {"layout", "drawable", "values", "anim", "menu", "drawable-xhdpi", "values-night"};
        for (String folder : searchOrder) {
            String suffix = (folder.startsWith("drawable") || folder.startsWith("anim") || folder.startsWith("layout") || folder.startsWith("menu") || folder.startsWith("values")) ? ".xml" : "";
            File candidate = new File(resourceRoot(scId) + File.separator + folder, path + (path.contains(".") ? "" : suffix));
            if (candidate.exists()) return candidate;
        }

        File assetCandidate = new File(assetsRoot(scId), path);
        if (assetCandidate.exists()) return assetCandidate;
        
        File javaCandidate = new File(javaManagerRoot(scId), path + (path.contains(".") ? "" : ".java"));
        if (javaCandidate.exists()) return javaCandidate;
        if (!path.contains(".")) {
             File ktCandidate = new File(javaManagerRoot(scId), path + ".kt");
             if (ktCandidate.exists()) return ktCandidate;
        }

        return new File(dataRoot(scId), path);
    }

    public static List<String> candidates(String scId, String filename) {
        List<String> list = new ArrayList<>();
        String[] searchOrder = {"layout", "drawable", "values", "anim", "menu", "drawable-xhdpi", "values-night"};
        for (String folder : searchOrder) {
            list.add("res/" + folder + "/" + filename);
        }
        list.add("assets/" + filename);
        list.add("java/" + filename);
        return list;
    }

    private PathResolver() {}
}
