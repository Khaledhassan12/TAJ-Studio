package pro.sketchware.upgrades;

import java.util.ArrayList;
import java.util.List;

/**
 * A comprehensive report of available upgrades for a specific project.
 * (عربي) تقرير شامل للترقيات المتاحة لمشروع معين.
 */
public class UpgradeReport {

    public final String scId;
    public final String appName;
    public final String packageName;
    public final String age;
    public final long lastModified;
    public final boolean hasCustomIcon;
    public final List<UpgradeItem> items = new ArrayList<>();

    // G7: Technical details
    public int storedVer;
    public int latestVer;
    public boolean androidxOn;
    public int minSdk;
    public int libsCount;

    public UpgradeReport(String scId, String appName, String packageName, String age, long lastModified, boolean hasCustomIcon) {
        this.scId = scId;
        this.appName = appName;
        this.packageName = packageName;
        this.age = age;
        this.lastModified = lastModified;
        this.hasCustomIcon = hasCustomIcon;
    }

    public boolean isUpToDate() {
        for (UpgradeItem item : items) {
            if (item.status == UpgradeItem.Status.UPGRADABLE) {
                return false;
            }
        }
        return true;
    }

    public int getUpgradableCount() {
        int count = 0;
        for (UpgradeItem item : items) {
            if (item.status == UpgradeItem.Status.UPGRADABLE) {
                count++;
            }
        }
        return count;
    }
}
