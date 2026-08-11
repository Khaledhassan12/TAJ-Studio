package pro.sketchware.ai.ui.providers;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.data.SecureKeyStore;
import pro.sketchware.ai.ui.AiHeaderInsets;
import pro.sketchware.ai.providers.ProviderConfig;
import pro.sketchware.ai.providers.ProviderRegistry;

/**
 * [WHAT] Detail screen for a specific AI provider.
 * [WHY] Allows base URL overrides and multiple API key management (Step 2).
 * [HOW] Real-time debounced URL saving; RecyclerView of keys; delete confirmation.
 */
public class ProviderDetailActivity extends BaseAppCompatActivity {

    private String providerId;
    private ProviderRegistry registry;
    private SecureKeyStore keyStore;
    private KeyAdapter adapter;
    private EditText edBaseUrl;
    private TextView tvTitle;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_detail);
        AiHeaderInsets.apply(findViewById(R.id.app_bar));

        providerId = getIntent().getStringExtra("provider_id");
        registry = ProviderRegistry.get(this);
        keyStore = SecureKeyStore.get(this);

        ProviderConfig config = registry.findById(providerId);
        if (config == null) {
            finish();
            return;
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        tvTitle = findViewById(R.id.tv_title);
        edBaseUrl = findViewById(R.id.ed_base_url);
        RecyclerView recyclerKeys = findViewById(R.id.recycler_keys);

        recyclerKeys.setLayoutManager(new LinearLayoutManager(this));
        adapter = new KeyAdapter();
        recyclerKeys.setAdapter(adapter);

        findViewById(R.id.btn_add_key).setOnClickListener(v -> openAddKeyDialog());

        applyDetailState(config);
        
        edBaseUrl.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                registry.setBaseUrlOverride(providerId, s.toString().trim());
            }
        });

        handleInsetts(findViewById(android.R.id.content));
    }

    private void applyDetailState(ProviderConfig config) {
        tvTitle.setText(config.displayName);
        edBaseUrl.setText(registry.getBaseUrl(providerId));
        refreshKeys();
    }

    private void refreshKeys() {
        List<SecureKeyStore.KeyInfo> keys = keyStore.listKeyNames(providerId);
        adapter.setItems(keys);
        adapter.notifyDataSetChanged();
        
        View cardNoKeys = findViewById(R.id.card_no_keys);
        if (cardNoKeys != null) {
            cardNoKeys.setVisibility(keys.isEmpty() ? View.VISIBLE : View.GONE);
            TextView tvNoKeys = findViewById(R.id.tv_no_keys);
            if (tvNoKeys != null) {
                ProviderConfig config = ProviderRegistry.get(this).findById(providerId);
                if (config != null) {
                    tvNoKeys.setText("No keys configured for " + config.displayName);
                }
            }
        }
    }

    private void openAddKeyDialog() {
        AddApiKeyDialog dialog = AddApiKeyDialog.newInstance(providerId);
        dialog.setListener(this::refreshKeys);
        dialog.show(getSupportFragmentManager(), "AddApiKeyDialog");
    }

    private class KeyAdapter extends RecyclerView.Adapter<KeyViewHolder> {
        private final List<SecureKeyStore.KeyInfo> items = new ArrayList<>();

        void setItems(List<SecureKeyStore.KeyInfo> newItems) {
            items.clear();
            items.addAll(newItems);
        }

        @NonNull @Override public KeyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new KeyViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_key_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull KeyViewHolder holder, int position) {
            SecureKeyStore.KeyInfo info = items.get(position);
            holder.name.setText(info.name);
            holder.btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(ProviderDetailActivity.this)
                        .setTitle("Delete Key")
                        .setMessage("Are you sure you want to delete this key?")
                        .setPositiveButton("Delete", (d, w) -> {
                            keyStore.removeKey(providerId, info.id);
                            refreshKeys();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        @Override public int getItemCount() { return items.size(); }
    }

    private static class KeyViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        View btnDelete;
        KeyViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.name);
            btnDelete = v.findViewById(R.id.btn_delete);
        }
    }
}
