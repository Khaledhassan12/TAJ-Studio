package pro.sketchware.ai.context;

import java.util.HashMap;
import a.a.a.lC;
import a.a.a.yB;

/**
 * [WHAT] Gathers real project evidence for AI context.
 * [WHY] Provides the model with grounded facts about the current work (R6).
 * [HOW] Reads metadata from Sketchware data structures.
 */
public class ProjectContextManager {

    public static ContextSnapshot snapshot(String scId) {
        HashMap<String, Object> metadata = lC.b(scId);
        if (metadata == null) return new ContextSnapshot("unknown", "unknown", null, null, null, null, null);

        String projectName = yB.c(metadata, "my_ws_name");
        String packageName = yB.c(metadata, "my_pkg_name");
        String minSdk = yB.c(metadata, "min_sdk");
        String targetSdk = yB.c(metadata, "target_sdk");

        // Bounded file tree summary (placeholders for now)
        String structure = "Java/Res/Assets counts omitted in P4 skeleton";
        String build = "unknown";

        return new ContextSnapshot(projectName, packageName, minSdk, targetSdk, structure, build, null);
    }
}
