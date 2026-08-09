package pro.sketchware.ai.ui.prompts;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.models.ModelCatalog;
import pro.sketchware.ai.prompts.PromptTemplate;
import pro.sketchware.ai.prompts.PromptTemplateStore;
import pro.sketchware.ai.prompts.PromptVariables;

/**
 * [WHAT] Multi-section prompt template editor.
 * [WHY] Allows complex prompt construction with variables and live preview (Step 5).
 * [HOW] R5 single-writer; Item-based editing; Reordering logic.
 */
public class PromptEditorActivity extends BaseAppCompatActivity {

    private PromptTemplateStore store;
    private PromptTemplate template;
    private int selectedSection = 0; // 0=System, 1=Prefix, 2=Suffix

    private EditText etTitle;
    private ChipGroup chipGroup;
    private TextView tvSectionDesc;
    private LinearLayout containerItems;
    private View tvEmpty;
    private TextView tvPreviewText;

    private boolean isChanged = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prompt_editor);

        store = PromptTemplateStore.get(this);
        String tid = getIntent().getStringExtra("template_id");
        
        // Find in store
        for (PromptTemplate t : store.loadAll()) {
            if (t.id.equals(tid)) {
                template = t;
                break;
            }
        }
        if (template == null) {
            Toast.makeText(this, "Template not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initUi();
        applyEditorState();
        handleInsetts(findViewById(android.R.id.content));
    }

    private void initUi() {
        findViewById(R.id.btn_back).setOnClickListener(v -> handleBack());
        findViewById(R.id.btn_save).setOnClickListener(v -> save());

        etTitle = findViewById(R.id.et_title);
        etTitle.setText(template.title);
        etTitle.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                template.title = s.toString().trim();
                isChanged = true;
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        chipGroup = findViewById(R.id.chip_group_sections);
        chipGroup.check(R.id.chip_system);
        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chip_system) selectedSection = 0;
            else if (checkedId == R.id.chip_prefix) selectedSection = 1;
            else if (checkedId == R.id.chip_suffix) selectedSection = 2;
            applyEditorState();
        });

        tvSectionDesc = findViewById(R.id.tv_section_desc);
        containerItems = findViewById(R.id.container_items);
        tvEmpty = findViewById(R.id.tv_empty);
        tvPreviewText = findViewById(R.id.tv_preview_text);
    }

    private void handleBack() {
        if (isChanged) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Discard changes?")
                    .setMessage("You have unsaved changes.")
                    .setPositiveButton("Discard", (d, w) -> finish())
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            finish();
        }
    }

    private void save() {
        if (template.title.isEmpty()) {
            ((com.google.android.material.textfield.TextInputLayout) findViewById(R.id.til_title)).setError("Title required");
            return;
        }
        List<PromptTemplate> all = store.loadAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(template.id)) {
                all.set(i, template);
                break;
            }
        }
        store.saveAll(all);
        Snackbar.make(containerItems, "Saved", Snackbar.LENGTH_SHORT).show();
        isChanged = false;
        finish();
    }

    /**
     * [R5] Single writer for the editor state.
     */
    private void applyEditorState() {
        List<PromptTemplate.Item> items;
        String desc;
        if (selectedSection == 0) {
            items = template.system;
            desc = "Sent as the system instruction.";
        } else if (selectedSection == 1) {
            items = template.prefix;
            desc = "Inserted before each user message.";
        } else {
            items = template.suffix;
            desc = "Inserted after each user message.";
        }

        tvSectionDesc.setText(desc);
        containerItems.removeAllViews();
        
        if (items.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            addSeparator(0);
        } else {
            tvEmpty.setVisibility(View.GONE);
            addSeparator(0);
            for (int i = 0; i < items.size(); i++) {
                addItemView(items.get(i), i);
                addSeparator(i + 1);
            }
        }

        applyPreview();
    }

    private void addItemView(PromptTemplate.Item item, int index) {
        View v;
        if (item.type == PromptTemplate.ItemType.TEXT) {
            v = getLayoutInflater().inflate(R.layout.item_prompt_text, containerItems, false);
            EditText et = v.findViewById(R.id.et_text);
            et.setText(item.text);
            et.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    item.text = s.toString();
                    isChanged = true;
                    applyPreview();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        } else {
            v = getLayoutInflater().inflate(R.layout.item_prompt_var, containerItems, false);
            PromptVariables.Variable var = null;
            for (PromptVariables.Variable c : PromptVariables.getCatalog()) {
                if (c.key.equals(item.varKey)) { var = c; break; }
            }
            if (var != null) {
                ((TextView) v.findViewById(R.id.tv_name)).setText(var.displayName);
                ((TextView) v.findViewById(R.id.tv_key)).setText("{" + var.key + "}");
                ((ImageView) v.findViewById(R.id.iv_icon)).setImageResource(var.iconRes);
            }
        }

        v.findViewById(R.id.btn_up).setEnabled(index > 0);
        v.findViewById(R.id.btn_up).setOnClickListener(view -> moveItem(index, -1));
        
        List<PromptTemplate.Item> items = getSelectedList();
        v.findViewById(R.id.btn_down).setEnabled(index < items.size() - 1);
        v.findViewById(R.id.btn_down).setOnClickListener(view -> moveItem(index, 1));
        
        v.findViewById(R.id.btn_delete).setOnClickListener(view -> {
            items.remove(index);
            isChanged = true;
            applyEditorState();
        });

        containerItems.addView(v);
    }

    private void addSeparator(int index) {
        View v = getLayoutInflater().inflate(R.layout.item_prompt_separator, containerItems, false);
        v.findViewById(R.id.btn_add).setOnClickListener(view -> {
            PopupMenu popup = new PopupMenu(this, view);
            popup.getMenu().add("Add Text");
            popup.getMenu().add("Add Variable");
            popup.setOnMenuItemClickListener(item -> {
                if ("Add Text".equals(item.getTitle())) {
                    getSelectedList().add(index, new PromptTemplate.Item(PromptTemplate.ItemType.TEXT, ""));
                    isChanged = true;
                    applyEditorState();
                } else {
                    showChooseVarSheet(index);
                }
                return true;
            });
            popup.show();
        });
        containerItems.addView(v);
    }

    private void showChooseVarSheet(int index) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = getLayoutInflater().inflate(R.layout.sheet_choose_variable, null);
        sheet.setContentView(v);

        RecyclerView rv = v.findViewById(R.id.recycler_vars);
        rv.setLayoutManager(new LinearLayoutManager(this));
        
        List<PromptVariables.Variable> vars = PromptVariables.getCatalog();
        rv.setAdapter(new VarAdapter(vars, var -> {
            getSelectedList().add(index, new PromptTemplate.Item(PromptTemplate.ItemType.VAR, var.key));
            isChanged = true;
            sheet.dismiss();
            applyEditorState();
        }));

        sheet.show();
    }

    private void moveItem(int index, int direction) {
        List<PromptTemplate.Item> list = getSelectedList();
        Collections.swap(list, index, index + direction);
        isChanged = true;
        applyEditorState();
    }

    private List<PromptTemplate.Item> getSelectedList() {
        if (selectedSection == 0) return template.system;
        if (selectedSection == 1) return template.prefix;
        return template.suffix;
    }

    private void applyPreview() {
        PromptVariables.ResolveCtx ctx = new PromptVariables.ResolveCtx(System.currentTimeMillis(), ModelCatalog.get(this).getDefaultModel() != null ? ModelCatalog.get(this).getDefaultModel().modelId : null);
        ctx.isPreview = true;
        
        String preview = PromptVariables.resolveList(getSelectedList(), ctx, AiStorage.get(this));
        tvPreviewText.setText(preview.isEmpty() ? "(empty)" : preview);
    }

    private interface VarListener { void onVarSelected(PromptVariables.Variable var); }

    private class VarAdapter extends RecyclerView.Adapter<VarViewHolder> {
        private final List<PromptVariables.Variable> items;
        private final VarListener listener;
        VarAdapter(List<PromptVariables.Variable> items, VarListener listener) { this.items = items; this.listener = listener; }
        @NonNull @Override public VarViewHolder onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new VarViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_variable_row, p, false));
        }
        @Override public void onBindViewHolder(@NonNull VarViewHolder h, int pos) {
            PromptVariables.Variable v = items.get(pos);
            h.name.setText(v.displayName);
            h.key.setText("{" + v.key + "}");
            h.icon.setImageResource(v.iconRes);
            h.itemView.setOnClickListener(v1 -> listener.onVarSelected(v));
        }
        @Override public int getItemCount() { return items.size(); }
    }

    private static class VarViewHolder extends RecyclerView.ViewHolder {
        TextView name, key; ImageView icon;
        VarViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.tv_name);
            key = v.findViewById(R.id.tv_key);
            icon = v.findViewById(R.id.iv_icon);
        }
    }
}
