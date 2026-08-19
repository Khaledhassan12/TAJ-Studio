package pro.sketchware.upgrades.model;

import java.util.List;

/**
 * WHAT: Finding - Represents a specific issue or recommendation found during a project scan.
 * (عربي) الاكتشاف - يمثل مشكلة محددة أو توصية تم العثور عليها أثناء فحص المشروع.
 */
public class Finding {

    public enum Category {
        UNUSED_RESOURCE,
        DUPLICATE_FILE,
        EMPTY_ARTIFACT,
        BUILD_ARTIFACT,
        UNUSED_IMPORT,
        LIBRARY_HEALTH,
        UPGRADE
    }

    public final String id;
    public final Category category;
    public final String title;
    public final String detail;
    public final List<String> paths;
    public final boolean autoFixable;
    public final String fixDescription;

    public Finding(String id, Category category, String title, String detail, List<String> paths, boolean autoFixable, String fixDescription) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.detail = detail;
        this.paths = paths;
        this.autoFixable = autoFixable;
        this.fixDescription = fixDescription;
    }
}
