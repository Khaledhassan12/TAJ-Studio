package pro.sketchware.ai.ui.providers;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.providers.AiIconLoader;
import pro.sketchware.ai.providers.ProviderConfig;
import pro.sketchware.ai.providers.ProviderRegistry;
import pro.sketchware.ai.ui.models.LocalModelsActivity;

/**
 * [WHAT] Rebuilt Providers management hub.
 * [WHY] Matches the approved M3 mockups exactly (Step 4 & 5).
 * [HOW] Dual lists; empty-state custom card; sparkle local row; floating documentation pill.
 */
public class ProvidersActivity extends BaseAppCompatActivity {

    private RecyclerView recyclerBuiltins;
    private RecyclerView recyclerCustom;
    private ProviderAdapter builtinsAdapter;
    private ProviderAdapter customAdapter;
    private ProviderRegistry registry;
    private TextView tvLocalSubtitle;
    private View cardEmptyCustom;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_providers);

        registry = ProviderRegistry.get(this);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        
        recyclerBuiltins = findViewById(R.id.recycler_builtins);
        recyclerCustom = findViewById(R.id.recycler_custom);
        tvLocalSubtitle = findViewById(R.id.tv_local_subtitle);
        cardEmptyCustom = findViewById(R.id.card_empty_custom);

        recyclerBuiltins.setLayoutManager(new LinearLayoutManager(this));
        builtinsAdapter = new ProviderAdapter(false);
        recyclerBuiltins.setAdapter(builtinsAdapter);

        recyclerCustom.setLayoutManager(new LinearLayoutManager(this));
        customAdapter = new ProviderAdapter(true);
        recyclerCustom.setAdapter(customAdapter);

        findViewById(R.id.btn_add_custom).setOnClickListener(v -> openAddProviderDialog());
        findViewById(R.id.btn_local).setOnClickListener(v -> startActivity(new Intent(this, LocalModelsActivity.class)));
        findViewById(R.id.btn_docs).setOnClickListener(v -> openDocumentationSheet());

        handleInsetts(findViewById(android.R.id.content));
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    public void refreshData() {
        List<ProviderConfig> all = registry.loadAll();
        List<ProviderConfig> builtins = new ArrayList<>();
        List<ProviderConfig> custom = new ArrayList<>();
        for (ProviderConfig cfg : all) {
            if (cfg.id.startsWith("custom_")) custom.add(cfg);
            else builtins.add(cfg);
        }
        builtinsAdapter.setItems(builtins);
        customAdapter.setItems(custom);
        
        applyProviderList(custom.isEmpty());
    }

    private void applyProviderList(boolean isCustomEmpty) {
        builtinsAdapter.notifyDataSetChanged();
        customAdapter.notifyDataSetChanged();
        
        cardEmptyCustom.setVisibility(isCustomEmpty ? View.VISIBLE : View.GONE);
        
        int modelCount = 0;
        try (android.database.Cursor c = AiStorage.get(this).listModels()) {
            if (c != null) modelCount = c.getCount();
        }
        tvLocalSubtitle.setText(modelCount + " local model(s)");
    }

    private void openAddProviderDialog() {
        AddProviderDialog dialog = new AddProviderDialog();
        dialog.show(getSupportFragmentManager(), "AddProviderDialog");
    }

    private void openDocumentationSheet() {
        DocumentationSheet sheet = new DocumentationSheet();
        sheet.show(getSupportFragmentManager(), "DocumentationSheet");
    }

    private class ProviderAdapter extends RecyclerView.Adapter<ProviderViewHolder> {
        private final List<ProviderConfig> items = new ArrayList<>();
        private final boolean isCustom;

        ProviderAdapter(boolean isCustom) { this.isCustom = isCustom; }

        void setItems(List<ProviderConfig> newItems) {
            items.clear();
            items.addAll(newItems);
        }

        @NonNull @Override public ProviderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ProviderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_provider_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ProviderViewHolder holder, int position) {
            ProviderConfig config = items.get(position);
            holder.name.setText(config.displayName);
            
            // Subtitle rule (Step 3)
            if (config.keyCount > 0) {
                holder.subtitle.setText(config.keyCount + " API key(s)");
            } else if (config.worksWithoutKey) {
                holder.subtitle.setText(registry.getBaseUrl(config.id));
            } else {
                holder.subtitle.setText("Not configured");
            }

            if (isCustom) {
                holder.icon.setImageResource(R.drawable.ic_mtrl_web);
            } else {
                AiIconLoader.get(ProvidersActivity.this).loadIcon(holder.icon, config.id);
            }
            
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ProvidersActivity.this, ProviderDetailActivity.class);
                intent.putExtra("provider_id", config.id);
                startActivity(intent);
            });
        }

        @Override public int getItemCount() { return items.size(); }
    }

    private static class ProviderViewHolder extends RecyclerView.ViewHolder {
        TextView name, subtitle;
        ImageView icon;
        ProviderViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.name);
            subtitle = v.findViewById(R.id.subtitle);
            icon = v.findViewById(R.id.icon);
        }
    }
}
