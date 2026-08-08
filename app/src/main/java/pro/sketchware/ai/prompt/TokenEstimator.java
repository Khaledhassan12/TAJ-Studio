package pro.sketchware.ai.prompt;

/**
 * [WHAT] Rough estimator for token counts.
 * [WHY] Needed for budgeting context without a real tokenizer in P4.
 * [HOW] Conservative heuristic: chars / 4. 
 * NOTE: This is an ESTIMATE, not exact.
 */
public class TokenEstimator {
    public static int estimate(String text) {
        if (text == null) return 0;
        return (int) Math.ceil(text.length() / 4.0);
    }
}
