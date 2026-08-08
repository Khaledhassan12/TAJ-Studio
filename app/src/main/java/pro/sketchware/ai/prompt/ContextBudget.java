package pro.sketchware.ai.prompt;

/**
 * [WHAT] Manages token budget for system prompts and context.
 * [WHY] Prevents model overflow on small local models or large project data.
 * [HOW] Reserves space for completion and calculates remaining for context.
 */
public class ContextBudget {
    private final int totalSize;
    private final int reservedForCompletion = 1024;
    private final int reservedForHistory = 2048;

    public ContextBudget(int totalSize) {
        this.totalSize = totalSize;
    }

    public int getSystemLimit() {
        // Reserve at least 25% for system, but no more than 4000 tokens for P4
        return Math.min(4000, totalSize / 4);
    }

    public int getContextLimit() {
        int remaining = totalSize - reservedForCompletion - reservedForHistory - getSystemLimit();
        return Math.max(0, remaining);
    }
}
