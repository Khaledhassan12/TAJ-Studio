package pro.sketchware.ai.generation;

import android.content.Context;
import android.text.TextUtils;
import pro.sketchware.ai.data.AiStorage;
import java.util.ArrayList;
import java.util.List;

/**
 * [WHAT] Manager for global generation defaults.
 * [WHY] Centralizes context window, thinking effort, and sampling parameters.
 * [HOW] Persists to AiStorage kv table; handles nulls as "unspecified".
 */
public class GenerationDefaults {

    private static GenerationDefaults instance;
    private final AiStorage storage;

    public static synchronized GenerationDefaults get(Context context) {
        if (instance == null) instance = new GenerationDefaults(context.getApplicationContext());
        return instance;
    }

    private GenerationDefaults(Context context) {
        this.storage = AiStorage.get(context);
    }

    // --- Context Window ---
    public int getContextWindow() {
        String val = storage.kvGet("gen_ctx_window");
        return TextUtils.isEmpty(val) ? 20 : Integer.parseInt(val);
    }
    public void setContextWindow(int n) { storage.kvPut("gen_ctx_window", String.valueOf(n)); }

    public boolean isVisualizeRollout() {
        return "true".equals(storage.kvGet("gen_visualize_rollout"));
    }
    public void setVisualizeRollout(boolean b) { storage.kvPut("gen_visualize_rollout", String.valueOf(b)); }

    // --- Thinking (P1-O: single enum SSOT; SYSTEM INSTRUCTION §5/§6 mutual exclusion) ---
    // thinkingMode: NONE | EFFORT | BUDGET  (kv "gen_thinking_mode")
    // EFFORT  => send reasoning_effort (openai)
    // BUDGET  => send thinking budget (anthropic/gemini)
    // NONE    => omit both
    public static final String THINKING_NONE = "NONE";
    public static final String THINKING_EFFORT = "EFFORT";
    public static final String THINKING_BUDGET = "BUDGET";

    public String getThinkingMode() {
        String val = storage.kvGet("gen_thinking_mode");
        return TextUtils.isEmpty(val) ? THINKING_EFFORT : val; // default EFFORT (preserves prior behavior)
    }
    public void setThinkingMode(String mode) { storage.kvPut("gen_thinking_mode", mode); }

    // --- Backward-compatible derived getters (P1-O: derive from single enum) ---
    /** @return true when thinkingMode == EFFORT */
    public boolean isThinkingEnabled() { return THINKING_EFFORT.equals(getThinkingMode()); }
    /** @return true when thinkingMode == BUDGET */
    public boolean isThinkingBudgetEnabled() { return THINKING_BUDGET.equals(getThinkingMode()); }

    public int getThinkingEffort() {
        String val = storage.kvGet("gen_thinking_effort");
        return TextUtils.isEmpty(val) ? 1 : Integer.parseInt(val); // 0=Low, 1=Medium, 2=High, 3=xHigh
    }
    public void setThinkingEffort(int e) { storage.kvPut("gen_thinking_effort", String.valueOf(e)); }

    public int getThinkingBudget() {
        String val = storage.kvGet("gen_thinking_budget");
        return TextUtils.isEmpty(val) ? 4096 : Integer.parseInt(val);
    }
    public void setThinkingBudget(int b) { storage.kvPut("gen_thinking_budget", String.valueOf(b)); }

    // --- Service Tier ---
    public boolean isServiceTierEnabled() {
        return "true".equals(storage.kvGet("gen_service_tier_enabled"));
    }
    public void setServiceTierEnabled(boolean b) { storage.kvPut("gen_service_tier_enabled", String.valueOf(b)); }

    public int getServiceTier() {
        String val = storage.kvGet("gen_service_tier");
        return TextUtils.isEmpty(val) ? 0 : Integer.parseInt(val); // 0=Auto, 1=Default, 2=Flex, 3=Fast
    }
    public void setServiceTier(int t) { storage.kvPut("gen_service_tier", String.valueOf(t)); }

    // --- Sampling Params ---
    public Float getTemperature() { return getFloat("gen_temperature"); }
    public void setTemperature(Float f) { setFloat("gen_temperature", f); }

    public Integer getMaxTokens() { return getInt("gen_max_tokens"); }
    public void setMaxTokens(Integer i) { setInt("gen_max_tokens", i); }

    public Float getTopP() { return getFloat("gen_top_p"); }
    public void setTopP(Float f) { setFloat("gen_top_p", f); }

    public Float getFreqPenalty() { return getFloat("gen_freq_penalty"); }
    public void setFreqPenalty(Float f) { setFloat("gen_freq_penalty", f); }

    public Float getPresPenalty() { return getFloat("gen_pres_penalty"); }
    public void setPresPenalty(Float f) { setFloat("gen_pres_penalty", f); }

    // --- Helpers ---
    private Float getFloat(String key) {
        String val = storage.kvGet(key);
        return TextUtils.isEmpty(val) ? null : Float.parseFloat(val);
    }
    private void setFloat(String key, Float f) {
        storage.kvPut(key, f == null ? "" : String.valueOf(f));
    }
    private Integer getInt(String key) {
        String val = storage.kvGet(key);
        return TextUtils.isEmpty(val) ? null : Integer.parseInt(val);
    }
    private void setInt(String key, Integer i) {
        storage.kvPut(key, i == null ? "" : String.valueOf(i));
    }
}
