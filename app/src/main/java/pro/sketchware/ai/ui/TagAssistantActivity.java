package pro.sketchware.ai.ui;

import android.os.Bundle;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.ai.config.AIConfigStore;
import pro.sketchware.ai.core.AIProviderRegistry;
import pro.sketchware.ai.core.ProviderProfile;
import pro.sketchware.ai.net.AIException;
import pro.sketchware.ai.net.ModelSyncService;
import pro.sketchware.databinding.ActivityTagAssistantBinding;
import pro.sketchware.utility.ThemeUtils;

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
    private final ExecutorService ollamaScanCoordinator = Executors.newSingleThreadExecutor();
    private static final OkHttpClient OLLAMA_SCAN_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(650, TimeUnit.MILLISECONDS)
            .readTimeout(650, TimeUnit.MILLISECONDS)
            .callTimeout(1200, TimeUnit.MILLISECONDS)
            .build();
    private volatile boolean ollamaScanInProgress = false;
    private boolean syncInProgress = false;
    // 0 = never checked, 1 = checking, 2 = success, 3 = failed.
    private int connectionUiState = 0;
    // Prevent programmatic profile loads from being treated as user edits.
    private boolean applyingProfileValues = false;

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
        setupInputListeners();
        setupAdvancedOptions();
    }

    private void setupInputListeners() {
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (applyingProfileValues) return;
                if (store.isVerified()) {
                    store.setVerified(false);
                }
                connectionUiState = 0;
                updateStatusUi();
            }
        };
        binding.editApiKey.addTextChangedListener(watcher);
        binding.editBaseUrl.addTextChangedListener(watcher);
        binding.editModel.addTextChangedListener(watcher);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStatusUi();
    }

    @Override
    public void onDestroy() {
        syncInProgress = false;
        ollamaScanCoordinator.shutdownNow();
        executor.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // Card 1: Status
    // ------------------------------------------------------------------

    private void setupStatusCard() {
        // The assistant is opt-in on a fresh install. Persisted choices survive app updates.
        binding.switchEnable.setChecked(store.isAssistantEnabled());
        binding.switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            store.setAssistantEnabled(isChecked);
            if (!isChecked) {
                // Agent tools cannot remain active while the assistant master switch is off.
                store.setAgentEnabled(false);
                binding.switchAgent.setOnCheckedChangeListener(null);
                binding.switchAgent.setChecked(false);
            }
            binding.switchAgent.setEnabled(isChecked);
            binding.cardProvider.setAlpha(isChecked ? 1f : 0.72f);
            binding.switchAgent.setOnCheckedChangeListener((v, checked) -> {
                if (!binding.switchEnable.isChecked() && checked) {
                    binding.switchAgent.setChecked(false);
                    return;
                }
                store.setAgentEnabled(checked);
            });
            updateStatusUi();
        });

        binding.switchAgent.setChecked(store.isAgentEnabled() && store.isAssistantEnabled());
        binding.switchAgent.setEnabled(store.isAssistantEnabled());
        binding.switchAgent.setOnCheckedChangeListener((v, checked) -> {
            if (!binding.switchEnable.isChecked() && checked) {
                binding.switchAgent.setChecked(false);
                return;
            }
            store.setAgentEnabled(checked);
        });
        binding.chipVerifiedStatus.setOnClickListener(v -> testConnection());
        updateStatusUi();
    }

    private void setupAdvancedOptions() {
        binding.switchStreaming.setChecked(store.isStreamingEnabled());
        binding.switchStreaming.setOnCheckedChangeListener((v, checked) -> store.setStreamingEnabled(checked));

        binding.switchThinking.setChecked(store.isShowThinking());
        binding.switchThinking.setOnCheckedChangeListener((v, checked) -> store.setShowThinking(checked));

        binding.switchAutoRetry.setChecked(store.isAutoRetry());
        binding.switchAutoRetry.setOnCheckedChangeListener((v, checked) -> store.setAutoRetry(checked));

        binding.switchAutoSuggest.setChecked(store.isAutoSuggestEnabled());
        binding.switchAutoSuggest.setOnCheckedChangeListener((v, checked) -> store.setAutoSuggestEnabled(checked));

        binding.switchAutoTestProvider.setChecked(store.isAutoTestProviderOnSelection());
        binding.switchAutoTestProvider.setOnCheckedChangeListener((v, checked) -> store.setAutoTestProviderOnSelection(checked));

        binding.switchHaptics.setChecked(store.isHapticsEnabled());
        binding.switchHaptics.setOnCheckedChangeListener((v, checked) -> store.setHapticsEnabled(checked));

        binding.sliderTemperature.setValue(store.getTemperature());
        binding.tvTemperatureValue.setText(String.format(Locale.getDefault(), "%.1f", store.getTemperature()));
        binding.sliderTemperature.addOnChangeListener((slider, value, fromUser) -> {
            store.setTemperature(value);
            binding.tvTemperatureValue.setText(String.format(Locale.getDefault(), "%.1f", value));
        });
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

        boolean verified = currentProfile != null && store.isVerifiedFor(currentProfile.id);
        int state = connectionUiState;
        if (state == 0 && verified) state = 2;
        int backgroundAttr = state == 2 ? R.attr.colorSecondaryContainer
                : state == 3 ? R.attr.colorErrorContainer
                : state == 1 ? R.attr.colorPrimaryContainer : R.attr.colorSurfaceContainerHigh;
        int foregroundAttr = state == 2 ? R.attr.colorOnSecondaryContainer
                : state == 3 ? R.attr.colorOnErrorContainer
                : state == 1 ? R.attr.colorOnPrimaryContainer : R.attr.colorOnSurfaceVariant;
        int label = state == 2 ? R.string.ai_premium_ready
                : state == 3 ? R.string.ai_connection_failed
                : state == 1 ? R.string.ai_connection_checking : R.string.ai_premium_setup;
        binding.chipVerifiedStatus.setText(label);
        binding.chipVerifiedStatus.setChipIconResource(state == 3 ? R.drawable.ic_ai_status_error
                : state == 1 ? R.drawable.ic_ai_status_pending
                : state == 2 ? R.drawable.ic_ai_premium : R.drawable.ic_ai_provider);
        binding.chipVerifiedStatus.setChipIconTint(android.content.res.ColorStateList.valueOf(
                ThemeUtils.getColor(this, foregroundAttr)));
        binding.chipVerifiedStatus.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                ThemeUtils.getColor(this, backgroundAttr)));
        binding.chipVerifiedStatus.setTextColor(ThemeUtils.getColor(this, foregroundAttr));
        binding.chipVerifiedStatus.setContentDescription(getString(state == 2
                ? R.string.ai_premium_ready_desc : state == 3 ? R.string.ai_connection_failed_desc
                : state == 1 ? R.string.ai_connection_checking_desc : R.string.ai_premium_setup_desc));
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
        ProviderIconAdapter adapter = new ProviderIconAdapter(this, profiles);
        binding.dropdownProvider.setAdapter(adapter);
        binding.dropdownProvider.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < profiles.size()) {
                ProviderProfile profile = profiles.get(position);
                // AutoCompleteTextView otherwise uses the profile object's default toString().
                binding.dropdownProvider.setText(profile.displayName, false);
                store.setProviderId(profile.id);
                applyProfile(profile);
                if (store.isAutoTestProviderOnSelection()) {
                    testConnection();
                }
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
        binding.btnScanOllama.setOnClickListener(v -> scanOllamaDevices());
    }
    private void applyProfile(ProviderProfile profile) {
        currentProfile = profile;
        connectionUiState = store.isVerifiedFor(profile.id) ? 2 : 0;
        updateStatusUi();

        applyingProfileValues = true;
        binding.editApiKey.setText(store.getKeyFor(profile.id));
        // Providers without API-key authentication should not reserve any screen space
        // for credentials. Restore the field automatically for key-based providers.
        binding.tilApiKey.setVisibility(profile.requiresKey ? View.VISIBLE : View.GONE);
        binding.tilApiKey.setEnabled(profile.requiresKey);
        if (!profile.requiresKey) {
            binding.tilApiKey.setError(null);
        }

        String savedUrl = store.getBaseUrlFor(profile.id);
        binding.editBaseUrl.setText(!savedUrl.isEmpty() ? savedUrl : profile.defaultBaseUrl);
        binding.tilBaseUrl.setEnabled(profile.baseUrlEditable);
        binding.tilBaseUrl.setError(null);
        // Ollama can run on a different device on the same LAN. Its URL remains
        // editable; use that device's LAN IP instead of localhost when remote.
        boolean isOllama = "ollama".equals(profile.id);
        binding.tilBaseUrl.setHelperText(isOllama
                ? "Ollama server URL, for example http://192.168.1.20:11434/v1"
                : null);
        binding.btnScanOllama.setVisibility(isOllama ? View.VISIBLE : View.GONE);
        binding.tvOllamaHelp.setVisibility(isOllama ? View.VISIBLE : View.GONE);

        String savedModel = store.getModelFor(profile.id);
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
        applyingProfileValues = false;
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


    /**
     * Searches the current Wi-Fi /24 range for reachable Ollama servers.
     * Discovery is opt-in, bounded to the local subnet, and performed off the UI thread.
     */
    private void scanOllamaDevices() {
        if (currentProfile == null || !"ollama".equals(currentProfile.id)) {
            return;
        }
        if (ollamaScanInProgress) {
            return;
        }

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        DhcpInfo dhcp = wifiManager == null ? null : wifiManager.getDhcpInfo();
        if (dhcp == null || dhcp.ipAddress == 0 || !wifiManager.isWifiEnabled()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Scan Devices")
                    .setMessage("Connect this phone to the same Wi-Fi network as the device running Ollama, then scan again. If Ollama runs on this phone, use http://127.0.0.1:11434/v1 instead.")
                    .setPositiveButton(R.string.common_word_ok, null)
                    .show();
            return;
        }

        // DhcpInfo stores IPv4 integers in little-endian byte order. Convert
        // both values before calculating the actual subnet instead of assuming /24.
        final int ownIpNetworkOrder = Integer.reverseBytes(dhcp.ipAddress);
        final int netmaskNetworkOrder = dhcp.netmask != 0
                ? Integer.reverseBytes(dhcp.netmask) : 0xFFFFFF00;
        final long networkAddress = ownIpNetworkOrder & netmaskNetworkOrder;
        final long broadcastAddress = networkAddress | (~netmaskNetworkOrder & 0xFFFFFFFFL);
        final long hostCountLong = broadcastAddress - networkAddress - 1L;
        // Keep discovery bounded: do not accidentally probe thousands of hosts on
        // a broad corporate or campus subnet.
        if (hostCountLong < 1L || hostCountLong > 1022L) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Network Too Large to Scan")
                    .setMessage("Automatic discovery is limited to subnets with up to 1,022 usable addresses. Enter the Ollama computer's IP address manually, or connect to a smaller private Wi-Fi subnet.")
                    .setPositiveButton(R.string.common_word_ok, null)
                    .show();
            return;
        }
        final int hostCount = (int) hostCountLong;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final androidx.appcompat.app.AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Scan Devices")
                .setMessage("Searching this Wi-Fi network for Ollama servers…\nKeep both devices connected to the same Wi-Fi.")
                .setNegativeButton("Cancel", (dialog, which) -> cancelled.set(true))
                .create();
        progressDialog.setOnCancelListener(dialog -> cancelled.set(true));
        progressDialog.show();
        ollamaScanInProgress = true;

        ollamaScanCoordinator.execute(() -> {
            List<OllamaDevice> found = Collections.synchronizedList(new ArrayList<>());
            ExecutorService workers = Executors.newFixedThreadPool(32);
            CountDownLatch finished = new CountDownLatch(hostCount);
            for (long offset = 1L; offset <= hostCount; offset++) {
                final int candidateNetworkOrder = (int) (networkAddress + offset);
                if (candidateNetworkOrder == ownIpNetworkOrder) {
                    finished.countDown();
                    continue;
                }
                final String ip = intToIp(Integer.reverseBytes(candidateNetworkOrder));
                workers.execute(() -> {
                    try {
                        if (!cancelled.get() && isOllamaAt(ip)) {
                            found.add(new OllamaDevice(ip, "http://" + ip + ":11434/v1"));
                        }
                    } catch (Exception ignored) {
                        // An offline device or a blocked port is an expected scan result.
                    } finally {
                        finished.countDown();
                    }
                });
            }
            try {
                finished.await(14, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancelled.set(true);
            } finally {
                workers.shutdownNow();
            }

            List<OllamaDevice> results = new ArrayList<>(found);
            results.sort((a, b) -> a.ip.compareTo(b.ip));
            runOnUiThread(() -> {
                ollamaScanInProgress = false;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                if (cancelled.get()) {
                    return;
                }
                if (results.isEmpty()) {
                    new MaterialAlertDialogBuilder(TagAssistantActivity.this)
                            .setTitle("No Ollama Devices Found")
                            .setMessage("No reachable Ollama server was found on this Wi-Fi subnet. Check that Ollama is running on the host, LAN access is enabled, the host firewall allows port 11434, and both devices are on the same Wi-Fi. You can still enter the Base URL manually.")
                            .setPositiveButton(R.string.common_word_ok, null)
                            .show();
                    return;
                }
                String[] labels = new String[results.size()];
                for (int i = 0; i < results.size(); i++) {
                    labels[i] = "Ollama server  •  " + results.get(i).ip;
                }
                new MaterialAlertDialogBuilder(TagAssistantActivity.this)
                        .setTitle("Ollama Devices (" + results.size() + ")")
                        .setMessage("Select a device to fill in Base URL.")
                        .setItems(labels, (dialog, which) -> {
                            if (which >= 0 && which < results.size()) {
                                OllamaDevice selected = results.get(which);
                                binding.editBaseUrl.setText(selected.baseUrl);
                                binding.tilBaseUrl.setError(null);
                                Snackbar.make(binding.getRoot(),
                                        "Base URL filled. Tap Sync Models to load models.",
                                        Snackbar.LENGTH_LONG).show();
                            }
                        })
                        .setNegativeButton(R.string.common_word_cancel, null)
                        .show();
            });
        });
    }

    private boolean isOllamaAt(String ip) {
        Request request = new Request.Builder()
                .url("http://" + ip + ":11434/api/tags")
                .get()
                .build();
        try (Response response = OLLAMA_SCAN_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return false;
            }
            // A successful HTTP response alone is not enough: confirm that this is
            // Ollama's documented model-list endpoint and not an unrelated web server.
            org.json.JSONObject payload = new org.json.JSONObject(response.body().string());
            return payload.optJSONArray("models") != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String intToIp(int address) {
        return (address & 0xff) + "." + ((address >> 8) & 0xff) + "."
                + ((address >> 16) & 0xff) + "." + ((address >> 24) & 0xff);
    }

    private static final class OllamaDevice {
        final String ip;
        final String baseUrl;

        OllamaDevice(String ip, String baseUrl) {
            this.ip = ip;
            this.baseUrl = baseUrl;
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
        if (!validateFields() || currentProfile == null) {
            return;
        }
        String key = AIConfigStore.sanitizeKey(textOf(binding.editApiKey));
        String url = AIConfigStore.sanitizeBaseUrl(textOf(binding.editBaseUrl));
        String model = textOf(binding.editModel).trim();

        store.setProviderId(currentProfile.id);
        store.setKeyFor(currentProfile.id, key);
        store.setBaseUrlFor(currentProfile.id, url);
        store.setModelFor(currentProfile.id, model);
        // A complete, explicitly saved configuration is a valid opt-in signal.
        // It does not claim the provider is verified; only Test connection can do that.
        store.setAssistantEnabled(true);
        binding.switchEnable.setOnCheckedChangeListener(null);
        binding.switchEnable.setChecked(true);
        binding.switchAgent.setEnabled(true);
        binding.cardProvider.setAlpha(1f);
        binding.switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            store.setAssistantEnabled(isChecked);
            if (!isChecked) {
                store.setAgentEnabled(false);
                binding.switchAgent.setOnCheckedChangeListener(null);
                binding.switchAgent.setChecked(false);
            }
            binding.switchAgent.setEnabled(isChecked);
            binding.cardProvider.setAlpha(isChecked ? 1f : 0.72f);
            binding.switchAgent.setOnCheckedChangeListener((v, checked) -> {
                if (!binding.switchEnable.isChecked() && checked) {
                    binding.switchAgent.setChecked(false);
                    return;
                }
                store.setAgentEnabled(checked);
            });
            updateStatusUi();
        });
        updateStatusUi();

        Snackbar.make(binding.getRoot(), R.string.ai_saved, Snackbar.LENGTH_SHORT).show();
    }

    /** Test connection now runs a real key validation check. */
    private void testConnection() {
        if (syncInProgress || !validateCredentials()) {
            return;
        }
        connectionUiState = 1;
        updateStatusUi();
        setSyncUi(true, true);
        ProviderProfile effective = resolveEffectiveProfile();
        String testedProviderId = effective.id;
        String key = AIConfigStore.sanitizeKey(textOf(binding.editApiKey));
        executor.execute(() -> {
            String verdict = ModelSyncService.validateKey(effective, key);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                boolean verified = verdict != null && verdict.startsWith("Key accepted by provider.");
                connectionUiState = verified ? 2 : 3;
                store.setVerifiedFor(testedProviderId, verified);
                setSyncUi(false, true);
                new MaterialAlertDialogBuilder(TagAssistantActivity.this)
                        .setTitle(R.string.ai_test_connection)
                        .setMessage(verdict)
                        .setPositiveButton(R.string.common_word_ok, null)
                        .show();
            });
        });
    }

    private void syncModels() {
        if (syncInProgress || !validateCredentials()) {
            return;
        }
        connectionUiState = 1;
        updateStatusUi();
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
                // A provider without a model-list endpoint is not a successful sync.
                // Preserve its last usable cache and do not mark the connection verified.
                if (result.modelListUnavailable) {
                    store.setVerifiedFor(effective.id, false);
                } else {
                    store.saveModelsCache(effective.id, result.models);
                    store.setVerifiedFor(effective.id, true);
                }
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    connectionUiState = result.modelListUnavailable ? 3 : 2;
                    setSyncUi(false, testMode);
                    updateStatusUi();
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
                store.setVerifiedFor(effective.id, false);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    connectionUiState = 3;
                    setSyncUi(false, testMode);
                    updateStatusUi();
                    
                    if (e.type == AIException.Type.NETWORK || e.type == AIException.Type.AUTH || e.type == AIException.Type.MODEL_NOT_FOUND) {
                        new MaterialAlertDialogBuilder(TagAssistantActivity.this)
                                .setTitle(R.string.ai_choose_model)
                                .setMessage(R.string.ai_no_model_list)
                                .setPositiveButton(R.string.common_word_close, null)
                                .show();
                    } else {
                        String message = e.friendlyMessage();
                        if (!e.rawBody.isEmpty()) {
                            message += "\n\n" + e.rawBody;
                        }
                        new MaterialAlertDialogBuilder(TagAssistantActivity.this)
                                .setTitle(R.string.ai_test_connection)
                                .setMessage(message)
                                .setPositiveButton(R.string.common_word_close, null)
                                .show();
                    }
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
        binding.btnTest.setEnabled(!inProgress);
        // Freeze the provider form while an operation is in flight so its result cannot
        // be applied to a different provider selected midway through the request.
        binding.dropdownProvider.setEnabled(!inProgress);
        binding.editApiKey.setEnabled(!inProgress);
        binding.editBaseUrl.setEnabled(!inProgress);
        binding.editModel.setEnabled(!inProgress);
        if (testMode) {
            binding.progressTest.setVisibility(inProgress ? View.VISIBLE : View.GONE);
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
