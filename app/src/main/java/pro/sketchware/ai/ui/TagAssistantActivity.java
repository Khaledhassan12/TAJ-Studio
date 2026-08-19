package pro.sketchware.ai.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.ai.config.AIConfigStore;
import pro.sketchware.ai.core.AIProviderRegistry;
import pro.sketchware.ai.core.ProviderProfile;
import pro.sketchware.ai.net.AIException;
import pro.sketchware.ai.net.ModelSyncService;
import pro.sketchware.databinding.ActivityTagAssistantBinding;

/**
 * TAG Assistant setup screen: master switch, default mode, provider selection,
 * credentials, live connection test (models endpoint) and the model syncer +
 * picker. Fully isolated from the host app: every failure ends in a friendly
 * dialog or Snackbar, never a crash.
 */
public final class TagAssistantActivity extends BaseAppCompatActivity {

    private ActivityTagAssistantBinding binding;
    private AIConfigStore store;
    private AIProviderRegistry registry;
    private final List<ProviderProfile> profiles = new ArrayList<>();
    private ProviderProfile currentProfile;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean syncInProgress = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityTagAssistantBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        {
            View view = binding.appBarLayout;
            int left = view.getPaddingLeft();
            int top = view.getPaddingTop();
            int right = view.getPaddingRight();
            int bottom = view.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(view, (v, i) -> {
                Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(left + insets.left, top + insets.top, right + insets.right, bottom + insets.bottom);
                return i;
            });
        }

