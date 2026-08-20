package pro.sketchware.ai.agent;

import java.io.File;
import a.a.a.wq;
import a.a.a.lC;
import java.util.HashMap;

/**
 * Source of truth for project file paths.
 * Mapped to the on-device data tree used by the Sketchware Pro editor.
 */
public final class ProjectPaths {

    public static String root(String scId) {
        return wq.b(scId);
    }

    public static String javaDir(String scId) {
        return root(scId) + File.separator + "files" + File.separator + "java";
    }

    public static String layoutDir(String scId) {
        return root(scId) + File.separator + "files" + File.separator + "resource" + File.separator + "layout";
    }

    public static String valuesDir(String scId) {
        return root(scId) + File.separator + "files" + File.separator + "resource" + File.separator + "values";
    }

    public static String valuesNightDir(String scId) {
        return root(scId) + File.separator + "files" + File.separator + "resource" + File.separator + "values-night";
    }

    public static String manifestFile(String scId) {
        return root(scId) + File.separator + "files" + File.separator + "resource" + File.separator + "AndroidManifest.xml";
    }

    public static String packagePath(String scId) {
        HashMap<String, Object> metadata = lC.b(scId);
        String pkg = (metadata != null) ? (String) metadata.get("my_sc_pkg_name") : "com.my.app";
        if (pkg == null) pkg = "com.my.app";
        return javaDir(scId) + File.separator + pkg.replace(".", File.separator);
    }

    private ProjectPaths() {}
}
