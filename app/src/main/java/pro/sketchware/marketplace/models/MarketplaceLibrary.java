package pro.sketchware.marketplace.models;

import java.util.ArrayList;
import java.util.List;

/**
 * نموذج بيانات لمكتبات المتجر.
 * Data model for marketplace libraries.
 */
public class MarketplaceLibrary {
    private final String id;
    private final String displayName;
    private final String summary;
    private final String description;
    private final String stableVersion;
    private final String coordinate;
    private final String docUrl;
    private final boolean androidx;
    private final String downloadUrl;
    private final Integer downloads;
    private final String usageSnippet;
    private final List<String> conflictsWith;
    private final boolean mostUsed;

    public MarketplaceLibrary(String id, String displayName, String summary, String description,
                              String stableVersion, String coordinate, String docUrl, boolean androidx,
                              String downloadUrl, Integer downloads, String usageSnippet,
                              List<String> conflictsWith, boolean mostUsed) {
        this.id = id;
        this.displayName = displayName;
        this.summary = summary;
        this.description = description;
        this.stableVersion = stableVersion;
        this.coordinate = coordinate;
        this.docUrl = docUrl;
        this.androidx = androidx;
        this.downloadUrl = downloadUrl;
        this.downloads = downloads;
        this.usageSnippet = usageSnippet;
        this.conflictsWith = conflictsWith != null ? conflictsWith : new ArrayList<>();
        this.mostUsed = mostUsed;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getSummary() { return summary; }
    public String getDescription() { return description; }
    public String getStableVersion() { return stableVersion; }
    public String getCoordinate() { return coordinate; }
    public String getDocUrl() { return docUrl; }
    public boolean isAndroidx() { return androidx; }
    public String getDownloadUrl() { return downloadUrl; }
    public Integer getDownloads() { return downloads; }
    public String getUsageSnippet() { return usageSnippet; }
    public List<String> getConflictsWith() { return conflictsWith; }
    public boolean isMostUsed() { return mostUsed; }
}
