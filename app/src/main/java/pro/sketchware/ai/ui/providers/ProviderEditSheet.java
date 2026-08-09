package pro.sketchware.ai.ui.providers;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import pro.sketchware.R;
import pro.sketchware.ai.data.Paths;
import pro.sketchware.ai.data.SecureKeyStore;
import pro.sketchware.ai.providers.ProviderConfig;
import pro.sketchware.ai.providers.ProviderRegistry;
import pro.sketchware.databinding.SheetProviderEditBinding;

/**
 * [WHAT] Bottom sheet for editing AI provider configuration.
 * [WHY] Allows testing, saving, and deleting provider metadata and keys.
 * [HOW] Uses OkHttp for real connection tests; SSOT for state updates.
 */
public class ProviderEditSheet extends BottomSheetDialogFragment {

    private static final String ARG_CONFIG_ID = "config_id";
    private SheetProviderEditBinding binding;
    private ProviderRegistry registry;
    private SecureKeyStore keyStore;
    private ProviderConfig config;
    private final OkHttpClient client = new OkHttpClient();
    private Call activeCall;

    public static ProviderEditSheet newInstance(@Nullable String configId) {
        ProviderEditSheet sheet = new ProviderEditSheet();
        Bundle args = new Bundle();
        args.putString(ARG_CONFIG_ID, configId);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetProviderEditBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        registry = ProviderRegistry.get(requireContext());
        keyStore = SecureKeyStore.get(requireContext());

        String configId = getArguments() != null ? getArguments().getString(ARG_CONFIG_ID) : null;
        if (configId == null) {
            // Create mode for openai-compatible
            config = new ProviderConfig(UUID.randomUUID().toString(), "New Custom Provider", ProviderConfig.TYPE_CLOUD, "compatible", "", false);
        } else {
            for (ProviderConfig c : registry.loadAll()) {
                if (c.id.equals(configId)) {
                    config = c;
                    break;
                }
            }
        }

        if (config == null) {
            dismiss();
            return;
        }

        applyEditState(config);

        binding.btnTest.setOnClickListener(v -> testConnection());
        binding.btnSave.setOnClickListener(v -> saveConfig());
        binding.btnDelete.setOnClickListener(v -> confirmDelete());
    }

    private void applyEditState(ProviderConfig cfg) {
        String configId = getArguments() != null ? getArguments().getString(ARG_CONFIG_ID) : null;
        binding.title.setText(configId == null ? "Add Provider" : "Edit Provider");
        binding.etDisplayName.setText(cfg.displayName);
        binding.etBaseUrl.setText(cfg.baseUrl);
        binding.switchEnabled.setChecked(cfg.enabled);
        
        binding.btnDelete.setVisibility(configId == null ? View.GONE : View.VISIBLE);
        binding.tiBaseUrl.setVisibility(cfg.providerType.equals("compatible") ? View.VISIBLE : View.GONE);

        if (cfg.hasKey) {
            binding.etApiKey.setHint("••••••••••••••••");
        } else {
            binding.etApiKey.setHint("Enter API Key");
        }
    }

    private void testConnection() {
        String key = binding.etApiKey.getText().toString().trim();
        if (TextUtils.isEmpty(key) && config.hasKey) {
            key = keyStore.getKey(config.id);
        }
        
        if (TextUtils.isEmpty(key) && !config.providerType.equals("llama-cpp")) {
            Toast.makeText(requireContext(), "API Key required for testing", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnTest.setEnabled(false);
        binding.btnTest.setText("Testing...");

        if (config.providerType.equals("llama-cpp")) {
            testLocal();
            return;
        }

        Request request;
        String baseUrl = binding.etBaseUrl.getText().toString().trim();
        if (TextUtils.isEmpty(baseUrl)) baseUrl = config.baseUrl;

        if (config.providerType.equals("openai") || config.providerType.equals("compatible")) {
            if (TextUtils.isEmpty(baseUrl)) baseUrl = "https://api.openai.com/v1";
            request = new Request.Builder()
                    .url(baseUrl + "/models")
                    .addHeader("Authorization", "Bearer " + key)
                    .build();
        } else if (config.providerType.equals("anthropic")) {
            String body = "{\"model\":\"claude-3-haiku-20240307\", \"max_tokens\":1, \"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
            request = new Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .addHeader("x-api-key", key)
                    .addHeader("anthropic-version", "2023-06-01")
                    .post(RequestBody.create(body, MediaType.parse("application/json")))
                    .build();
        } else if (config.providerType.equals("gemini")) {
            request = new Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models?key=" + key)
                    .build();
        } else {
            onTestResult(false, "Unknown provider type");
            return;
        }

        activeCall = client.newCall(request);
        activeCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (isAdded()) requireActivity().runOnUiThread(() -> onTestResult(false, "Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                boolean success = response.isSuccessful();
                String error = success ? null : "Error " + response.code();
                response.close();
                if (isAdded()) requireActivity().runOnUiThread(() -> onTestResult(success, error));
            }
        });
    }

    private void testLocal() {
        File[] files = Paths.modelsDir().listFiles((dir, name) -> name.endsWith(".gguf"));
        boolean success = files != null && files.length > 0;
        onTestResult(success, success ? null : "No .gguf models found in " + Paths.modelsDir().getName());
    }

    private void onTestResult(boolean success, @Nullable String error) {
        binding.btnTest.setEnabled(true);
        binding.btnTest.setText("Test Connection");
        
        config.lastTestedAt = System.currentTimeMillis();
        config.lastTestResult = success ? "success" : (error != null && error.contains("401") ? "auth_error" : "network_error");
        
        int color = success ? 0xFF4CAF50 : 0xFFF44336;
        String msg = success ? "Connection successful" : "Connection failed: " + error;
        
        Snackbar snack = Snackbar.make(binding.getRoot(), msg, Snackbar.LENGTH_LONG);
        snack.setBackgroundTint(color);
        snack.show();
    }

    private void saveConfig() {
        String displayName = binding.etDisplayName.getText().toString().trim();
        if (TextUtils.isEmpty(displayName)) {
            binding.etDisplayName.setError("Required");
            return;
        }

        config.displayName = displayName;
        config.baseUrl = binding.etBaseUrl.getText().toString().trim();
        config.enabled = binding.switchEnabled.isChecked();

        String key = binding.etApiKey.getText().toString().trim();
        if (!TextUtils.isEmpty(key)) {
            keyStore.putKey(config.id, key);
            config.hasKey = true;
        } else if (binding.etApiKey.getHint() != null && binding.etApiKey.getHint().toString().contains("Enter")) {
            keyStore.removeKey(config.id);
            config.hasKey = false;
        }

        registry.save(config);
        if (getActivity() instanceof ProvidersActivity) {
            ((ProvidersActivity) getActivity()).refreshData();
        }
        dismiss();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Provider")
                .setMessage("Delete this provider? This removes the saved API key.")
                .setPositiveButton("Delete", (d, w) -> {
                    registry.delete(config.id);
                    if (getActivity() instanceof ProvidersActivity) {
                        ((ProvidersActivity) getActivity()).refreshData();
                    }
                    dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        if (activeCall != null) activeCall.cancel();
        super.onDestroyView();
        binding = null;
    }
}
