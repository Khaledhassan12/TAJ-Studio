package pro.sketchware.ai.ui.models;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.R;
import pro.sketchware.ai.models.ModelCatalog;
import pro.sketchware.ai.ui.AiHeaderInsets;
import pro.sketchware.ai.providers.AiIconLoader;
import pro.sketchware.ai.providers.ProviderConfig;
import pro.sketchware.ai.providers.ProviderRegistry;

/**
 * [WHAT] Rebuilt Models Activity with performance optimizations.
 * [WHY] Prevents ANRs with 1000+ models via ListAdapter + background processing (RISK-15).
 * [HOW] Async list building; debounced search; lazy expansion; background persistence.
 */
public class ModelsActivity extends BaseAppCompatActivity {

    private static final String DOCS_URL = "https://platform.openai.com/docs/models";

    private ModelCatalog catalog;
    private ProviderRegistry registry;
    private ModelListAdapter adapter;
    private LinearProgressIndicator loadingProgress;
    
    private final ExecutorService listExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<String> expandedGroups = new HashSet<>();
    private String currentSearch = "";
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private boolean isSyncing = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_models);
        AiHeaderInsets.apply(findViewById(R.id.app_bar));

        catalog = ModelCatalog.get(this);
        registry = ProviderRegistry.get(this);

        initUi();
        
        loadingProgress.setVisibility(View.VISIBLE);
        catalog.load(() -> {
            loadingProgress.setVisibility(View.GONE);
            rebuildList();
        });

        catalog.addListener(this::rebuildList);
        handleInsetts(findViewById(android.R.id.content));
    }

    private void initUi() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        loadingProgress = findViewById(R.id.loading_progress);
        
        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ModelListAdapter();
        recycler.setAdapter(adapter);

        findViewById(R.id.btn_docs).setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(DOCS_URL)));
        });
    }

    private void rebuildList() {
        String search = currentSearch;
        listExecutor.execute(() -> {
            List<ModelListItem> items = buildFlatList(search);
            mainHandler.post(() -> adapter.submitList(items));
        });
    }

    private List<ModelListItem> buildFlatList(String search) {
        List<ModelListItem> list = new ArrayList<>();

        list.add(new ModelListItem(ModelListItem.TYPE_SECTION_TITLE, "Default Model"));
        list.add(new ModelListItem(ModelListItem.TYPE_DEFAULT_CARD, catalog.getDefaultModel()));

        list.add(new ModelListItem(ModelListItem.TYPE_SECTION_TITLE, "Custom Models"));
        List<ModelCatalog.CustomModel> custom = catalog.getCustomModels();
        if (custom.isEmpty()) {
            list.add(new ModelListItem(ModelListItem.TYPE_EMPTY_CARD, "No custom models added"));
        } else {
            for (ModelCatalog.CustomModel cm : custom) {
                list.add(new ModelListItem(ModelListItem.TYPE_CUSTOM_ROW, cm));
            }
        }
        list.add(new ModelListItem(ModelListItem.TYPE_ADD_CUSTOM_BUTTON, null));

        list.add(new ModelListItem(ModelListItem.TYPE_SECTION_TITLE, "Fetched Models"));
        list.add(new ModelListItem(ModelListItem.TYPE_SYNC_CARD, null));
        list.add(new ModelListItem(ModelListItem.TYPE_SEARCH_CARD, null));

        List<ProviderConfig> configs = registry.loadAll();
        boolean hasAnyFetched = false;
        boolean isSearching = !search.isEmpty();

        for (ProviderConfig cfg : configs) {
            List<String> models = catalog.getFetchedModels(cfg.id);
            if (models.isEmpty()) continue;

            List<String> filtered = new ArrayList<>();
            for (String m : models) {
                if (m.toLowerCase().contains(search)) filtered.add(m);
            }

            if (!filtered.isEmpty()) {
                hasAnyFetched = true;
                int enabledInGroup = 0;
                for (String m : filtered) if (catalog.isEnabled(cfg.id, m)) enabledInGroup++;

                boolean expanded = isSearching || expandedGroups.contains(cfg.id);
                list.add(new ModelListItem(ModelListItem.TYPE_GROUP_HEADER, new GroupHeaderData(cfg, filtered.size(), enabledInGroup, expanded)));
                
                if (expanded) {
                    for (String mId : filtered) {
                        list.add(new ModelListItem(ModelListItem.TYPE_MODEL_ROW, new ModelRowData(cfg.id, mId, catalog.getAlias(cfg.id, mId), catalog.isEnabled(cfg.id, mId))));
                    }
                }
            }
        }

        if (!hasAnyFetched && isSearching) {
            list.add(new ModelListItem(ModelListItem.TYPE_EMPTY_CARD, "No models match search"));
        } else if (!hasAnyFetched) {
            list.add(new ModelListItem(ModelListItem.TYPE_EMPTY_CARD, "No fetched models"));
        }

        return list;
    }

    private void onGroupToggled(String providerId) {
        if (expandedGroups.contains(providerId)) expandedGroups.remove(providerId);
        else expandedGroups.add(providerId);
        rebuildList();
    }

    private void onSearchChanged(String query) {
        currentSearch = query.toLowerCase();
        if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
        searchRunnable = this::rebuildList;
        searchHandler.postDelayed(searchRunnable, 200);
    }

    private void onModelEnabledToggled(String pId, String mId, boolean enabled) {
        catalog.setEnabled(pId, mId, enabled);
    }

    private void syncAll() {
        if (isSyncing) return;
        isSyncing = true;
        rebuildList();

        new Thread(() -> {
            ModelCatalog.SyncReport report = catalog.syncAll();
            runOnUiThread(() -> {
                isSyncing = false;
                rebuildList();
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

    private void showAddCustomModelDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_add_custom_model, null);
        View selectorBox = v.findViewById(R.id.provider_selector_box);
        ImageView ivSelectedIcon = v.findViewById(R.id.iv_selected_provider_icon);
        TextView tvSelectedName = v.findViewById(R.id.tv_selected_provider_name);
        EditText etId = v.findViewById(R.id.et_model_id);
        EditText etAlias = v.findViewById(R.id.et_alias);

        List<ProviderConfig> configs = registry.loadAll();
        final int[] selectedIdx = {0};
        
        Runnable applySelected = () -> {
            ProviderConfig sel = configs.get(selectedIdx[0]);
            tvSelectedName.setText(sel.displayName);
            AiIconLoader.get(this).loadIcon(ivSelectedIcon, sel.id);
        };
        applySelected.run();

        selectorBox.setOnClickListener(view -> {
            ListPopupWindow popup = new ListPopupWindow(this);
            popup.setAnchorView(selectorBox);
            popup.setAdapter(new ProviderDropdownAdapter(this, configs));
            popup.setOnItemClickListener((parent, view1, position, id) -> {
                selectedIdx[0] = position;
                applySelected.run();
                popup.dismiss();
            });
            popup.show();
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Add Custom Model")
                .setView(v)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
        
        MaterialButton btnAdd = (MaterialButton) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        btnAdd.setEnabled(false);
        etId.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnAdd.setEnabled(!s.toString().trim().isEmpty());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnAdd.setOnClickListener(view -> {
            String pId = configs.get(selectedIdx[0]).id;
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
                .setPositiveButton("Save", (dialog, which) -> catalog.setAlias(providerId, modelId, et.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
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
            if (currentDef != null && currentDef.providerId.equals(e.providerId) && currentDef.modelId.equals(e.modelId)) selected = i;
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

    // --- Adapters & ViewHolders ---

    private class ModelListAdapter extends ListAdapter<ModelListItem, RecyclerView.ViewHolder> {
        ModelListAdapter() {
            super(new DiffUtil.ItemCallback<ModelListItem>() {
                @Override public boolean areItemsTheSame(@NonNull ModelListItem old, @NonNull ModelListItem newItem) {
                    if (old.type != newItem.type) return false;
                    if (old.type == ModelListItem.TYPE_GROUP_HEADER) return ((GroupHeaderData) old.data).cfg.id.equals(((GroupHeaderData) newItem.data).cfg.id);
                    if (old.type == ModelListItem.TYPE_MODEL_ROW) return ((ModelRowData) old.data).modelId.equals(((ModelRowData) newItem.data).modelId);
                    if (old.type == ModelListItem.TYPE_CUSTOM_ROW) return ((ModelCatalog.CustomModel) old.data).modelId.equals(((ModelCatalog.CustomModel) newItem.data).modelId);
                    return Objects.equals(old.data, newItem.data);
                }
                @Override public boolean areContentsTheSame(@NonNull ModelListItem old, @NonNull ModelListItem newItem) {
                    return old.equals(newItem);
                }
            });
        }

        @Override public int getItemViewType(int position) { return getItem(position).type; }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            switch (viewType) {
                case ModelListItem.TYPE_SECTION_TITLE: return new SectionTitleViewHolder(inflater.inflate(R.layout.item_section_title, parent, false));
                case ModelListItem.TYPE_DEFAULT_CARD: return new DefaultCardViewHolder(inflater.inflate(R.layout.item_default_model_card, parent, false));
                case ModelListItem.TYPE_EMPTY_CARD: return new EmptyCardViewHolder(inflater.inflate(R.layout.item_empty_card, parent, false));
                case ModelListItem.TYPE_CUSTOM_ROW: return new CustomRowViewHolder(inflater.inflate(R.layout.item_custom_model_row, parent, false));
                case ModelListItem.TYPE_ADD_CUSTOM_BUTTON: return new AddCustomViewHolder(inflater.inflate(R.layout.item_add_custom_card, parent, false));
                case ModelListItem.TYPE_SYNC_CARD: return new SyncCardViewHolder(inflater.inflate(R.layout.item_sync_card, parent, false));
                case ModelListItem.TYPE_SEARCH_CARD: return new SearchCardViewHolder(inflater.inflate(R.layout.item_search_card, parent, false));
                case ModelListItem.TYPE_GROUP_HEADER: return new GroupHeaderViewHolder(inflater.inflate(R.layout.item_model_group, parent, false));
                case ModelListItem.TYPE_MODEL_ROW: return new ModelRowViewHolder(inflater.inflate(R.layout.item_model_selectable_row, parent, false));
            }
            throw new RuntimeException("Unknown view type " + viewType);
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ModelListItem item = getItem(position);
            switch (item.type) {
                case ModelListItem.TYPE_SECTION_TITLE: ((SectionTitleViewHolder) holder).bind((String) item.data); break;
                case ModelListItem.TYPE_DEFAULT_CARD: ((DefaultCardViewHolder) holder).bind((ModelCatalog.ModelEntry) item.data); break;
                case ModelListItem.TYPE_EMPTY_CARD: ((EmptyCardViewHolder) holder).bind((String) item.data); break;
                case ModelListItem.TYPE_CUSTOM_ROW: ((CustomRowViewHolder) holder).bind((ModelCatalog.CustomModel) item.data); break;
                case ModelListItem.TYPE_ADD_CUSTOM_BUTTON: ((AddCustomViewHolder) holder).bind(); break;
                case ModelListItem.TYPE_SYNC_CARD: ((SyncCardViewHolder) holder).bind(); break;
                case ModelListItem.TYPE_SEARCH_CARD: ((SearchCardViewHolder) holder).bind(); break;
                case ModelListItem.TYPE_GROUP_HEADER: ((GroupHeaderViewHolder) holder).bind((GroupHeaderData) item.data); break;
                case ModelListItem.TYPE_MODEL_ROW: ((ModelRowViewHolder) holder).bind((ModelRowData) item.data); break;
            }
        }
    }

    private static class SectionTitleViewHolder extends RecyclerView.ViewHolder {
        SectionTitleViewHolder(View v) { super(v); }
        void bind(String title) { ((TextView) itemView).setText(title); }
    }

    private class DefaultCardViewHolder extends RecyclerView.ViewHolder {
        TextView name, provider;
        DefaultCardViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.tv_default_model_name);
            provider = v.findViewById(R.id.tv_default_model_provider);
            v.setOnClickListener(view -> showDefaultModelPicker());
        }
        void bind(ModelCatalog.ModelEntry data) {
            if (data != null) {
                name.setText(data.alias);
                provider.setText(data.providerId);
                provider.setVisibility(View.VISIBLE);
            } else {
                name.setText("No models enabled");
                provider.setVisibility(View.GONE);
            }
        }
    }

    private static class EmptyCardViewHolder extends RecyclerView.ViewHolder {
        EmptyCardViewHolder(View v) { super(v); }
        void bind(String text) { ((TextView) itemView.findViewById(R.id.tv_empty_text)).setText(text); }
    }

    private class CustomRowViewHolder extends RecyclerView.ViewHolder {
        TextView alias, meta; View edit, delete;
        CustomRowViewHolder(View v) {
            super(v);
            alias = v.findViewById(R.id.tv_alias);
            meta = v.findViewById(R.id.tv_meta);
            edit = v.findViewById(R.id.btn_edit);
            delete = v.findViewById(R.id.btn_delete);
        }
        void bind(ModelCatalog.CustomModel data) {
            alias.setText(data.alias != null && !data.alias.isEmpty() ? data.alias : data.modelId);
            meta.setText(data.provider + " · " + data.modelId);
            edit.setOnClickListener(v -> showEditAliasDialog(data.provider, data.modelId));
            delete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(ModelsActivity.this).setTitle("Delete").setMessage("Sure?").setPositiveButton("Yes", (d, w) -> catalog.deleteCustom(data.provider, data.modelId)).show();
            });
        }
    }

    private class AddCustomViewHolder extends RecyclerView.ViewHolder {
        AddCustomViewHolder(View v) { super(v); v.setOnClickListener(v1 -> showAddCustomModelDialog()); }
        void bind() {}
    }

    private class SyncCardViewHolder extends RecyclerView.ViewHolder {
        View progress; ImageView icon; TextView sub;
        SyncCardViewHolder(View v) {
            super(v);
            progress = v.findViewById(R.id.progress_sync);
            icon = v.findViewById(R.id.iv_sync_icon);
            sub = v.findViewById(R.id.tv_sync_subtitle);
            v.setOnClickListener(v1 -> syncAll());
        }
        void bind() {
            progress.setVisibility(isSyncing ? View.VISIBLE : View.GONE);
            icon.setVisibility(isSyncing ? View.GONE : View.VISIBLE);
            sub.setText(isSyncing ? "Syncing models..." : "Fetch the latest model list for all configured APIs");
        }
    }

    private class SearchCardViewHolder extends RecyclerView.ViewHolder {
        EditText et;
        SearchCardViewHolder(View v) {
            super(v);
            et = v.findViewById(R.id.et_search);
            et.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { onSearchChanged(s.toString()); }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
        void bind() { 
            // Avoid infinite loop by checking if text actually changed
            if (!et.getText().toString().equals(currentSearch)) {
                et.setText(currentSearch);
            }
        }
    }

    private class GroupHeaderViewHolder extends RecyclerView.ViewHolder {
        ImageView icon, chevron; TextView name, counts;
        GroupHeaderViewHolder(View v) {
            super(v);
            icon = v.findViewById(R.id.iv_provider_icon);
            chevron = v.findViewById(R.id.iv_chevron);
            name = v.findViewById(R.id.tv_provider_name);
            counts = v.findViewById(R.id.tv_counts);
        }
        void bind(GroupHeaderData data) {
            AiIconLoader.get(ModelsActivity.this).loadIcon(icon, data.cfg.id);
            name.setText(data.cfg.displayName);
            counts.setText(data.enabled + " enabled · " + data.total + " total");
            chevron.setRotation(data.expanded ? 180 : 0);
            itemView.findViewById(R.id.header).setOnClickListener(v1 -> onGroupToggled(data.cfg.id));
        }
    }

    private class ModelRowViewHolder extends RecyclerView.ViewHolder {
        TextView mid, alias; MaterialCheckBox cb; View edit;
        ModelRowViewHolder(View v) {
            super(v);
            mid = v.findViewById(R.id.tv_model_id);
            alias = v.findViewById(R.id.tv_alias);
            cb = v.findViewById(R.id.checkbox);
            edit = v.findViewById(R.id.btn_edit_alias);
        }
        void bind(ModelRowData data) {
            mid.setText(data.modelId);
            if (!data.alias.equals(data.modelId)) { alias.setText(data.alias); alias.setVisibility(View.VISIBLE); }
            else alias.setVisibility(View.GONE);
            cb.setOnCheckedChangeListener(null);
            cb.setChecked(data.enabled);
            cb.setOnCheckedChangeListener((v1, checked) -> onModelEnabledToggled(data.providerId, data.modelId, checked));
            edit.setOnClickListener(v1 -> showEditAliasDialog(data.providerId, data.modelId));
        }
    }

    private static class ProviderDropdownAdapter extends BaseAdapter {
        private final Context context;
        private final List<ProviderConfig> configs;
        ProviderDropdownAdapter(Context ctx, List<ProviderConfig> configs) { this.context = ctx; this.configs = configs; }
        @Override public int getCount() { return configs.size(); }
        @Override public Object getItem(int position) { return configs.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) convertView = LayoutInflater.from(context).inflate(R.layout.item_provider_dropdown_row, parent, false);
            ProviderConfig cfg = configs.get(position);
            ((TextView) convertView.findViewById(R.id.tv_name)).setText(cfg.displayName);
            AiIconLoader.get(context).loadIcon(convertView.findViewById(R.id.iv_icon), cfg.id);
            return convertView;
        }
    }

    // --- Domain Classes ---

    private static class GroupHeaderData {
        ProviderConfig cfg; int total, enabled; boolean expanded;
        GroupHeaderData(ProviderConfig cfg, int total, int enabled, boolean expanded) {
            this.cfg = cfg; this.total = total; this.enabled = enabled; this.expanded = expanded;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GroupHeaderData that = (GroupHeaderData) o;
            return total == that.total && enabled == that.enabled && expanded == that.expanded && Objects.equals(cfg.id, that.cfg.id);
        }
        @Override public int hashCode() { return Objects.hash(cfg.id, total, enabled, expanded); }
    }

    private static class ModelRowData {
        String providerId, modelId, alias; boolean enabled;
        ModelRowData(String pId, String mId, String alias, boolean enabled) {
            this.providerId = pId; this.modelId = mId; this.alias = alias; this.enabled = enabled;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ModelRowData that = (ModelRowData) o;
            return enabled == that.enabled && Objects.equals(providerId, that.providerId) && Objects.equals(modelId, that.modelId) && Objects.equals(alias, that.alias);
        }
        @Override public int hashCode() { return Objects.hash(providerId, modelId, alias, enabled); }
    }

    private static class ModelListItem {
        static final int TYPE_SECTION_TITLE = 0, TYPE_DEFAULT_CARD = 1, TYPE_EMPTY_CARD = 2, TYPE_CUSTOM_ROW = 3, 
                         TYPE_ADD_CUSTOM_BUTTON = 4, TYPE_SYNC_CARD = 5, TYPE_SEARCH_CARD = 6, TYPE_GROUP_HEADER = 7, TYPE_MODEL_ROW = 8;
        int type; Object data;
        ModelListItem(int type, Object data) { this.type = type; this.data = data; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ModelListItem that = (ModelListItem) o;
            return type == that.type && Objects.equals(data, that.data);
        }
        @Override public int hashCode() { return Objects.hash(type, data); }
    }
}
