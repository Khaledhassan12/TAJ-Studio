package pro.sketchware.ai.ui.prompts;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import pro.sketchware.R;
import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.models.SavedPrompt;
import pro.sketchware.databinding.ActivitySystemPromptsBinding;
import pro.sketchware.databinding.ItemPromptRowBinding;
import pro.sketchware.databinding.ActivityPromptEditorBinding;

/**
 * [WHAT] Activity for managing system prompt templates.
 * [WHY] Allows users to save and reuse specific model instructions.
 * [HOW] Lists saved prompts from DB (kv table) and provides a multi-line editor.
 */
public class SystemPromptsActivity extends BaseAppCompatActivity {

    private ActivitySystemPromptsBinding binding;
    private final List<SavedPrompt> prompts = new ArrayList<>();
    private PromptAdapter adapter;
    private AiStorage storage;
    private final Gson gson = new Gson();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivitySystemPromptsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        storage = AiStorage.get(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        handleInsetts(binding.getRoot());

        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PromptAdapter();
        binding.recycler.setAdapter(adapter);

        binding.fabCreate.setOnClickListener(v -> showEditorSheet(null));

        loadPrompts();
    }

    private void loadPrompts() {
        String json = storage.kvGet("ai_prompts_list");
        prompts.clear();
        if (!TextUtils.isEmpty(json)) {
            List<SavedPrompt> loaded = gson.fromJson(json, new TypeToken<List<SavedPrompt>>(){}.getType());
            if (loaded != null) prompts.addAll(loaded);
        }
        applyPromptList();
    }

    private void savePrompts() {
        try {
            storage.kvPut("ai_prompts_list", gson.toJson(prompts));
            applyPromptList();
        } catch (Exception e) {
            Toast.makeText(this, R.string.ai_msg_save_failed, Toast.LENGTH_SHORT).show();
            loadPrompts(); // Revert
        }
    }

    private void applyPromptList() {
        adapter.notifyDataSetChanged();
    }

    private void showEditorSheet(@Nullable SavedPrompt existing) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        ActivityPromptEditorBinding editorBinding = ActivityPromptEditorBinding.inflate(getLayoutInflater());
        dialog.setContentView(editorBinding.getRoot());

        if (existing != null) {
            editorBinding.etName.setText(existing.name);
            editorBinding.etContent.setText(existing.content);
        }

        editorBinding.btnSave.setOnClickListener(v -> {
            String name = editorBinding.etName.getText().toString().trim();
            String content = editorBinding.etContent.getText().toString().trim();

            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(content)) {
                Toast.makeText(this, "Name and Content are required", Toast.LENGTH_SHORT).show();
                return;
            }

            SavedPrompt prompt = existing != null ? existing : new SavedPrompt();
            if (existing == null) {
                prompt.id = UUID.randomUUID().toString();
                prompts.add(prompt);
            }
            prompt.name = name;
            prompt.content = content;

            savePrompts();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void deletePrompt(SavedPrompt prompt) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.ai_dialog_confirm_delete)
                .setPositiveButton("Delete", (d, w) -> {
                    prompts.remove(prompt);
                    savePrompts();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class PromptAdapter extends RecyclerView.Adapter<PromptViewHolder> {
        @NonNull @Override public PromptViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PromptViewHolder(ItemPromptRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }
        @Override public void onBindViewHolder(@NonNull PromptViewHolder holder, int position) {
            SavedPrompt p = prompts.get(position);
            holder.binding.name.setText(p.name);
            holder.binding.preview.setText(p.content.length() > 50 ? p.content.substring(0, 50) + "..." : p.content);
            holder.binding.btnDelete.setOnClickListener(v -> deletePrompt(p));
            holder.itemView.setOnClickListener(v -> showEditorSheet(p));
        }
        @Override public int getItemCount() { return prompts.size(); }
    }

    private static class PromptViewHolder extends RecyclerView.ViewHolder {
        ItemPromptRowBinding binding;
        PromptViewHolder(ItemPromptRowBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }
}
