package pro.sketchware.ai.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.agent.tools.ToolRegistry;
import pro.sketchware.ai.images.ImageGenSettings;
import pro.sketchware.ai.models.ModelCatalog;

/**
 * [WHAT] Settings screen for Image Generation.
 * [WHY] P2-IG: Lets the user enable the generate_image agent tool and pick its
 * model + default size (Java/XML, §2 real binding).
 * [HOW] All UI strictly derived from ImageGenSettings (SSOT, R16); single writer
 * per field; visible rows exist IFF enabled (§8/§17).
 *
 * [العربية]
 * شاشة إعدادات توليد الصور.
 * تُشتق كل حالات الواجهة بصرامة من ImageGenSettings (المصدر الوحيد، R16)،
 * وتظهر الصفوف الفرعية فقط عند تفعيل الميزة.
 */
public class ImageGenerationActivity extends BaseAppCompatActivity {

    private static final long SIZE_SAVE_DEBOUNCE_MS = 500L;

    private ImageGenSettings settings;
    private ModelCatalog catalog;

    private MaterialSwitch switchEnable;
    private View rowImageModel;
    private View dividerModel;
    private View dividerSize;
    private View rowDefaultSize;
    private TextView tvImageModelSub;
    private EditText etSizeW;
    private EditText etSizeH;
    private TextView tvSizeError;

