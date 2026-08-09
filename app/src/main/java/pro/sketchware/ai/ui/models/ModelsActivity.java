package pro.sketchware.ai.ui.models;

import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.data.Paths;
import pro.sketchware.ai.models.AiModel;
import pro.sketchware.databinding.ActivityModelsBinding;
import pro.sketchware.databinding.ItemModelRowBinding;
import pro.sketchware.databinding.SheetModelConfigBinding;

/**
 * [WHAT] Activity for managing AI models.
 * [WHY] Allows enabling/disabling models and configuring their parameters.
 * [HOW] Lists cloud models from DB and local models from file system.
 */
public class ModelsActivity extends BaseAppCompatActivity {

    private ActivityModelsBinding binding;
    private final List<AiModel> models = new ArrayList<>();
    private ModelAdapter adapter;
    private AiStorage storage;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityModelsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        storage = AiStorage.get(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        handleInsetts(binding.getRoot());

        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ModelAdapter();
        binding.recycler.setAdapter(adapter);

        loadModels();
    }

    private void loadModels() {
        models.clear();

        // 1. Scan local directory for GGUF files
        File[] localFiles = Paths.modelsDir().listFiles((dir, name) -> name.endsWith(".gguf"));
        if (localFiles != null) {
            for (File f : localFiles) {
                AiModel m = new AiModel();
                m.id = f.getName().replace(".gguf", "");
                m.kind = AiModel.Kind.LOCAL;
                m.provider = "llama.cpp";
                m.name = f.getName();
                m.filePath = f.getAbsolutePath();
                m.sizeBytes = f.length();
                m.isActive = "true".equals(storage.kvGet("model_enabled_" + m.id));
                models.add(m);
            }
        }

        // 2. Load cloud models from DB
        try (Cursor c = storage.listModels()) {
            while (c.moveToNext()) {
                AiModel m = new AiModel();
                m.id = c.getString(c.getColumnIndexOrThrow("id"));
                m.kind = AiModel.Kind.valueOf(c.getString(c.getColumnIndexOrThrow("kind")));
                m.provider = c.getString(c.getColumnIndexOrThrow("provider"));
                m.name = c.getString(c.getColumnIndexOrThrow("name"));
                m.isActive = "true".equals(storage.kvGet("model_enabled_" + m.id));
                // Only add if not already in list (local might override cloud id if named same)
                boolean exists = false;
                for (AiModel existing : models) if (existing.id.equals(m.id)) { exists = true; break; }
                if (!exists) models.add(m);
            }
        }

        applyModelList();
    }

    private void applyModelList() {
        adapter.notifyDataSetChanged();
    }

    private void toggleModel(AiModel model, boolean enabled) {
        try {
            storage.kvPut("model_enabled_" + model.id, String.valueOf(enabled));
            model.isActive = enabled;
            // No need to refresh everything, but keep SSOT in sync
        } catch (Exception e) {
            Toast.makeText(this, "Failed to persist state", Toast.LENGTH_SHORT).show();
            applyModelList(); // Revert UI
        }
    }

    private void showConfigSheet(AiModel model) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        SheetModelConfigBinding configBinding = SheetModelConfigBinding.inflate(getLayoutInflater());
        dialog.setContentView(configBinding.getRoot());

        configBinding.modelName.setText(model.name);
        
        String ctxJson = storage.kvGet("model_config_" + model.id + "_ctx");
        float ctx = TextUtils.isEmpty(ctxJson) ? 2048f : Float.parseFloat(ctxJson);
        configBinding.sliderContext.setValue(ctx);

        String tempJson = storage.kvGet("model_config_" + model.id + "_temp");
        float temp = TextUtils.isEmpty(tempJson) ? 0.7f : Float.parseFloat(tempJson);
        configBinding.sliderTemp.setValue(temp);

        configBinding.etPromptOverride.setText(storage.kvGet("model_config_" + model.id + "_prompt"));

        configBinding.btnSave.setOnClickListener(v -> {
            storage.kvPut("model_config_" + model.id + "_ctx", String.valueOf(configBinding.sliderContext.getValue()));
            storage.kvPut("model_config_" + model.id + "_temp", String.valueOf(configBinding.sliderTemp.getValue()));
            storage.kvPut("model_config_" + model.id + "_prompt", configBinding.etPromptOverride.getText().toString());
            Toast.makeText(this, "Configuration saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private class ModelAdapter extends RecyclerView.Adapter<ModelViewHolder> {
        @NonNull @Override public ModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ModelViewHolder(ItemModelRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }
        @Override public void onBindViewHolder(@NonNull ModelViewHolder holder, int position) {
            AiModel m = models.get(position);
            holder.binding.name.setText(m.name);
            String size = m.sizeBytes > 0 ? " | " + (m.sizeBytes / 1024 / 1024) + "MB" : "";
            holder.binding.meta.setText(m.provider + size);
            
            holder.binding.switchEnabled.setOnCheckedChangeListener(null);
            holder.binding.switchEnabled.setChecked(m.isActive);
            holder.binding.switchEnabled.setOnCheckedChangeListener((v, checked) -> toggleModel(m, checked));
            
            holder.itemView.setOnClickListener(v -> showConfigSheet(m));
        }
        @Override public int getItemCount() { return models.size(); }
    }

    private static class ModelViewHolder extends RecyclerView.ViewHolder {
        ItemModelRowBinding binding;
        ModelViewHolder(ItemModelRowBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }
}
