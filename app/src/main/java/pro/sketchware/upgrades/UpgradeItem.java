package pro.sketchware.upgrades;

/**
 * Represents a single upgradeable item for a project.
 * (عربي) يمثل عنصر ترقية واحد لمشروع معين.
 */
public class UpgradeItem {

    public enum Type {
        ANDROIDX,
        GRADLE_CONFIG,
        LIBRARIES,
        MANIFEST,
        PERMISSIONS
    }

    public enum Status {
        UP_TO_DATE,
        UPGRADABLE,
        RECOMMENDED_MANUAL
    }

    public final Type type;
    public final Status status;
    public final String title;
    public final String description;

    public UpgradeItem(Type type, Status status, String title, String description) {
        this.type = type;
        this.status = status;
        this.title = title;
        this.description = description;
    }
}
