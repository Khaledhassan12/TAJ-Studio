package pro.sketchware.ai.agent.tools;

import java.io.File;
import java.util.HashMap;
import a.a.a.lC;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

public final class JavaToolHelper {

    public static String getManagerPath(String sc_id) {
        return ProjectPaths.javaManagerRoot(sc_id);
    }

    public static String getProjectPath(String sc_id) {
        return ProjectPaths.projectJavaRoot(sc_id);
    }

    public static String getLayoutPath(String sc_id) {
        return ProjectPaths.layoutDir(sc_id);
    }

    public static String getValuesPath(String sc_id) {
        return ProjectPaths.valuesDir(sc_id);
    }

    public static String getProjectPackagePath(String sc_id) {
        return ProjectPaths.packagePath(sc_id);
    }

    public static String getPackageName(String sc_id) {
        String verified = getVerifiedPackageName(sc_id);
        return verified != null ? verified : "com.my.app";
    }

    /**
     * Returns the package stored for the currently selected project, without silently
     * substituting a sample/default package when project metadata is unavailable.
     */
    public static String getVerifiedPackageName(String sc_id) {
        HashMap<String, Object> metadata = lC.b(sc_id);
        if (metadata == null) return null;
        Object value = metadata.get("my_sc_pkg_name");
        if (!(value instanceof String)) return null;
        String pkg = ((String) value).trim();
        if (pkg.isEmpty() || !pkg.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")) return null;
        return pkg;
    }

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
}
