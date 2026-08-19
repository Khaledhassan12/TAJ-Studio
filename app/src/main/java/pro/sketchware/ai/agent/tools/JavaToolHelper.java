package pro.sketchware.ai.agent.tools;

import java.io.File;
import java.util.HashMap;
import a.a.a.yq;
import a.a.a.lC;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

public final class JavaToolHelper {

    public static String getManagerPath(String sc_id) {
        return new FilePathUtil().getPathJava(sc_id);
    }

    public static String getProjectPath(String sc_id) {
        return a.a.a.wq.d(sc_id) + java.io.File.separator + "app/src/main/java";
    }

    public static String getProjectPackagePath(String sc_id) {
        HashMap<String, Object> metadata = lC.b(sc_id);
        if (metadata == null) return getProjectPath(sc_id);
        
        yq project = new yq(null, a.a.a.wq.d(sc_id), metadata);
        return project.javaFilesPath + java.io.File.separator + project.packageNameAsFolders;
    }

    public static String getPackageName(String sc_id) {
        HashMap<String, Object> metadata = lC.b(sc_id);
        if (metadata == null) return "com.my.app";
        return (String) metadata.get("my_sc_pkg_name");
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
