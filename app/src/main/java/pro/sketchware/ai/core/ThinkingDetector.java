package pro.sketchware.ai.core;

import java.util.regex.Pattern;

public final class ThinkingDetector {

    private static final Pattern HINT_PATTERN = Pattern.compile(
            "(o1|o3|o4|reasoner|reasoning|qwq|thinking|deepseek-r1|gemini-2\\.5|glm-4\\.5|claude.*(sonnet-4|opus-4|3-7))",
            Pattern.CASE_INSENSITIVE);

    public static boolean hint(String modelId) {
        if (modelId == null) return false;
        return HINT_PATTERN.matcher(modelId).find();
    }

    private ThinkingDetector() {}
}
