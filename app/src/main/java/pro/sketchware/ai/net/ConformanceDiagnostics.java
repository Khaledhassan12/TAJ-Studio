package pro.sketchware.ai.net;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.config.ProviderCatalog;
import pro.sketchware.ai.core.ProviderProfile;
import pro.sketchware.ai.core.Protocol;

/**
 * Performs a local, static inspection of the provider catalog.
 * This deliberately does not claim to validate credentials or network access;
 * use the live connection test for those checks.
 */
public final class ConformanceDiagnostics {
    private static final String TAG = "AIConformance";

    private ConformanceDiagnostics() {}

    public static String runReport() {
        StringBuilder report = new StringBuilder();
        int passed = 0;
        int warnings = 0;
        List<ProviderProfile> profiles = ProviderCatalog.builtInProfiles();

        report.append("PROVIDER CONFIGURATION AUDIT (STATIC)\n");
        report.append("Profiles inspected: ").append(profiles.size()).append('\n');
        report.append("No network requests or credential validation are performed here.\n\n");

        for (ProviderProfile profile : profiles) {
            List<String> issues = new ArrayList<>();
            if (profile == null) {
                warnings++;
                report.append("[WARN] Null provider profile found in catalog.\n\n");
                continue;
            }

            if (profile.id == null || profile.id.trim().isEmpty()) {
                issues.add("Provider ID is empty.");
            }
            if (profile.displayName == null || profile.displayName.trim().isEmpty()) {
                issues.add("Display name is empty.");
            }
            if (profile.protocol == null) {
                issues.add("Protocol is not configured.");
            }

            String base = profile.defaultBaseUrl == null ? "" : profile.defaultBaseUrl.trim();
            if (base.isEmpty()) {
                if (!profile.baseUrlEditable) {
                    issues.add("Base URL is empty and cannot be configured by the user.");
                }
            } else if (!(base.startsWith("https://") || base.startsWith("http://"))) {
                issues.add("Default base URL does not use HTTP or HTTPS.");
            }

            report.append(issues.isEmpty() ? "[OK] " : "[WARN] ")
                    .append(profile.displayName == null || profile.displayName.trim().isEmpty()
                            ? "(unnamed provider)" : profile.displayName)
                    .append(" (").append(profile.id == null ? "no-id" : profile.id).append(")\n");

            report.append("  Protocol: ").append(profile.protocol == null ? "UNSET" : profile.protocol.name()).append('\n');
            report.append("  Base URL: ").append(base.isEmpty()
                    ? (profile.baseUrlEditable ? "user-configurable" : "missing")
                    : base).append('\n');
            report.append("  Authentication: ").append(authDescription(profile)).append('\n');
            report.append("  Models endpoint: ").append(modelsPath(profile)).append('\n');
            report.append("  Suggested models: ").append(profile.defaultModelSuggestions.length).append('\n');

            if (issues.isEmpty()) {
                passed++;
                report.append("  Result: profile fields passed static checks.\n");
            } else {
                warnings++;
                for (String issue : issues) report.append("  Issue: ").append(issue).append('\n');
            }
            report.append('\n');
        }

        report.append("Summary: ").append(passed).append(" profile(s) passed; ")
                .append(warnings).append(" profile(s) need review.\n");
        report.append("For live authentication and reachability, use Test Connection in full settings.");

        String result = report.toString();
        Log.i(TAG, "Static provider configuration audit completed: "
                + passed + " passed, " + warnings + " warnings");
        return result;
    }

    private static String authDescription(ProviderProfile profile) {
        if (profile.protocol == Protocol.ANTHROPIC) return "x-api-key + anthropic-version";
        if (profile.protocol == Protocol.GEMINI) return "x-goog-api-key";
        if (profile.skipAuth) return "No Authorization header";
        if (profile.apiKeyHeaderAuth) return "api-key header";
        return profile.requiresKey ? "Authorization: Bearer" : "Optional Bearer token";
    }

    private static String modelsPath(ProviderProfile profile) {
        if (profile.apiKeyHeaderAuth) return "not universally available (deployment-specific)";
        if (profile.protocol == Protocol.ANTHROPIC) return "/v1/models";
        if (profile.protocol == Protocol.GEMINI) return "/v1beta/models";
        return "/models";
    }
}
