package pro.sketchware.ai.context;

/**
 * [WHAT] Snapshot of project facts for AI context.
 */
public class ContextSnapshot {
    public final String projectName;
    public final String packageName;
    public final String minSdk;
    public final String targetSdk;
    public final String fileTreeSummary;
    public final String lastBuildStatus;
    public final String recentConversationExcerpt;

    public ContextSnapshot(String projectName, String packageName, String minSdk, String targetSdk, String fileTreeSummary, String lastBuildStatus, String recentConversationExcerpt) {
        this.projectName = projectName;
        this.packageName = packageName;
        this.minSdk = minSdk;
        this.targetSdk = targetSdk;
        this.fileTreeSummary = fileTreeSummary;
        this.lastBuildStatus = lastBuildStatus;
        this.recentConversationExcerpt = recentConversationExcerpt;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(projectName != null ? projectName : "unknown").append("\n");
        sb.append("Package: ").append(packageName != null ? packageName : "unknown").append("\n");
        sb.append("MinSDK: ").append(minSdk != null ? minSdk : "unknown").append("\n");
        sb.append("TargetSDK: ").append(targetSdk != null ? targetSdk : "unknown").append("\n");
        if (fileTreeSummary != null) sb.append("Structure: ").append(fileTreeSummary).append("\n");
        if (lastBuildStatus != null) sb.append("Last Build: ").append(lastBuildStatus).append("\n");
        if (recentConversationExcerpt != null) sb.append("Recent Chat: ").append(recentConversationExcerpt).append("\n");
        return sb.toString();
    }
}
