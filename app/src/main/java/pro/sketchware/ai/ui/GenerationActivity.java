package pro.sketchware.ai.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import pro.sketchware.R;
import pro.sketchware.ai.generation.GenerationDefaults;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import java.util.Locale;

/**
 * [WHAT] Activity for managing global generation defaults.
 * [WHY] P2-1: Refactored to support Dynamic Colors (Monet) using Theme Attributes.
 * [HOW] Single source of truth (GenerationDefaults) drives all UI states.
 *   Colors resolved via getThemeColor(R.attr.*) for 100% theme coherence.
 */
public class GenerationActivity extends BaseAppCompatActivity {

    private GenerationDefaults defaults;
    private boolean isAdvancedThinkingVisible = false;

    // S1
    private TajSlider sliderCtxWindow;
    private TextView tvCtxWindowSub;
    private TajSwitch switchRollout;

    // S2
    private TajSwitch switchThinking;
    private TextView tvThinkingSub;
    private View cardThinkingEffortVal;
    private TajSlider sliderThinkingEffort;
    private TextView tvThinkingEffortVal;
    private TextView btnThinkingAdvanced;
    private LinearLayout containerThinkingAdvanced;
    private TajSwitch switchThinkingBudget;
    private View cardThinkingBudgetVal;
    private TajSlider sliderThinkingBudget;
    private TextView tvThinkingBudgetVal;

    // S3
    private TajSwitch switchServiceTier;
    private TextView tvServiceTierSub;
    private View cardServiceTierVal;
    private TajSlider sliderServiceTier;
    private TextView tvServiceTierVal;

