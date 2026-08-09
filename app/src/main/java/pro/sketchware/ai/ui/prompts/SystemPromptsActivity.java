package pro.sketchware.ai.ui.prompts;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.prompts.PromptTemplate;
import pro.sketchware.ai.prompts.PromptTemplateStore;

/**
 * [WHAT] System Prompts list management.
 * [WHY] Stabilization + Exact Mockup Parity (P1-K).
 * [HOW] Replaces ListPopupWindow with PopupWindow to avoid NPE; literal mockup layouts.
 */
public class SystemPromptsActivity extends BaseAppCompatActivity {

    private PromptTemplateStore store;
    private RecyclerView recycler;
    private PromptAdapter adapter;
    private String activeId;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_system_prompts);

        store = PromptTemplateStore.get(this);
        
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        
        recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PromptAdapter();
        recycler.setAdapter(adapter);

        findViewById(R.id.btn_add_prompt).setOnClickListener(v -> showAddPromptSheet());

        findViewById(R.id.btn_docs).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/docs/models"));
            startActivity(intent);
        });

        store.addListener(this::refreshData);
        handleInsetts(findViewById(android.R.id.content));
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    private void refreshData() {
        activeId = store.getActiveId();
        applyTemplateList(store.loadAll());
    }

    private void applyTemplateList(List<PromptTemplate> list) {
        adapter.setItems(list);
    }

    private void showAddPromptSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = getLayoutInflater().inflate(R.layout.sheet_choose_template, null);
        sheet.setContentView(v);

        v.findViewById(R.id.btn_blank).setOnClickListener(v1 -> {
            sheet.dismiss();
            Intent intent = new Intent(this, PromptEditorActivity.class);
            intent.putExtra("mode", "create");
            intent.putExtra("seed", "blank");
            startActivity(intent);
        });

        v.findViewById(R.id.btn_default).setOnClickListener(v1 -> {
            sheet.dismiss();
            Intent intent = new Intent(this, PromptEditorActivity.class);
            intent.putExtra("mode", "create");
            intent.putExtra("seed", "default");
            startActivity(intent);
        });

        // User icons mapping (Verification)
        ((ImageView) v.findViewById(R.id.iv_blank_icon)).setImageResource(R.drawable.ic_mtrl_add);
        ((ImageView) v.findViewById(R.id.iv_default_icon)).setImageResource(R.drawable.neurology_24);

        sheet.show();
    }

    private void openEditor(PromptTemplate t) {
        Intent intent = new Intent(this, PromptEditorActivity.class);
        intent.putExtra("mode", "edit");
        intent.putExtra("template_id", t.id);
        startActivity(intent);
    }

    private void duplicateTemplate(PromptTemplate t) {
        PromptTemplate copy = new PromptTemplate(t.title + " (copy)", false);
        copy.system.addAll(t.system);
        copy.prefix.addAll(t.prefix);
        copy.suffix.addAll(t.suffix);
        store.addTemplate(copy);
        Snackbar.make(recycler, "Duplicated", Snackbar.LENGTH_SHORT).show();
    }

    private void deleteTemplate(PromptTemplate t) {
        if (t.builtIn) {
            Snackbar.make(recycler, "Built-in default can't be deleted — duplicate it instead", Snackbar.LENGTH_LONG).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Prompt")
                .setMessage("Are you sure you want to delete '" + t.title + "'?")
                .setPositiveButton("Delete", (d, w) -> {
                    store.deleteTemplate(t.id);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class PromptAdapter extends RecyclerView.Adapter<PromptViewHolder> {
        private final List<PromptTemplate> items = new ArrayList<>();

        void setItems(List<PromptTemplate> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull @Override public PromptViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PromptViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_prompt_template_row, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull PromptViewHolder holder, int position) {
            PromptTemplate t = items.get(position);
            holder.title.setText(t.title);
            holder.radio.setChecked(t.id.equals(activeId));
            
            String preview = "";
            for (PromptTemplate.Item item : t.system) {
                if (item.type == PromptTemplate.ItemType.TEXT) {
                    preview = item.text.split("\n")[0];
                    break;
                }
            }
            holder.preview.setText(preview);

            holder.itemView.setOnClickListener(v -> {
                store.setActiveId(t.id);
                activeId = t.id;
                notifyDataSetChanged();
            });
            
            holder.menu.setOnClickListener(v -> showOverflowMenu(v, t));
        }

        private void showOverflowMenu(View anchor, PromptTemplate t) {
            View content = getLayoutInflater().inflate(R.layout.popup_overflow, null);
            PopupWindow popup = new PopupWindow(content, WRAP_CONTENT, WRAP_CONTENT, true);
            
            content.findViewById(R.id.btn_edit).setOnClickListener(v -> {
                popup.dismiss(); openEditor(t);
            });
            content.findViewById(R.id.btn_duplicate).setOnClickListener(v -> {
                popup.dismiss(); duplicateTemplate(t);
            });
            content.findViewById(R.id.btn_delete).setOnClickListener(v -> {
                popup.dismiss(); deleteTemplate(t);
            });

            popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popup.setElevation(12f);
            popup.showAsDropDown(anchor, 0, 4, Gravity.END);
        }

        @Override public int getItemCount() { return items.size(); }
    }

    private static class PromptViewHolder extends RecyclerView.ViewHolder {
        TextView title, preview;
        MaterialRadioButton radio;
        ImageButton menu;
        PromptViewHolder(View v) {
            super(v);
            title = v.findViewById(R.id.tv_title);
            preview = v.findViewById(R.id.tv_preview);
            radio = v.findViewById(R.id.radio_active);
            menu = v.findViewById(R.id.btn_menu);
        }
    }
}
