package pro.sketchware.ai.data;

import android.os.Environment;
import java.io.File;

/**
 * [WHAT] Central path management for AI files.
 * [WHY] Ensures consistent directory structure for models and project data.
 * [HOW] Pure static methods that auto-create directories lazily.
 *
 * [العربية]
 * إدارة المسارات المركزية لملفات الذكاء الاصطناعي.
 * تضمن هيكلية مجلدات متناسقة للموديلات وبيانات المشاريع.
 */
public class Paths {

    public static File base() {
        File f = new File(Environment.getExternalStorageDirectory(), ".sketchware/ai");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File modelsDir() {
        File f = new File(base(), "models");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File projectsDir() {
        File f = new File(base(), "projects");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File projectDir(String scId) {
        File f = new File(projectsDir(), scId);
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File modelFile(String id) {
        return new File(modelsDir(), id + ".gguf");
    }

    public static File tempDownloadFile(String id) {
        return new File(modelsDir(), id + ".part");
    }
}