    // S4
    private TajSlider sliderTemp;
    private TajSlider sliderMaxTokens;
    private TajSlider sliderTopP;
    private TajSlider sliderFreqPenalty;
    private TajSlider sliderPresPenalty;
    private TextView tvTempVal;
    private TextView tvMaxTokensVal;
    private TextView tvTopPVal;
    private TextView tvFreqPenaltyVal;
    private TextView tvPresPenaltyVal;
    private View btnResetTemp;
    private View btnResetMaxTokens;
    private View btnResetTopP;
    private View btnResetFreqPenalty;
    private View btnResetPresPenalty;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generation);

        AiHeaderInsets.apply(findViewById(R.id.app_bar));

        defaults = GenerationDefaults.get(this);
        initUi();
        applyGenerationScreen();
        // BaseAppCompatActivity.handleInsetts for navigation bars
        super.handleInsetts(findViewById(R.id.root_container));
    }

    @Override
    public void onResume() {
        super.onResume();
        applyGenerationScreen();
    }

    private void initUi() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_docs).setOnClickListener(v -> 
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/docs/models")), "Documentation"))
        );

        // S1
        sliderCtxWindow = findViewById(R.id.slider_ctx_window);
        sliderCtxWindow.setRange(5f, 50f);
        sliderCtxWindow.setStops(new float[]{5f, 10f, 20f, 30f, 50f});
        tvCtxWindowSub = findViewById(R.id.tv_ctx_window_sub);
        switchRollout = findViewById(R.id.switch_rollout);

        // S2
        switchThinking = findViewById(R.id.switch_thinking);
        tvThinkingSub = findViewById(R.id.tv_thinking_sub);
        cardThinkingEffortVal = findViewById(R.id.card_thinking_effort_val);
        sliderThinkingEffort = findViewById(R.id.slider_thinking_effort);
        sliderThinkingEffort.setRange(0f, 3f);
        sliderThinkingEffort.setStops(new float[]{0f, 1f, 2f, 3f});
        tvThinkingEffortVal = findViewById(R.id.tv_thinking_effort_val);
        btnThinkingAdvanced = findViewById(R.id.btn_thinking_advanced);
        containerThinkingAdvanced = findViewById(R.id.container_thinking_advanced);
        switchThinkingBudget = findViewById(R.id.switch_thinking_budget);
        cardThinkingBudgetVal = findViewById(R.id.card_thinking_budget_val);
        sliderThinkingBudget = findViewById(R.id.slider_thinking_budget);
        sliderThinkingBudget.setRange(1024f, 16384f);
        sliderThinkingBudget.setStops(new float[]{1024f, 2048f, 4096f, 8192f, 16384f});
        tvThinkingBudgetVal = findViewById(R.id.tv_thinking_budget_val);

        // S3
        switchServiceTier = findViewById(R.id.switch_service_tier);
        tvServiceTierSub = findViewById(R.id.tv_service_tier_sub);
        cardServiceTierVal = findViewById(R.id.card_service_tier_val);
        sliderServiceTier = findViewById(R.id.slider_service_tier);
        sliderServiceTier.setRange(0f, 3f);
        sliderServiceTier.setStops(new float[]{0f, 1f, 2f, 3f});
        tvServiceTierVal = findViewById(R.id.tv_service_tier_val);

        // S4
        sliderTemp = findViewById(R.id.slider_temp); sliderTemp.setRange(0f, 2f);
        tvTempVal = findViewById(R.id.tv_temp_val); btnResetTemp = findViewById(R.id.btn_reset_temp);

        sliderMaxTokens = findViewById(R.id.slider_max_tokens);
        sliderMaxTokens.setRange(256f, 16384f);
        sliderMaxTokens.setStops(new float[]{256f, 512f, 1024f, 2048f, 4096f, 8192f, 16384f});
        tvMaxTokensVal = findViewById(R.id.tv_max_tokens_val); btnResetMaxTokens = findViewById(R.id.btn_reset_max_tokens);

        sliderTopP = findViewById(R.id.slider_top_p); sliderTopP.setRange(0f, 1f);
        tvTopPVal = findViewById(R.id.tv_top_p_val); btnResetTopP = findViewById(R.id.btn_reset_top_p);

        sliderFreqPenalty = findViewById(R.id.slider_freq_penalty); sliderFreqPenalty.setRange(-2f, 2f);
        tvFreqPenaltyVal = findViewById(R.id.tv_freq_penalty_val); btnResetFreqPenalty = findViewById(R.id.btn_reset_freq_penalty);

        sliderPresPenalty = findViewById(R.id.slider_pres_penalty); sliderPresPenalty.setRange(-2f, 2f);
        tvPresPenaltyVal = findViewById(R.id.tv_pres_penalty_val); btnResetPresPenalty = findViewById(R.id.btn_reset_pres_penalty);

        setupListeners();
    }

    private void setupListeners() {
        sliderCtxWindow.setOnSliderChangeListener(val -> {
            defaults.setContextWindow((int) val);
            applyGenerationScreen();
        });
        switchRollout.setOnCheckedChangeListener(isChecked -> {
            defaults.setVisualizeRollout(isChecked);
            applyGenerationScreen();
        });

        // R16 Mutual Exclusion: Enabling Thinking turns Budget OFF
        switchThinking.setOnCheckedChangeListener(isChecked -> {
            if (isChecked) {
                defaults.setThinkingMode(GenerationDefaults.THINKING_EFFORT);
            } else {
                defaults.setThinkingMode(GenerationDefaults.THINKING_NONE);
            }
            applyGenerationScreen();
        });

        // R16 Mutual Exclusion: Enabling Budget turns Thinking OFF
        switchThinkingBudget.setOnCheckedChangeListener(isChecked -> {
            if (isChecked) {
                defaults.setThinkingMode(GenerationDefaults.THINKING_BUDGET);
            } else {
                defaults.setThinkingMode(GenerationDefaults.THINKING_NONE);
            }
            applyGenerationScreen();
        });

        sliderThinkingEffort.setOnSliderChangeListener(val -> {
            defaults.setThinkingEffort((int) val);
            applyGenerationScreen();
        });

        btnThinkingAdvanced.setOnClickListener(v -> {
            isAdvancedThinkingVisible = !isAdvancedThinkingVisible;
            applyGenerationScreen();
        });

        sliderThinkingBudget.setOnSliderChangeListener(val -> {
            defaults.setThinkingBudget((int) val);
            applyGenerationScreen();
        });

        switchServiceTier.setOnCheckedChangeListener(isChecked -> {
            defaults.setServiceTierEnabled(isChecked);
            applyGenerationScreen();
        });

        sliderServiceTier.setOnSliderChangeListener(val -> {
            defaults.setServiceTier((int) val);
            applyGenerationScreen();
        });

        // S4 Listeners
        sliderTemp.setOnSliderChangeListener(val -> { defaults.setTemperature(val); applyGenerationScreen(); });
        btnResetTemp.setOnClickListener(v -> { defaults.setTemperature(null); applyGenerationScreen(); });

        sliderMaxTokens.setOnSliderChangeListener(val -> { defaults.setMaxTokens((int) val); applyGenerationScreen(); });
        btnResetMaxTokens.setOnClickListener(v -> { defaults.setMaxTokens(null); applyGenerationScreen(); });

        sliderTopP.setOnSliderChangeListener(val -> { defaults.setTopP(val); applyGenerationScreen(); });
        btnResetTopP.setOnClickListener(v -> { defaults.setTopP(null); applyGenerationScreen(); });

        sliderFreqPenalty.setOnSliderChangeListener(val -> { defaults.setFreqPenalty(val); applyGenerationScreen(); });
        btnResetFreqPenalty.setOnClickListener(v -> { defaults.setFreqPenalty(null); applyGenerationScreen(); });

        sliderPresPenalty.setOnSliderChangeListener(val -> { defaults.setPresPenalty(val); applyGenerationScreen(); });
        btnResetPresPenalty.setOnClickListener(v -> { defaults.setPresPenalty(null); applyGenerationScreen(); });
    }

    private void applyGenerationScreen() {
        // S1
        int ctx = defaults.getContextWindow();
        sliderCtxWindow.setValue((float) ctx);
        tvCtxWindowSub.setText("Retain " + ctx + " recent messages");
        switchRollout.setChecked(defaults.isVisualizeRollout());

        // S2 Thinking Mode logic
        String mode = defaults.getThinkingMode();
        boolean effortOn = GenerationDefaults.THINKING_EFFORT.equals(mode);
        boolean budgetOn = GenerationDefaults.THINKING_BUDGET.equals(mode);

        switchThinking.setChecked(effortOn);
        switchThinkingBudget.setChecked(budgetOn);

        String[] effortLabels = {"Low", "Medium", "High", "xHigh"};
        int effort = defaults.getThinkingEffort();
        tvThinkingEffortVal.setText(effortLabels[effort]);
        sliderThinkingEffort.setValue((float) effort);
        sliderThinkingEffort.setEnabled(effortOn);
        cardThinkingEffortVal.setAlpha(effortOn ? 1f : 0.45f);

        int budget = defaults.getThinkingBudget();
        tvThinkingBudgetVal.setText(budget + " tokens");
        sliderThinkingBudget.setValue((float) budget);
        sliderThinkingBudget.setEnabled(budgetOn);
        cardThinkingBudgetVal.setAlpha(budgetOn ? 1f : 0.45f);
        
        containerThinkingAdvanced.setVisibility(isAdvancedThinkingVisible ? View.VISIBLE : View.GONE);
        btnThinkingAdvanced.setText(isAdvancedThinkingVisible ? "Hide advanced \u2303" : "Advanced \u2304");
        cardThinkingBudgetVal.setVisibility(budgetOn || isAdvancedThinkingVisible ? View.VISIBLE : View.GONE);

        if (effortOn) tvThinkingSub.setText(effortLabels[effort]);
        else if (budgetOn) tvThinkingSub.setText(budget + " tokens");
        else tvThinkingSub.setText("Off");

        // S3 Service Tier
        boolean tierEnabled = defaults.isServiceTierEnabled();
        switchServiceTier.setChecked(tierEnabled);
        String[] tierLabels = {"Auto", "Default", "Flex", "Fast"};
        int tier = defaults.getServiceTier();
        tvServiceTierSub.setText(tierLabels[tier]);
        tvServiceTierVal.setText(tierLabels[tier]);
        sliderServiceTier.setValue((float) tier);
        sliderServiceTier.setEnabled(tierEnabled);
        cardServiceTierVal.setAlpha(tierEnabled ? 1f : 0.45f);

        // S4 Parameters
        updateParamRow(defaults.getTemperature(), sliderTemp, tvTempVal, btnResetTemp, "%.2f", 0.7f);
        updateParamRow(defaults.getMaxTokens() != null ? defaults.getMaxTokens().floatValue() : null, sliderMaxTokens, tvMaxTokensVal, btnResetMaxTokens, "%.0f", 4096f);
        updateParamRow(defaults.getTopP(), sliderTopP, tvTopPVal, btnResetTopP, "%.2f", 1.0f);
        updateParamRow(defaults.getFreqPenalty(), sliderFreqPenalty, tvFreqPenaltyVal, btnResetFreqPenalty, "%.2f", 0.0f);
        updateParamRow(defaults.getPresPenalty(), sliderPresPenalty, tvPresPenaltyVal, btnResetPresPenalty, "%.2f", 0.0f);
    }

    private void updateParamRow(Float val, TajSlider slider, TextView tvValue, View btnReset, String format, float defPos) {
        if (val == null) {
            tvValue.setText("Not specified");
            tvValue.setTextColor(getThemeColor(R.attr.colorOnSurfaceVariant));
            btnReset.setVisibility(View.GONE);
            slider.setValue(defPos); // R16: Reset to default position
        } else {
            tvValue.setText(String.format(Locale.US, format, val));
            tvValue.setTextColor(getThemeColor(R.attr.colorPrimary));
            btnReset.setVisibility(View.VISIBLE);
            slider.setValue(val);
        }
    }

    private int getThemeColor(int attrId) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attrId, typedValue, true);
        if (typedValue.resourceId != 0) {
            return ContextCompat.getColor(this, typedValue.resourceId);
        }
        return typedValue.data;
    }

    @Override
    public void handleInsetts(View view) {
        view.setPadding(0, getStatusBarHeight(), 0, getNavigationBarHeight());
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : 0;
    }

    private int getNavigationBarHeight() {
        int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : 0;
    }
}
