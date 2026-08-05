package pro.sketchware.marketplace.models;

import java.util.ArrayList;
import java.util.List;

/**
 * نموذج بيانات موسّع لمكتبات المتجر - يشمل الفئة، رابط GitHub، التراخيص، ودعم Compose.
 * Extended data model for marketplace libraries - includes category, GitHub URL, licenses, and Compose support.
 */
public class MarketplaceLibrary {
    private final String id;
    private final String displayName;
    private final String summary;
    private final String description;
    private String stableVersion;
    private String coordinate;
    private final String docUrl;
    private final boolean androidx;
    private final String downloadUrl;
    private final Integer downloads;
    private final String usageSnippet;
    private final List<String> conflictsWith;
    private final boolean mostUsed;
    private final String iconUrl;
    private final int iconRes;

    // New Fields
    private final String category;
    private final String githubUrl;
    private final String license;
    private final int minSdk;
    private final boolean kotlinSupport;
    private final boolean javaSupport;
    private final boolean composeSupport;
    private final int popularity;
    private final boolean maintained;
    private final String lastUpdate;
    private final List<String> dependencies;
    private final String sampleCode;

    public MarketplaceLibrary(String id, String displayName, String summary, String description,
                              String stableVersion, String coordinate, String docUrl, boolean androidx,
                              String downloadUrl, Integer downloads, String usageSnippet,
                              List<String> conflictsWith, boolean mostUsed, String iconUrl, int iconRes,
                              String category, String githubUrl, String license, int minSdk,
                              boolean kotlinSupport, boolean javaSupport, boolean composeSupport,
                              int popularity, boolean maintained, String lastUpdate,
                              List<String> dependencies, String sampleCode) {
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
        this.iconUrl = iconUrl;
        this.iconRes = iconRes;
        
        this.category = category;
        this.githubUrl = githubUrl;
        this.license = license;
        this.minSdk = minSdk;
        this.kotlinSupport = kotlinSupport;
        this.javaSupport = javaSupport;
        this.composeSupport = composeSupport;
        this.popularity = popularity;
        this.maintained = maintained;
        this.lastUpdate = lastUpdate;
        this.dependencies = dependencies != null ? dependencies : new ArrayList<>();
        this.sampleCode = sampleCode;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getSummary() { return summary; }
    public String getDescription() { return description; }
    public String getStableVersion() { return stableVersion; }
    
    // WHAT: Setter for dynamic version switching.
    // HOW: Updating the stableVersion field to reflect user choice from Picker.
    public void setStableVersion(String stableVersion) { this.stableVersion = stableVersion; }
    
    public String getCoordinate() { return coordinate; }
    
    // WHAT: Setter for updating coordinates with new version.
    // HOW: Replacing the version part of the group:artifact:version string.
    public void setCoordinate(String coordinate) { this.coordinate = coordinate; }
    public String getDocUrl() { return docUrl; }
    public boolean isAndroidx() { return androidx; }
    public String getDownloadUrl() { return downloadUrl; }
    public Integer getDownloads() { return downloads; }
    public String getUsageSnippet() { return usageSnippet; }
    public List<String> getConflictsWith() { return conflictsWith; }
    public boolean isMostUsed() { return mostUsed; }
    public String getIconUrl() { return iconUrl; }
    public int getIconRes() { return iconRes; }

    public String getCategory() { return category; }
    public String getGithubUrl() { return githubUrl; }
    public String getLicense() { return license; }
    public int getMinSdk() { return minSdk; }
    public boolean isKotlinSupport() { return kotlinSupport; }
    public boolean isJavaSupport() { return javaSupport; }
    public boolean isComposeSupport() { return composeSupport; }
    public int getPopularity() { return popularity; }
    public boolean isMaintained() { return maintained; }
    public String getLastUpdate() { return lastUpdate; }
    public List<String> getDependencies() { return dependencies; }
    public String getSampleCode() { return sampleCode; }
}
