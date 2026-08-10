package pro.sketchware.ai.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.models.ModelCatalog;

/**
 * [WHAT] Settings screen for Title Generation.
 * [WHY] P2-TG: Allows users to configure automatic conversation naming (Java/XML).
 * [HOW] Strictly derived from mockups; bound to AiStorage (R16).
 */
public class TitleGenerationActivity extends BaseAppCompatActivity {

    private AiStorage storage;
    private ModelCatalog catalog;

    private MaterialSwitch switchAuto_gen;
    private View rowTitleModel;
    private View dividerModel;
    private TextView tvTitleModelSub;
    private MaterialSwitch switchNotifications;
    private TextView tvTitlePromptPreview;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_title_generation);

        storage = AiStorage.get(this);
        catalog = ModelCatalog.get(this);

        initUi();
        applyState();
        handleInsetts(findViewById(android.R.id.content));
    }

    private void initUi() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_docs).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/docs/models"));
            startActivity(intent);
        });

        switchAuto_gen = findViewById(R.id.switch_auto_gen);
        rowTitleModel = findViewById(R.id.row_title_model);
        dividerModel = findViewById(R.id.divider_model);
        tvTitleModelSub = findViewById(R.id.tv_title_model_sub);
        switchNotifications = findViewById(R.id.switch_notifications);
        tvTitlePromptPreview = findViewById(R.id.tv_title_prompt_preview);

        switchAuto_gen.setOnCheckedChangeListener((btn, isChecked) -> {
            storage.setTitleGenEnabled(isChecked);
            applyState();
        });

        rowTitleModel.setOnClickListener(v -> showModelPickerDialog());

        switchNotifications.setOnCheckedChangeListener((btn, isChecked) -> {
            storage.setTitleGenNotificationsEnabled(isChecked);
        });

        findViewById(R.id.row_title_prompt).setOnClickListener(v -> showPromptEditDialog());
    }

    /**
     * [R16] derivation: UI strictly reflects the persistent state.
     */
    private void applyState() {
        boolean enabled = storage.isTitleGenEnabled();
        switchAuto_gen.setChecked(enabled);
        
        rowTitleModel.setVisibility(enabled ? View.VISIBLE : View.GONE);
        dividerModel.setVisibility(enabled ? View.VISIBLE : View.GONE);

        String modelId = storage.getTitleGenModel();
        if (modelId == null) {
            tvTitleModelSub.setText("Use Current Model");
        } else {
            String foundAlias = null;
            for (ModelCatalog.ModelEntry entry : catalog.usableModels()) {
                String key = entry.providerId + ":" + entry.modelId;
                if (key.equals(modelId)) {
                    foundAlias = entry.alias;
                    break;
                }
            }
            tvTitleModelSub.setText(foundAlias != null ? foundAlias : modelId);
        }

        switchNotifications.setChecked(storage.isTitleGenNotificationsEnabled());
        tvTitlePromptPreview.setText(storage.getTitleGenPrompt());
    }

    private void showModelPickerDialog() {
        List<ModelCatalog.ModelEntry> usable = catalog.usableModels();
        String currentModel = storage.getTitleGenModel();

        String[] items = new String[usable.size() + 1];
        items[0] = "Use Current Model";
        int checkedItem = (currentModel == null) ? 0 : -1;

        for (int i = 0; i < usable.size(); i++) {
            ModelCatalog.ModelEntry e = usable.get(i);
            items[i + 1] = e.alias + " (" + e.providerId + ")";
            if (currentModel != null && currentModel.equals(e.providerId + ":" + e.modelId)) {
                checkedItem = i + 1;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Title Model")
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    if (which == 0) {
                        storage.setTitleGenModel(null);
                    } else {
                        ModelCatalog.ModelEntry e = usable.get(which - 1);
                        storage.setTitleGenModel(e.providerId + ":" + e.modelId);
                    }
                    applyState();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPromptEditDialog() {
        final EditText et = new EditText(this);
        et.setText(storage.getTitleGenPrompt());
        et.setHint("Prompt");
        et.setMinLines(3);
        et.setGravity(android.view.Gravity.TOP);
        
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(padding, padding, padding, padding);
        container.addView(et);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Title Prompt")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String val = et.getText().toString().trim();
                    if (val.isEmpty()) storage.setTitleGenPrompt(null);
                    else storage.setTitleGenPrompt(val);
                    applyState();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
