package pro.sketchware.ai.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.models.ModelCatalog;
import pro.sketchware.ai.providers.ProviderIconLoader;
import pro.sketchware.ai.transcription.TranscriptionSettings;
import pro.sketchware.ai.ui.TajSlider;

/**
 * [WHAT] Settings screen for Image Transcription.
 * [WHY] P2-IT: Allows users to configure vision-based image description (Java/XML).
 * [HOW] Bound to TranscriptionSettings (SSOT); uses dynamic row inflation for model lists.
 */
public class ImageTranscriptionActivity extends BaseAppCompatActivity {

    private TranscriptionSettings settings;
    private ModelCatalog catalog;
    private ProviderIconLoader iconLoader;

    private MaterialSwitch switchEnable;
    private View containerSettings;
    
    private TextView tvActiveModelName;
    private TextView tvActiveModelProvider;
    
    private LinearLayout containerEnabledModels;
    private TextView tvPromptPreview;
    private TextView tvBatchSizeVal;
    private TajSlider sliderBatchSize;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_transcription);

        settings = TranscriptionSettings.get(this);
        catalog = ModelCatalog.get(this);
        iconLoader = ProviderIconLoader.get(this);

        initUi();
        applyUiState();
        handleInsetts(findViewById(android.R.id.content));
    }

    private void initUi() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_docs).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/docs/guides/vision"));
            startActivity(intent);
        });

        switchEnable = findViewById(R.id.switch_enable);
        containerSettings = findViewById(R.id.container_transcription_settings);
        
        tvActiveModelName = findViewById(R.id.tv_active_model_name);
        tvActiveModelProvider = findViewById(R.id.tv_active_model_provider);
        
        containerEnabledModels = findViewById(R.id.container_enabled_models);
        tvPromptPreview = findViewById(R.id.tv_prompt_preview);
        tvBatchSizeVal = findViewById(R.id.tv_batch_size_val);
        sliderBatchSize = findViewById(R.id.slider_batch_size);

        switchEnable.setOnCheckedChangeListener((btn, isChecked) -> {
            settings.setEnabled(isChecked);
            applyUiState();
        });

        findViewById(R.id.row_active_model).setOnClickListener(v -> showModelPickerDialog());
        findViewById(R.id.row_prompt).setOnClickListener(v -> showPromptEditDialog());

        sliderBatchSize.setRange(1, 10);
        sliderBatchSize.setStops(new float[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        sliderBatchSize.setOnSliderChangeListener(val -> {
            tvBatchSizeVal.setText(String.valueOf((int) val));
            settings.setBatchSize((int) val);
        });
    }

    private void applyUiState() {
        boolean enabled = settings.isEnabled();
        switchEnable.setChecked(enabled);
        containerSettings.setVisibility(enabled ? View.VISIBLE : View.GONE);

        if (enabled) {
            // Model
            String modelId = settings.getModel();
            if (modelId == null) {
                tvActiveModelName.setText("Not selected");
                tvActiveModelName.setAlpha(0.6f);
                tvActiveModelProvider.setText("Select a vision model");
            } else {
                tvActiveModelName.setAlpha(1.0f);
                String[] parts = modelId.split(":", 2);
                if (parts.length == 2) {
                    tvActiveModelName.setText(catalog.getAlias(parts[0], parts[1]));
                    tvActiveModelProvider.setText(parts[0]);
                } else {
                    tvActiveModelName.setText(modelId);
                    tvActiveModelProvider.setText("Unknown provider");
                }
            }

            // Enabled Models
            renderEnabledModels();

            // Prompt
            tvPromptPreview.setText(settings.getPrompt());

            // Batch Size
            int batchSize = settings.getBatchSize();
            tvBatchSizeVal.setText(String.valueOf(batchSize));
            sliderBatchSize.setValue(batchSize);
        }
    }

    private void renderEnabledModels() {
        containerEnabledModels.removeAllViews();
        List<String> enabledModels = settings.getEnabledModels();
        LayoutInflater inflater = getLayoutInflater();

        if (enabledModels.isEmpty()) {
            View emptyView = inflater.inflate(R.layout.item_transcription_model_row, containerEnabledModels, false);
            TextView title = emptyView.findViewById(R.id.title);
            TextView subtitle = emptyView.findViewById(R.id.subtitle);
            ImageView icon = emptyView.findViewById(R.id.icon);
            
            icon.setImageResource(R.drawable.ic_mtrl_info);
            title.setText("No models enabled");
            subtitle.setText("Add models below to enable image transcription");
            containerEnabledModels.addView(emptyView);
        } else {
            for (String modelId : enabledModels) {
                View row = inflater.inflate(R.layout.item_transcription_model_row, containerEnabledModels, false);
                TextView title = row.findViewById(R.id.title);
                TextView subtitle = row.findViewById(R.id.subtitle);
                ImageView icon = row.findViewById(R.id.icon);
                ImageView btnDelete = row.findViewById(R.id.btn_delete);
                
                String[] parts = modelId.split(":", 2);
                if (parts.length == 2) {
                    title.setText(catalog.getAlias(parts[0], parts[1]));
                    subtitle.setText(parts[0]);
                    iconLoader.loadIcon(icon, parts[0]);
                } else {
                    title.setText(modelId);
                    subtitle.setText("Unknown");
                }
                
                btnDelete.setVisibility(View.VISIBLE);
                btnDelete.setOnClickListener(v -> {
                    List<String> current = new ArrayList<>(settings.getEnabledModels());
                    current.remove(modelId);
                    settings.setEnabledModels(current);
                    if (modelId.equals(settings.getModel())) {
                        settings.setModel(null);
                    }
                    applyUiState();
                });
                
                containerEnabledModels.addView(row);
            }
        }

        // Add model row
        View addRow = inflater.inflate(R.layout.item_transcription_model_row, containerEnabledModels, false);
        addRow.findViewById(R.id.icon_add).setVisibility(View.VISIBLE);
        addRow.findViewById(R.id.icon).setVisibility(View.GONE);
        ((TextView) addRow.findViewById(R.id.title)).setText("Add model");
        ((TextView) addRow.findViewById(R.id.subtitle)).setText("Enable another vision model");
        addRow.setOnClickListener(v -> showAddModelDialog());
        containerEnabledModels.addView(addRow);
    }

    private void showModelPickerDialog() {
        List<String> enabledIds = settings.getEnabledModels();
        String current = settings.getModel();

        String[] items = new String[enabledIds.size() + 1];
        items[0] = "Not selected";
        int checkedItem = (current == null) ? 0 : -1;

        for (int i = 0; i < enabledIds.size(); i++) {
            String mid = enabledIds.get(i);
            String[] parts = mid.split(":", 2);
            String alias = parts.length == 2 ? catalog.getAlias(parts[0], parts[1]) : mid;
            items[i + 1] = alias + (parts.length == 2 ? " (" + parts[0] + ")" : "");
            if (mid.equals(current)) checkedItem = i + 1;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Transcription Model")
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    if (which == 0) {
                        settings.setModel(null);
                    } else {
                        settings.setModel(enabledIds.get(which - 1));
                    }
                    applyUiState();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddModelDialog() {
        List<ModelCatalog.ModelEntry> allUsable = catalog.usableModels();
        List<String> currentEnabled = settings.getEnabledModels();
        
        List<ModelCatalog.ModelEntry> candidates = new ArrayList<>();
        for (ModelCatalog.ModelEntry e : allUsable) {
            String mid = e.providerId + ":" + e.modelId;
            if (!currentEnabled.contains(mid)) {
                candidates.add(e);
            }
        }

        if (candidates.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Add Model")
                    .setMessage("All enabled models are already added to transcription.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String[] items = new String[candidates.size()];
        boolean[] checkedItems = new boolean[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            items[i] = candidates.get(i).alias + " (" + candidates.get(i).providerId + ")";
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Models to Enable")
                .setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
                    checkedItems[which] = isChecked;
                })
                .setPositiveButton("Add", (dialog, which) -> {
                    List<String> current = new ArrayList<>(settings.getEnabledModels());
                    for (int i = 0; i < checkedItems.length; i++) {
                        if (checkedItems[i]) {
                            ModelCatalog.ModelEntry e = candidates.get(i);
                            current.add(e.providerId + ":" + e.modelId);
                        }
                    }
                    settings.setEnabledModels(current);
                    applyUiState();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPromptEditDialog() {
        final EditText et = new EditText(this);
        et.setText(settings.getPrompt());
        et.setHint("Transcription Prompt");
        et.setMinLines(3);
        et.setGravity(android.view.Gravity.TOP);
        
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(padding, padding, padding, padding);
        container.addView(et);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Transcription Prompt")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String val = et.getText().toString().trim();
                    if (!val.isEmpty()) {
                        settings.setPrompt(val);
                        applyUiState();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
