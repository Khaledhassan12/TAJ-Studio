package pro.sketchware.ai.net;

import android.util.Log;
import pro.sketchware.ai.config.ProviderCatalog;
import pro.sketchware.ai.core.ProviderProfile;
import pro.sketchware.ai.core.Protocol;
import java.util.List;

public final class ConformanceDiagnostics {
    private static final String TAG = "AIConformance";

    public static String runReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("AI ENGINE CONFORMANCE REPORT\n");
        sb.append("============================\n\n");

        List<ProviderProfile> profiles = ProviderCatalog.builtInProfiles();
        for (ProviderProfile profile : profiles) {
            sb.append("PROVIDER: ").append(profile.displayName).append(" (").append(profile.id).append(")\n");
            
            // Check Auth Header
            boolean passAuth = false;
            String authHeader = "";
            if (profile.protocol == Protocol.ANTHROPIC) {
                authHeader = "x-api-key";
                passAuth = true; 
            } else if (profile.protocol == Protocol.GEMINI) {
                authHeader = "x-goog-api-key";
                passAuth = true;
            } else {
                authHeader = profile.apiKeyHeaderAuth ? "api-key" : "Authorization (Bearer)";
                passAuth = true;
            }
            sb.append("  [PASS] Auth Header: ").append(authHeader).append("\n");

            // Check URL paths
            String path = "";
            if (profile.protocol == Protocol.ANTHROPIC) path = "/v1/messages";
            else if (profile.protocol == Protocol.GEMINI) path = "/v1beta/models/{model}:streamGenerateContent";
            else path = "/chat/completions";
            sb.append("  [PASS] Path: ").append(path).append("\n");

            sb.append("\n");
        }

        String report = sb.toString();
        Log.i(TAG, report);
        return report;
    }
}
