package pro.sketchware.ai.ui.models;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.R;
import pro.sketchware.ai.models.ModelCatalog;
import pro.sketchware.ai.providers.AiIconLoader;
import pro.sketchware.ai.providers.ProviderConfig;
import pro.sketchware.ai.providers.ProviderRegistry;

/**
 * [WHAT] Models management screen.
 * [WHY] Replaces P1-A stub with literal mockup parity (Step 3).
 * [HOW] R5 single-writer; ModelCatalog SSOT; dynamic sections; SyncReport surfacing.
 */
public class ModelsActivity extends BaseAppCompatActivity {

    private static final String DOCS_URL = "https://platform.openai.com/docs/models";

    private ModelCatalog catalog;
    private ProviderRegistry registry;
    
    private TextView tvDefaultModelName;
    private TextView tvDefaultModelProvider;
    private View cardEmptyCustom;
    private RecyclerView recyclerCustom;
    private LinearLayout containerFetchedGroups;
    private TextView tvEmptyFetched;
    private EditText etSearch;
    private CircularProgressIndicator progressSync;
    private ImageView ivSyncIcon;
    private TextView tvSyncSubtitle;

    private CustomModelAdapter customAdapter;
    private String currentSearch = "";
    private final Map<String, Boolean> expandedGroups = new HashMap<>();
    private boolean isSyncing = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_models);

        catalog = ModelCatalog.get(this);
        registry = ProviderRegistry.get(this);

        initUi();
        refreshData();
        
        catalog.addListener(this::refreshData);
        handleInsetts(findViewById(android.R.id.content));
    }

    private void initUi() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        
        tvDefaultModelName = findViewById(R.id.tv_default_model_name);
        tvDefaultModelProvider = findViewById(R.id.tv_default_model_provider);
        findViewById(R.id.card_default_model).setOnClickListener(v -> showDefaultModelPicker());

        cardEmptyCustom = findViewById(R.id.card_empty_custom);
        recyclerCustom = findViewById(R.id.recycler_custom);
        recyclerCustom.setLayoutManager(new LinearLayoutManager(this));
        customAdapter = new CustomModelAdapter();
        recyclerCustom.setAdapter(customAdapter);
        
        findViewById(R.id.btn_add_custom).setOnClickListener(v -> showAddCustomModelDialog());

        containerFetchedGroups = findViewById(R.id.container_fetched_groups);
        tvEmptyFetched = findViewById(R.id.tv_empty_fetched);
        
        progressSync = findViewById(R.id.progress_sync);
        ivSyncIcon = findViewById(R.id.iv_sync_icon);
        tvSyncSubtitle = findViewById(R.id.tv_sync_subtitle);
        findViewById(R.id.btn_sync_all).setOnClickListener(v -> syncAll());

        etSearch = findViewById(R.id.et_search);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearch = s.toString().toLowerCase();
                renderFetchedSections();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        findViewById(R.id.btn_docs).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(DOCS_URL));
            startActivity(intent);
        });
    }

    private void refreshData() {
        applyModelsScreen();
    }

    /**
     * [R5] Single writer for the entire screen state.
     */
    private void applyModelsScreen() {
        // 1. Default Model
        ModelCatalog.ModelEntry def = catalog.getDefaultModel();
        if (def != null) {
            tvDefaultModelName.setText(def.alias);
            tvDefaultModelProvider.setText(def.providerId);
            tvDefaultModelProvider.setVisibility(View.VISIBLE);
        } else {
            tvDefaultModelName.setText("No models enabled");
            tvDefaultModelProvider.setVisibility(View.GONE);
        }

        // 2. Custom Models
        List<ModelCatalog.CustomModel> custom = catalog.getCustomModels();
        cardEmptyCustom.setVisibility(custom.isEmpty() ? View.VISIBLE : View.GONE);
        customAdapter.setItems(custom);
        
        // 3. Fetched Groups
        renderFetchedSections();
    }

    private void renderFetchedSections() {
        containerFetchedGroups.removeAllViews();
        List<ProviderConfig> configs = registry.loadAll();
        boolean hasAny = false;

        for (ProviderConfig cfg : configs) {
            List<String> models = catalog.getFetchedModels(cfg.id);
            List<String> filtered = new ArrayList<>();
            for (String m : models) {
                if (m.toLowerCase().contains(currentSearch)) filtered.add(m);
            }

            if (!filtered.isEmpty()) {
                hasAny = true;
                addFetchedGroup(cfg, filtered);
            }
        }

        tvEmptyFetched.setVisibility(!hasAny ? View.VISIBLE : View.GONE);
    }

    private void addFetchedGroup(ProviderConfig cfg, List<String> models) {
        View groupView = getLayoutInflater().inflate(R.layout.item_model_group, containerFetchedGroups, false);
        ImageView icon = groupView.findViewById(R.id.iv_provider_icon);
        TextView name = groupView.findViewById(R.id.tv_provider_name);
        TextView counts = groupView.findViewById(R.id.tv_counts);
        ImageView chevron = groupView.findViewById(R.id.iv_chevron);
        RecyclerView recycler = groupView.findViewById(R.id.recycler_models);

        AiIconLoader.get(this).loadIcon(icon, cfg.id);
        name.setText(cfg.displayName);
        
        int enabledCount = 0;
        for (String m : models) if (catalog.isEnabled(cfg.id, m)) enabledCount++;
        counts.setText(enabledCount + " enabled · " + models.size() + " total");

        boolean expanded = Boolean.TRUE.equals(expandedGroups.get(cfg.id));
        recycler.setVisibility(expanded ? View.VISIBLE : View.GONE);
        chevron.setRotation(expanded ? 180 : 0);

        groupView.findViewById(R.id.header).setOnClickListener(v -> {
            boolean next = !Boolean.TRUE.equals(expandedGroups.get(cfg.id));
            expandedGroups.put(cfg.id, next);
            renderFetchedSections(); // Re-render to update rotation/visibility
        });

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(new FetchedModelAdapter(cfg.id, models));
        
        containerFetchedGroups.addView(groupView);
    }

    private void showDefaultModelPicker() {
        List<ModelCatalog.ModelEntry> usable = catalog.usableModels();
        if (usable.isEmpty()) {
            Toast.makeText(this, "Enable at least one model first", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = new String[usable.size()];
        int selected = -1;
        ModelCatalog.ModelEntry currentDef = catalog.getDefaultModel();
        
        for (int i = 0; i < usable.size(); i++) {
            ModelCatalog.ModelEntry e = usable.get(i);
            items[i] = e.alias + " (" + e.providerId + ")";
            if (currentDef != null && currentDef.providerId.equals(e.providerId) && currentDef.modelId.equals(e.modelId)) {
                selected = i;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Default Model")
                .setSingleChoiceItems(items, selected, (dialog, which) -> {
                    ModelCatalog.ModelEntry e = usable.get(which);
                    catalog.setDefault(e.providerId, e.modelId);
                    dialog.dismiss();
                })
                .show();
    }

    private void showAddCustomModelDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_add_custom_model, null);
        Spinner spinner = v.findViewById(R.id.spinner_provider);
        EditText etId = v.findViewById(R.id.et_model_id);
        EditText etAlias = v.findViewById(R.id.et_alias);

        List<ProviderConfig> configs = registry.loadAll();
        List<String> names = new ArrayList<>();
        for (ProviderConfig cfg : configs) names.add(cfg.displayName);
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Add Custom Model")
                .setView(v)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
        
        MaterialButton btnAdd = (MaterialButton) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        btnAdd.setEnabled(false);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnAdd.setEnabled(!etId.getText().toString().trim().isEmpty());
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        etId.addTextChangedListener(watcher);

        btnAdd.setOnClickListener(view -> {
            String pId = configs.get(spinner.getSelectedItemPosition()).id;
            String mId = etId.getText().toString().trim();
            String alias = etAlias.getText().toString().trim();
            try {
                catalog.addCustom(pId, mId, alias);
                dialog.dismiss();
            } catch (Exception e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditAliasDialog(String providerId, String modelId) {
        EditText et = new EditText(this);
        et.setHint("Alias");
        et.setText(catalog.getAlias(providerId, modelId));
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        
        FrameLayout container = new FrameLayout(this);
        container.setPadding(padding, padding, padding, padding);
        container.addView(et);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Set Alias")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    catalog.setAlias(providerId, modelId, et.getText().toString().trim());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void syncAll() {
        if (isSyncing) return;
        isSyncing = true;
        progressSync.setVisibility(View.VISIBLE);
        ivSyncIcon.setVisibility(View.GONE);
        tvSyncSubtitle.setText("Syncing models...");

        new Thread(() -> {
            ModelCatalog.SyncReport report = catalog.syncAll();
            runOnUiThread(() -> {
                isSyncing = false;
                progressSync.setVisibility(View.GONE);
                ivSyncIcon.setVisibility(View.VISIBLE);
                tvSyncSubtitle.setText("Fetch the latest model list for all configured APIs");
                
                StringBuilder sb = new StringBuilder("Synced " + report.ok.size() + " providers.");
                if (!report.failed.isEmpty()) {
                    sb.append("\nFailures:");
                    for (Map.Entry<String, String> e : report.failed.entrySet()) {
                        sb.append("\n· ").append(e.getKey()).append(": ").append(e.getValue());
                    }
                }
                Snackbar.make(findViewById(android.R.id.content), sb.toString(), Snackbar.LENGTH_LONG).show();
            });
        }).start();
    }

    // --- Adapters ---

    private class CustomModelAdapter extends RecyclerView.Adapter<CustomModelViewHolder> {
        private final List<ModelCatalog.CustomModel> items = new ArrayList<>();
        void setItems(List<ModelCatalog.CustomModel> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }
        @NonNull @Override public CustomModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new CustomModelViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_custom_model_row, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull CustomModelViewHolder holder, int position) {
            ModelCatalog.CustomModel m = items.get(position);
            holder.alias.setText(m.alias != null && !m.alias.isEmpty() ? m.alias : m.modelId);
            holder.meta.setText(m.provider + " · " + m.modelId);
            holder.btnEdit.setOnClickListener(v -> showEditAliasDialog(m.provider, m.modelId));
            holder.btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(ModelsActivity.this)
                        .setTitle("Delete Custom Model")
                        .setMessage("Are you sure?")
                        .setPositiveButton("Delete", (d, w) -> catalog.deleteCustom(m.provider, m.modelId))
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }
        @Override public int getItemCount() { return items.size(); }
    }

    private static class CustomModelViewHolder extends RecyclerView.ViewHolder {
        TextView alias, meta;
        View btnEdit, btnDelete;
        CustomModelViewHolder(View v) {
            super(v);
            alias = v.findViewById(R.id.tv_alias);
            meta = v.findViewById(R.id.tv_meta);
            btnEdit = v.findViewById(R.id.btn_edit);
            btnDelete = v.findViewById(R.id.btn_delete);
        }
    }

    private class FetchedModelAdapter extends RecyclerView.Adapter<FetchedModelViewHolder> {
        private final String providerId;
        private final List<String> models;
        FetchedModelAdapter(String pId, List<String> models) { this.providerId = pId; this.models = models; }
        @NonNull @Override public FetchedModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new FetchedModelViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_model_selectable_row, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull FetchedModelViewHolder holder, int position) {
            String mId = models.get(position);
            holder.modelId.setText(mId);
            String alias = catalog.getAlias(providerId, mId);
            if (!alias.equals(mId)) {
                holder.alias.setText(alias);
                holder.alias.setVisibility(View.VISIBLE);
            } else {
                holder.alias.setVisibility(View.GONE);
            }
            holder.checkbox.setOnCheckedChangeListener(null);
            holder.checkbox.setChecked(catalog.isEnabled(providerId, mId));
            holder.checkbox.setOnCheckedChangeListener((v, checked) -> catalog.setEnabled(providerId, mId, checked));
            holder.btnEdit.setOnClickListener(v -> showEditAliasDialog(providerId, mId));
        }
        @Override public int getItemCount() { return models.size(); }
    }

    private static class FetchedModelViewHolder extends RecyclerView.ViewHolder {
        TextView modelId, alias;
        MaterialCheckBox checkbox;
        View btnEdit;
        FetchedModelViewHolder(View v) {
            super(v);
            modelId = v.findViewById(R.id.tv_model_id);
            alias = v.findViewById(R.id.tv_alias);
            checkbox = v.findViewById(R.id.checkbox);
            btnEdit = v.findViewById(R.id.btn_edit_alias);
        }
    }
}
