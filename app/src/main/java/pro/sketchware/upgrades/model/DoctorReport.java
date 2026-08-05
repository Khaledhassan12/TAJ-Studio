package pro.sketchware.upgrades.model;

import java.util.List;

/**
 * WHAT: DoctorReport - Contains all findings from a project "health check".
 * (عربي) تقرير الطبيب - يحتوي على جميع الاكتشافات الناتجة عن "فحص الصحة" للمشروع.
 */
public class DoctorReport {

    public final String scId;
    public final List<Finding> findings;
    public final long scanTimeMs;

    public DoctorReport(String scId, List<Finding> findings, long scanTimeMs) {
        this.scId = scId;
        this.findings = findings;
        this.scanTimeMs = scanTimeMs;
    }

    public int getCount(Finding.Category category) {
        int count = 0;
        for (Finding f : findings) {
            if (f.category == category) count++;
        }
        return count;
    }

    public boolean isEmpty() {
        return findings.isEmpty();
    }
}