        {
            View view = binding.contentScroll;
            int left = view.getPaddingLeft();
            int top = view.getPaddingTop();
            int right = view.getPaddingRight();
            int bottom = view.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(view, (v, i) -> {
                Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(left, top, right, bottom + insets.bottom);
                return i;
            });
        }

        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));

        store = AIConfigStore.getInstance(this);
        registry = AIProviderRegistry.getInstance(this);

        setupStatusCard();
        setupProviderCard();
    }

    @Override
    public void onDestroy() {
        syncInProgress = false;
        executor.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // Card 1: Status
    // ------------------------------------------------------------------

    private void setupStatusCard() {
        binding.switchEnable.setChecked(store.isAssistantEnabled());
        binding.switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            store.setAssistantEnabled(isChecked);
            updateStatusUi();
        });

        boolean agentDefault = AIConfigStore.MODE_AGENT.equals(store.getDefaultMode());
        binding.chipGroupMode.check(agentDefault ? R.id.chip_mode_agent : R.id.chip_mode_chat);
        binding.chipGroupMode.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                String mode = checkedIds.get(0) == R.id.chip_mode_agent
                        ? AIConfigStore.MODE_AGENT
                        : AIConfigStore.MODE_CHAT;
                store.setDefaultMode(mode);
            }
        });

        updateStatusUi();
    }

    private void updateStatusUi() {
        boolean enabled = binding.switchEnable.isChecked();
        binding.cardProvider.setAlpha(enabled ? 1f : 0.55f);
        if (enabled) {
            binding.tvStatusMessage.setVisibility(View.GONE);
        } else {
            binding.tvStatusMessage.setText(R.string.ai_connection_disabled);
            binding.tvStatusMessage.setVisibility(View.VISIBLE);
        }
    }

    // ------------------------------------------------------------------
    // Card 2: Provider
    // ------------------------------------------------------------------

    private void setupProviderCard() {
        profiles.addAll(registry.allProfiles());

        List<String> names = new ArrayList<>();
        for (ProviderProfile profile : profiles) {
            names.add(profile.displayName);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, names);
        binding.dropdownProvider.setAdapter(adapter);
        binding.dropdownProvider.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < profiles.size()) {
                applyProfile(profiles.get(position));
            }
        });

        ProviderProfile saved = registry.get(store.getSelectedProviderId());
        if (saved == null || !profiles.contains(saved)) {
            saved = profiles.get(0);
        }
        binding.dropdownProvider.setText(saved.displayName, false);
        applyProfile(saved);

        binding.btnTest.setOnClickListener(v -> testConnection());
        binding.btnSave.setOnClickListener(v -> saveConfiguration());
        binding.btnSyncModels.setOnClickListener(v -> syncModels());
        binding.btnChooseModel.setOnClickListener(v -> openModelPicker());
    }
    private void applyProfile(ProviderProfile profile) {
        currentProfile = profile;

        binding.editApiKey.setText(store.getApiKey(profile.id));
        binding.tilApiKey.setEnabled(profile.requiresKey);
        if (!profile.requiresKey) {
            binding.tilApiKey.setError(null);
        }

        String savedUrl = store.getBaseUrl(profile.id);
        binding.editBaseUrl.setText(!savedUrl.isEmpty() ? savedUrl : profile.defaultBaseUrl);
        binding.tilBaseUrl.setEnabled(profile.baseUrlEditable);
        binding.tilBaseUrl.setError(null);

        String savedModel = store.getModel(profile.id);
        if (!savedModel.isEmpty()) {
            binding.editModel.setText(savedModel);
        } else if (profile.hasSuggestions()) {
            binding.editModel.setText(profile.defaultModelSuggestions[0]);
        } else {
            binding.editModel.setText("");
        }
        binding.tilModel.setError(null);

        populateModelChips(profile);
        refreshModelSyncUi(profile);
    }

    private void populateModelChips(ProviderProfile profile) {
        binding.chipGroupModels.removeAllViews();
        if (!profile.hasSuggestions()) {
            binding.tvSuggestionsLabel.setVisibility(View.GONE);
            binding.chipGroupModels.setVisibility(View.GONE);
            return;
        }
        binding.tvSuggestionsLabel.setVisibility(View.VISIBLE);
        binding.chipGroupModels.setVisibility(View.VISIBLE);
        for (String model : profile.defaultModelSuggestions) {
            Chip chip = new Chip(this);
            chip.setText(model);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setOnClickListener(v -> binding.editModel.setText(model));
            binding.chipGroupModels.addView(chip);
        }
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private String textOf(EditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString();
    }

    private boolean validateFields() {
        return validateCredentials() && validateModelField();
    }

    private boolean validateCredentials() {
        if (currentProfile == null) {
            return false;
        }
        String key = textOf(binding.editApiKey).trim();
        String url = textOf(binding.editBaseUrl).trim();

        if (currentProfile.requiresKey && key.isEmpty()) {
            binding.tilApiKey.setError(getString(R.string.ai_key_required));
            return false;
        }
        binding.tilApiKey.setError(null);

        if (currentProfile.baseUrlEditable && currentProfile.defaultBaseUrl.isEmpty() && url.isEmpty()) {
            binding.tilBaseUrl.setError(getString(R.string.ai_url_required));
            return false;
        }
        binding.tilBaseUrl.setError(null);
        return true;
    }

    private boolean validateModelField() {
        if (currentProfile == null) {
            return false;
        }
        String model = textOf(binding.editModel).trim();
        if (model.isEmpty()) {
            binding.tilModel.setError(getString(R.string.ai_model_required));
            return false;
        }
        binding.tilModel.setError(null);
        return true;
    }

    // ------------------------------------------------------------------
    // Save + connection test + model sync
    // ------------------------------------------------------------------

    private void saveConfiguration() {
        if (!validateFields()) {
            return;
        }
        String key = AIConfigStore.sanitizeKey(textOf(binding.editApiKey));
        String url = AIConfigStore.sanitizeBaseUrl(textOf(binding.editBaseUrl));
        String model = textOf(binding.editModel).trim();

        store.setSelectedProviderId(currentProfile.id);
        store.setApiKey(currentProfile.id, key);
        store.setBaseUrl(currentProfile.id, url);
        store.setModel(currentProfile.id, model);

        Snackbar.make(binding.getRoot(), R.string.ai_saved, Snackbar.LENGTH_SHORT).show();
    }

    /** Test connection now probes the MODELS endpoint (fast, no tokens burned). */
    private void testConnection() {
        if (syncInProgress || !validateCredentials()) {
            return;
        }
        setSyncUi(true, true);
        startModelFetch();
    }

    private void syncModels() {
        if (syncInProgress || !validateCredentials()) {
            return;
        }
        setSyncUi(true, false);
        startModelFetch();
    }

    private void startModelFetch() {
        ProviderProfile effective = resolveEffectiveProfile();
        final boolean testMode = binding.progressTest.getVisibility() == View.VISIBLE;
        String key = AIConfigStore.sanitizeKey(textOf(binding.editApiKey));
        executor.execute(() -> {
            try {
                ModelSyncService.Result result = ModelSyncService.fetch(effective, key);
                store.saveModelsCache(effective.id, result.models);
                store.setVerified(true);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setSyncUi(false, testMode);
                    if (result.modelListUnavailable) {
                        new MaterialAlertDialogBuilder(TagAssistantActivity.this)
                                .setTitle(R.string.ai_choose_model)
                                .setMessage(R.string.ai_no_model_list)
                                .setPositiveButton(R.string.common_word_close, null)
                                .show();
                        return;
                    }
                    refreshModelSyncUi(effective);
                    Snackbar.make(binding.getRoot(),
                            getString(R.string.ai_connected_models, result.models.size()),
                            Snackbar.LENGTH_SHORT).show();
                });
            } catch (AIException e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setSyncUi(false, testMode);
                    String message = e.friendlyMessage();
                    if (!e.rawBody.isEmpty()) {
                        message += "\n\n" + e.rawBody;
                    }
                    new MaterialAlertDialogBuilder(TagAssistantActivity.this)
                            .setTitle(R.string.ai_test_connection)
                            .setMessage(message)
                            .setPositiveButton(R.string.common_word_close, null)
                            .show();
                });
            }
        });
    }
    private ProviderProfile resolveEffectiveProfile() {
        ProviderProfile effective = currentProfile;
        if (currentProfile != null && currentProfile.baseUrlEditable) {
            String url = AIConfigStore.sanitizeBaseUrl(textOf(binding.editBaseUrl));
            if (!url.isEmpty()) {
                effective = currentProfile.withBaseUrl(url);
            }
        }
        return effective;
    }

    private void setSyncUi(boolean inProgress, boolean testMode) {
        syncInProgress = inProgress;
        binding.btnSyncModels.setEnabled(!inProgress);
        binding.btnChooseModel.setEnabled(!inProgress);
        binding.btnSave.setEnabled(!inProgress);
        if (testMode) {
            binding.progressTest.setVisibility(inProgress ? View.VISIBLE : View.GONE);
            binding.btnTest.setEnabled(!inProgress);
            if (inProgress) {
                binding.tvStatusMessage.setText(R.string.ai_connection_testing);
                binding.tvStatusMessage.setVisibility(View.VISIBLE);
            } else {
                updateStatusUi();
            }
        } else {
            binding.progressModelSync.setVisibility(inProgress ? View.VISIBLE : View.GONE);
        }
    }

    /** Opens the M3 model picker bottom sheet for the active provider. */
    private void openModelPicker() {
        if (syncInProgress || !validateCredentials()) {
            return;
        }
        ProviderProfile effective = resolveEffectiveProfile();
        String key = AIConfigStore.sanitizeKey(textOf(binding.editApiKey));
        ModelPickerBottomSheet sheet = ModelPickerBottomSheet.newInstance(
                currentProfile.id, effective, key, ProviderProfile.exposesPricing(currentProfile));
        sheet.setListener(this::onModelPicked);
        sheet.show(getSupportFragmentManager(), "model_picker");
    }

    private void onModelPicked(String modelId) {
        binding.editModel.setText(modelId);
        saveConfiguration();
    }

    // ------------------------------------------------------------------
    // Model sync UI state
    // ------------------------------------------------------------------

    private void refreshModelSyncUi(ProviderProfile profile) {
        boolean hasCache = store.getModelsCacheTimestamp(profile.id) > 0L;
        boolean pricing = ProviderProfile.exposesPricing(profile);
        binding.chipGroupModelFilter.setVisibility(hasCache ? View.VISIBLE : View.GONE);
        binding.chipModelFilterAll.setChecked(true);
        binding.chipModelFilterFree.setEnabled(pricing);
        binding.chipModelFilterPaid.setEnabled(pricing);
        binding.tvPricingHint.setVisibility(hasCache && !pricing ? View.VISIBLE : View.GONE);
        binding.tvLastSync.setText(formatLastSync(profile));
    }

    private String formatLastSync(ProviderProfile profile) {
        long timestamp = store.getModelsCacheTimestamp(profile.id);
        if (timestamp <= 0L) {
            return getString(R.string.ai_last_sync_never);
        }
        SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return getString(R.string.ai_synced_at, format.format(new Date(timestamp)));
    }
}
