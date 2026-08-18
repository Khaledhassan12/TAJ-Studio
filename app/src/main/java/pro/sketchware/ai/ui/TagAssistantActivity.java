package pro.sketchware.ai.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.ai.config.AIConfigStore;
import pro.sketchware.ai.core.AIProvider;
import pro.sketchware.ai.core.AIProviderRegistry;
import pro.sketchware.ai.core.AIRequest;
import pro.sketchware.ai.core.AIMessage;
import pro.sketchware.ai.core.AIResponse;
import pro.sketchware.ai.core.ProviderProfile;
import pro.sketchware.ai.core.StreamCallbacks;
import pro.sketchware.ai.net.AIException;
import pro.sketchware.databinding.ActivityTagAssistantBinding;

/**
 * TAG Assistant setup screen: master switch, default mode, provider selection,
 * credentials and a live connection test. Fully isolated from the host app —
 * every failure here ends in a friendly dialog or Snackbar, never a crash.
 */
public final class TagAssistantActivity extends BaseAppCompatActivity {

    private ActivityTagAssistantBinding binding;
    private AIConfigStore store;
    private AIProviderRegistry registry;
    private final List<ProviderProfile> profiles = new ArrayList<>();
    private ProviderProfile currentProfile;
    private AIProvider.Handle testHandle;

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
        if (testHandle != null) {
            testHandle.cancel();
            testHandle = null;
        }
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
    // Save + connection test
    // ------------------------------------------------------------------

    private boolean validateFields() {
        if (currentProfile == null) {
            return false;
        }
        String key = binding.editApiKey.getText() == null ? "" : binding.editApiKey.getText().toString().trim();
        String url = binding.editBaseUrl.getText() == null ? "" : binding.editBaseUrl.getText().toString().trim();
        String model = binding.editModel.getText() == null ? "" : binding.editModel.getText().toString().trim();

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

        if (model.isEmpty()) {
            binding.tilModel.setError(getString(R.string.ai_model_required));
            return false;
        }
        binding.tilModel.setError(null);
        return true;
    }

    private void saveConfiguration() {
        if (!validateFields()) {
            return;
        }
        String key = binding.editApiKey.getText().toString().trim();
        String url = binding.editBaseUrl.getText().toString().trim();
        String model = binding.editModel.getText().toString().trim();

        store.setSelectedProviderId(currentProfile.id);
        store.setApiKey(currentProfile.id, key);
        store.setBaseUrl(currentProfile.id, url);
        store.setModel(currentProfile.id, model);

        Snackbar.make(binding.getRoot(), R.string.ai_saved, Snackbar.LENGTH_SHORT).show();
    }

    private void testConnection() {
        if (testHandle != null || !validateFields()) {
            return;
        }

        String key = binding.editApiKey.getText().toString().trim();
        String url = binding.editBaseUrl.getText().toString().trim();
        String model = binding.editModel.getText().toString().trim();

        ProviderProfile effectiveProfile = currentProfile;
        if (currentProfile.baseUrlEditable && !url.isEmpty()) {
            effectiveProfile = currentProfile.withBaseUrl(url);
        }

        AIProvider provider = registry.createProvider(effectiveProfile, key);
        AIRequest request = new AIRequest.Builder()
                .model(model)
                .temperature(0f)
                .maxTokens(8)
                .addMessage(AIMessage.user("Reply with exactly: OK"))
                .build();

        setTesting(true);
        final String providerName = effectiveProfile.displayName;
        testHandle = provider.stream(request, new StreamCallbacks() {
            @Override
            public void onToken(String token) {
                // A single token is enough to prove connectivity.
            }

            @Override
            public void onComplete(AIResponse response) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setTesting(false);
                    Snackbar.make(binding.getRoot(),
                            getString(R.string.ai_connection_success, providerName),
                            Snackbar.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setTesting(false);
                    String message = error instanceof AIException
                            ? ((AIException) error).friendlyMessage()
                            : getString(R.string.ai_connection_disabled);
                    new MaterialAlertDialogBuilder(TagAssistantActivity.this)
                            .setTitle(R.string.ai_test_connection)
                            .setMessage(message)
                            .setPositiveButton(R.string.common_word_close, null)
                            .show();
                });
            }
        });
    }

    private void setTesting(boolean testing) {
        testHandle = testing ? testHandle : null;
        binding.progressTest.setVisibility(testing ? View.VISIBLE : View.GONE);
        binding.btnTest.setEnabled(!testing);
        if (testing) {
            binding.tvStatusMessage.setText(R.string.ai_connection_testing);
            binding.tvStatusMessage.setVisibility(View.VISIBLE);
        } else if (binding.switchEnable.isChecked()) {
            binding.tvStatusMessage.setVisibility(View.GONE);
        }
    }
}
