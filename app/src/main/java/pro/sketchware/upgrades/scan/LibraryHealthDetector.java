package pro.sketchware.upgrades.scan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import pro.sketchware.upgrades.model.Finding;

/**
 * WHAT: LibraryHealthDetector - Checks for legacy support libraries and recommends AndroidX alternatives.
 * (عربي) مكتشف صحة المكتبات - يبحث عن مكتبات الدعم القديمة ويوصي ببدائل AndroidX.
 */
public class LibraryHealthDetector {

    private static final Map<String, String> MIGRATION_MAP = new HashMap<>();
    static {
        MIGRATION_MAP.put("appcompat-v7", "androidx.appcompat:appcompat");
        MIGRATION_MAP.put("design", "com.google.android.material:material");
        MIGRATION_MAP.put("support-v4", "androidx.legacy:legacy-support-v4");
        MIGRATION_MAP.put("recyclerview-v7", "androidx.recyclerview:recyclerview");
        MIGRATION_MAP.put("cardview-v7", "androidx.cardview:cardview");
    }

    public List<Finding> scan(String scId) {
        List<Finding> findings = new ArrayList<>();
        ArrayList<HashMap<String, Object>> projectLibs = LocalLibrariesUtil.getLocalLibraries(scId);
        
        for (HashMap<String, Object> lib : projectLibs) {
            String name = (String) lib.get("name");
            if (name == null) continue;

            for (String legacy : MIGRATION_MAP.keySet()) {
                if (name.contains(legacy)) {
                    findings.add(new Finding(
                        "legacy_lib_" + name,
                        Finding.Category.LIBRARY_HEALTH,
                        "Legacy Library Found: " + name,
                        "Recommeded alternative: " + MIGRATION_MAP.get(legacy),
                        List.of("local_library configuration"),
                        false,
                        "Replace with AndroidX version in Library Manager"
                    ));
                    break;
                }
            }
        }
        return findings;
    }
}
