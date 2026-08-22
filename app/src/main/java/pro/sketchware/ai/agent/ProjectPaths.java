package pro.sketchware.ai.agent;

import java.io.File;
import java.util.HashMap;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.FilePathUtil;
import mod.hilal.saif.activities.tools.ConfigActivity;
import a.a.a.wq;
import a.a.a.lC;
import a.a.a.yq;
import pro.sketchware.SketchApplication;

/**
 * single source of truth; delegates ONLY to audited methods
 */
public final class ProjectPaths {

    public static String dataRoot(String scId) {
        return wq.b(scId);
    }

    public static String resourceRoot(String scId) {
        return new FilePathUtil().getPathResource(scId);
    }

    public static String layoutDir(String scId) {
        return new yq(SketchApplication.getContext(), scId).layoutFilesPath;
    }

    public static String valuesDir(String scId) {
        return new yq(SketchApplication.getContext(), scId).resDirectoryPath + File.separator + "values";
    }

    public static String valuesNightDir(String scId) {
        return new yq(SketchApplication.getContext(), scId).resDirectoryPath + File.separator + "values-night";
    }

    public static String drawableDir(String scId) {
        return new yq(SketchApplication.getContext(), scId).resDirectoryPath + File.separator + "drawable";
    }

    public static String drawableXhdpiDir(String scId) {
        return new yq(SketchApplication.getContext(), scId).resDirectoryPath + File.separator + "drawable-xhdpi";
    }

    public static String animDir(String scId) {
        return new yq(SketchApplication.getContext(), scId).resDirectoryPath + File.separator + "anim";
    }

    public static String menuDir(String scId) {
        return new yq(SketchApplication.getContext(), scId).resDirectoryPath + File.separator + "menu";
    }

    public static String assetsRoot(String scId) {
        return new FilePathUtil().getPathAssets(scId);
    }

    public static String javaManagerRoot(String scId) {
        return new FilePathUtil().getPathJava(scId);
    }

    public static String projectJavaRoot(String scId) {
        return new yq(SketchApplication.getContext(), scId).javaFilesPath;
    }

    public static String iconsRoot(String scId) {
        return new yq(SketchApplication.getContext(), scId).resDirectoryPath + File.separator + "mipmap-xhdpi";
    }

    public static String blocksRoot(String scId) {
        return FileUtil.getExternalStorageDir() + ConfigActivity.getStringSettingValueOrSetAndGet(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH,
                (String) ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH));
    }

    public static String paletteRoot(String scId) {
        return FileUtil.getExternalStorageDir() + ConfigActivity.getStringSettingValueOrSetAndGet(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH,
                (String) ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH));
    }

    public static String manifestFile(String scId) {
        return new yq(SketchApplication.getContext(), scId).androidManifestPath;
    }

    public static String packagePath(String scId) {
        HashMap<String, Object> metadata = lC.b(scId);
        String pkg = (metadata != null) ? (String) metadata.get("my_sc_pkg_name") : "com.my.app";
        if (pkg == null) pkg = "com.my.app";
        return projectJavaRoot(scId) + File.separator + pkg.replace(".", File.separator);
    }

    public static String manifestInjectionPath(String scId) {
        return dataRoot(scId) + File.separator + "Injection" + File.separator + "androidmanifest" + File.separator + "attributes.json";
    }

    public static String gradleFiles(String scId) {
        return dataRoot(scId);
    }

    private ProjectPaths() {}
}