    private final Handler sizeHandler = new Handler(Looper.getMainLooper());
    private boolean applyingState = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_generation);

        settings = ImageGenSettings.get(this);
        catalog = ModelCatalog.get(this);

        initUi();
        applyUiState();
        handleInsetts(findViewById(android.R.id.content));
    }

    @Override
    public void onResume() {
        super.onResume();
        // P2-IG: re-check tool registration whenever we return to this screen.
        ToolRegistry.syncImageGen(this);
        applyUiState();
    }

    @Override
    public void onDestroy() {
        sizeHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void initUi() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_docs).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/docs/guides/images"));
            startActivity(intent);
        });

        switchEnable = findViewById(R.id.switch_enable);
        rowImageModel = findViewById(R.id.row_image_model);
        dividerModel = findViewById(R.id.divider_model);
        dividerSize = findViewById(R.id.divider_size);
        rowDefaultSize = findViewById(R.id.row_default_size);
        tvImageModelSub = findViewById(R.id.tv_image_model_sub);
        etSizeW = findViewById(R.id.et_size_w);
        etSizeH = findViewById(R.id.et_size_h);
        tvSizeError = findViewById(R.id.tv_size_error);

        switchEnable.setOnCheckedChangeListener((btn, isChecked) -> {
            if (applyingState) return;
            settings.setEnabled(isChecked);
            ToolRegistry.syncImageGen(this);
            applyUiState();
        });

        rowImageModel.setOnClickListener(v -> showModelPickerDialog());

        TextWatcher sizeWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (applyingState) return;
                scheduleSizeSave();
            }
        };
        etSizeW.addTextChangedListener(sizeWatcher);
        etSizeH.addTextChangedListener(sizeWatcher);
    }

    /**
     * [R16/§8] derivation: every widget state is strictly derived from the
     * persisted SSOT. Sub-rows are VISIBLE IFF the feature is enabled.
     */
    private void applyUiState() {
        applyingState = true;
        try {
            boolean enabled = settings.isEnabled();
            switchEnable.setChecked(enabled);

            int subVisibility = enabled ? View.VISIBLE : View.GONE;
            rowImageModel.setVisibility(subVisibility);
            dividerModel.setVisibility(subVisibility);
            dividerSize.setVisibility(subVisibility);
            rowDefaultSize.setVisibility(subVisibility);
            if (!enabled) tvSizeError.setVisibility(View.GONE);

            // Image model subtitle: alias (falls back to model name) or "Not selected".
            String modelKey = settings.getModel();
            if (modelKey == null) {
                tvImageModelSub.setText("Not selected");
                tvImageModelSub.setAlpha(0.6f);
            } else {
                tvImageModelSub.setAlpha(1.0f);
                tvImageModelSub.setText(resolveModelLabel(modelKey));
            }

            // Sizes (only refill when the user is not typing).
            if (enabled) {
                if (!etSizeW.hasFocus()) etSizeW.setText(String.valueOf(settings.getSizeW()));
                if (!etSizeH.hasFocus()) etSizeH.setText(String.valueOf(settings.getSizeH()));
            }
            etSizeW.setError(null);
            etSizeH.setError(null);
        } finally {
            applyingState = false;
        }
    }

    private String resolveModelLabel(String modelKey) {
        String[] parts = modelKey.split(":", 2);
        if (parts.length == 2) {
            return catalog.getAlias(parts[0], parts[1]) + " (" + parts[0] + ")";
        }
        return modelKey;
    }

    // --- Size debounce (500ms) ---

    private void scheduleSizeSave() {
        sizeHandler.removeCallbacksAndMessages(null);
        sizeHandler.postDelayed(this::commitSizes, SIZE_SAVE_DEBOUNCE_MS);
    }

    private void commitSizes() {
        Integer w = parseSize(etSizeW);
        Integer h = parseSize(etSizeH);

        boolean valid = true;
        if (w == null) {
            etSizeW.setError("Must be " + ImageGenSettings.MIN_SIZE + "–" + ImageGenSettings.MAX_SIZE);
            valid = false;
        }
        if (h == null) {
            etSizeH.setError("Must be " + ImageGenSettings.MIN_SIZE + "–" + ImageGenSettings.MAX_SIZE);
            valid = false;
        }

        if (!valid) {
            tvSizeError.setText("Size not saved: values must be between " + ImageGenSettings.MIN_SIZE + " and " + ImageGenSettings.MAX_SIZE + " px.");
            tvSizeError.setVisibility(View.VISIBLE);
            return;
        }

        tvSizeError.setVisibility(View.GONE);
        settings.setSizeW(w);
        settings.setSizeH(h);
    }

    /**
     * @return parsed value IFF it is a valid integer inside MIN..MAX, else null.
     */
    private Integer parseSize(EditText field) {
        String text = field.getText().toString().trim();
        if (text.isEmpty()) return null;
        try {
            int value = Integer.parseInt(text);
            if (value < ImageGenSettings.MIN_SIZE || value > ImageGenSettings.MAX_SIZE) return null;
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // --- Model picker (single-selection, §5/§6) ---

    private void showModelPickerDialog() {
        List<ModelCatalog.ModelEntry> usable = catalog.usableModels();
        List<ModelCatalog.ModelEntry> filtered = new ArrayList<>();
        for (ModelCatalog.ModelEntry e : usable) {
            if (ImageGenSettings.matchesImageKeywords(e.modelId) || ImageGenSettings.matchesImageKeywords(e.alias)) {
                filtered.add(e);
            }
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        container.setPadding(padding, dp(8), padding, 0);

        CheckBox cbShowAll = new CheckBox(this);
        cbShowAll.setText("Show all models");
        container.addView(cbShowAll);

        ListView listView = new ListView(this);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setDivider(null);
        container.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(320)));

        final String[] currentHolder = {settings.getModel()};
        final androidx.appcompat.app.AlertDialog[] dialogRef = new androidx.appcompat.app.AlertDialog[1];

        Runnable refreshList = () -> {
            List<ModelCatalog.ModelEntry> source = cbShowAll.isChecked() ? usable : filtered;
            List<String> labels = new ArrayList<>();
            labels.add("Not selected");
            for (ModelCatalog.ModelEntry e : source) {
                labels.add(e.alias + " (" + e.providerId + ")");
            }
            listView.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_list_item_single_choice, labels));

            int checked = 0;
            if (currentHolder[0] != null) {
                for (int i = 0; i < source.size(); i++) {
                    ModelCatalog.ModelEntry e = source.get(i);
                    if (currentHolder[0].equals(e.providerId + ":" + e.modelId)) {
                        checked = i + 1;
                        break;
                    }
                }
            }
            listView.setItemChecked(checked, true);
            listView.setOnItemClickListener((parent, view, position, id) -> {
                // Single writer (R16): exactly ONE selection persists immediately.
                if (position == 0) {
                    settings.setModel(null);
                } else {
                    ModelCatalog.ModelEntry e = source.get(position - 1);
                    settings.setModel(e.providerId + ":" + e.modelId);
                }
                applyUiState();
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            });
        };

        cbShowAll.setOnCheckedChangeListener((btn, isChecked) -> refreshList.run());
        refreshList.run();

        dialogRef[0] = new MaterialAlertDialogBuilder(this)
                .setTitle("Select Image Model")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
