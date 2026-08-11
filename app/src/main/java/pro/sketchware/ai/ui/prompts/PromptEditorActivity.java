package pro.sketchware.ai.ui.prompts;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
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
import pro.sketchware.ai.ui.AiHeaderInsets;

/**
 * [WHAT] Multi-section prompt template editor.
 * [WHY] Allows complex prompt construction with variables and live preview.
 * [HOW] R5 single-writer; Working copy draft lifecycle; Mode-based save.
 */
public class PromptEditorActivity extends BaseAppCompatActivity {

    private PromptTemplateStore store;
    private PromptTemplate template; // Working copy
    private String mode; // "create" or "edit"
    
    private int selectedSection = 0; // 0=System, 1=Prefix, 2=Suffix

    private EditText etTitle;
    private com.google.android.material.button.MaterialButton btnSectionSystem, btnSectionPrefix, btnSectionSuffix;
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
        AiHeaderInsets.apply(findViewById(R.id.app_bar));

        store = PromptTemplateStore.get(this);
        
        mode = getIntent().getStringExtra("mode");
        if ("create".equals(mode)) {
            String seedKind = getIntent().getStringExtra("seed");
            if ("default".equals(seedKind)) {
                template = store.createDefaultSeed("New Default", false);
            } else {
                template = new PromptTemplate("New Prompt", false);
            }
            isChanged = true; // New items are unsaved
        } else {
            String tid = getIntent().getStringExtra("template_id");
            PromptTemplate original = store.getById(tid);
            if (original != null) {
                template = original.deepCopy();
            }
        }

        if (template == null) {
            Toast.makeText(this, "Template load failed", Toast.LENGTH_SHORT).show();
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

        btnSectionSystem = findViewById(R.id.btn_section_system);
        btnSectionPrefix = findViewById(R.id.btn_section_prefix);
        btnSectionSuffix = findViewById(R.id.btn_section_suffix);

        btnSectionSystem.setOnClickListener(v -> { selectedSection = 0; applyEditorState(); });
        btnSectionPrefix.setOnClickListener(v -> { selectedSection = 1; applyEditorState(); });
        btnSectionSuffix.setOnClickListener(v -> { selectedSection = 2; applyEditorState(); });

        tvSectionDesc = findViewById(R.id.tv_section_desc);
        containerItems = findViewById(R.id.container_items);
        tvEmpty = findViewById(R.id.tv_empty);
        tvPreviewText = findViewById(R.id.tv_preview_text);

        findViewById(R.id.btn_docs).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/docs/models"));
            startActivity(intent);
        });
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
        
        if ("create".equals(mode)) {
            store.addTemplate(template);
        } else {
            store.updateTemplate(template);
        }
        
        isChanged = false;
        finish();
    }

    /**
     * [R5] Single writer for the editor state.
     */
    private void applyEditorState() {
        updateChips();
        List<PromptTemplate.Item> items = getSelectedList();
        String desc;
        if (selectedSection == 0) desc = "Sent as the system instruction.";
        else if (selectedSection == 1) desc = "Inserted before each user message.";
        else desc = "Inserted after each user message.";

        tvSectionDesc.setText(desc);
        containerItems.removeAllViews();
        
        addSeparator(0);
        if (items.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            for (int i = 0; i < items.size(); i++) {
                addItemView(items.get(i), i);
                addSeparator(i + 1);
            }
        }
        applyPreview();
    }

    private void updateChips() {
        int primaryContainer = ContextCompat.getColor(this, R.color.taj_ai_primary);
        int onPrimaryContainer = ContextCompat.getColor(this, android.R.color.white);
        int surface = ContextCompat.getColor(this, R.color.monokia_pro_black);
        int onSurface = ContextCompat.getColor(this, R.color.monokia_pro_grey);

        styleChip(btnSectionSystem, selectedSection == 0, primaryContainer, onPrimaryContainer, surface, onSurface);
        styleChip(btnSectionPrefix, selectedSection == 1, primaryContainer, onPrimaryContainer, surface, onSurface);
        styleChip(btnSectionSuffix, selectedSection == 2, primaryContainer, onPrimaryContainer, surface, onSurface);
    }

    private void styleChip(com.google.android.material.button.MaterialButton btn, boolean selected, int p, int op, int s, int os) {
        if (selected) {
            btn.setBackgroundColor(p);
            btn.setTextColor(op);
        } else {
            btn.setBackgroundColor(s);
            btn.setTextColor(os);
        }
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

        List<PromptTemplate.Item> list = getSelectedList();
        v.findViewById(R.id.btn_up).setVisibility(index > 0 ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.btn_up).setOnClickListener(view -> moveItem(index, -1));
        
        v.findViewById(R.id.btn_down).setVisibility(index < list.size() - 1 ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.btn_down).setOnClickListener(view -> moveItem(index, 1));
        
        v.findViewById(R.id.btn_delete).setOnClickListener(view -> {
            list.remove(index);
            isChanged = true;
            applyEditorState();
        });

        containerItems.addView(v);
    }

    private void addSeparator(int index) {
        View v = getLayoutInflater().inflate(R.layout.item_prompt_separator, containerItems, false);
        v.findViewById(R.id.btn_add).setOnClickListener(view -> showAddMenu(view, index));
        containerItems.addView(v);
    }

    private void showAddMenu(View anchor, int insertIndex) {
        View content = getLayoutInflater().inflate(R.layout.popup_add_item, null);
        PopupWindow popup = new PopupWindow(content, WRAP_CONTENT, WRAP_CONTENT, true);
        
        content.findViewById(R.id.btn_add_text).setOnClickListener(v -> {
            popup.dismiss();
            getSelectedList().add(insertIndex, new PromptTemplate.Item(PromptTemplate.ItemType.TEXT, ""));
            isChanged = true;
            applyEditorState();
        });
        content.findViewById(R.id.btn_add_variable).setOnClickListener(v -> {
            popup.dismiss();
            showChooseVarSheet(insertIndex);
        });

        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(12f);
        popup.showAsDropDown(anchor, 0, 4, Gravity.END);
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
        PromptVariables.ResolveCtx ctx = new PromptVariables.ResolveCtx(System.currentTimeMillis(), 
            ModelCatalog.get(this).getDefaultModel() != null ? ModelCatalog.get(this).getDefaultModel().modelId : null);
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
