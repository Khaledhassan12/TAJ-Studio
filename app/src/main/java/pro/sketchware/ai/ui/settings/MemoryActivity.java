package pro.sketchware.ai.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.UUID;

import pro.sketchware.R;
import pro.sketchware.ai.agent.tools.ToolRegistry;
import pro.sketchware.ai.memory.MemoryEntry;
import pro.sketchware.ai.memory.MemoryStore;
import pro.sketchware.ai.ui.AiHeaderInsets;
import pro.sketchware.ai.ui.TajSwitch;

/**
 * [WHAT] TAJ Memory settings screen (P2-MEM, D18).
 * [WHY] Real binding to the memory SSOT (R16, §2):
 * - Both access switches ⇄ kv gates; every toggle re-syncs ToolRegistry
 *   (adds/removes the memory tools from the agent specs).
 * - Active memory content is edited via dialog and injected into every
 *   composed prompt by SystemPromptManager IFF active access is ON.
 * - Saved memories render strictly from MemoryStore.list() (applyState);
 *   model-created memories appear on the next render (onResume).
 */
public class MemoryActivity extends BaseAppCompatActivity {

    private MemoryStore store;
    private View root;
    private TajSwitch switchSavedAccess;
    private TajSwitch switchActiveAccess;
    private TextView tvActiveCtxSub;
    private View cardEmpty;
    private LinearLayout containerSaved;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memory);
        AiHeaderInsets.apply(findViewById(R.id.app_bar));

        store = MemoryStore.get(this);
        root = findViewById(R.id.memory_root);

        boolean rtl = isRtl();
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.tv_display_title)).setText(rtl ? R.string.mem_title_ar : R.string.mem_title);
        ((TextView) findViewById(R.id.tv_section_access)).setText(rtl ? R.string.mem_section_access_ar : R.string.mem_section_access);
        ((TextView) findViewById(R.id.tv_section_active)).setText(rtl ? R.string.mem_section_active_ar : R.string.mem_section_active);
        ((TextView) findViewById(R.id.tv_section_saved)).setText(rtl ? R.string.mem_section_saved_ar : R.string.mem_section_saved);
        ((TextView) findViewById(R.id.tv_saved_access_title)).setText(rtl ? R.string.mem_saved_title_ar : R.string.mem_saved_title);
        ((TextView) findViewById(R.id.tv_saved_access_sub)).setText(rtl ? R.string.mem_saved_sub_ar : R.string.mem_saved_sub);
        ((TextView) findViewById(R.id.tv_active_access_title)).setText(rtl ? R.string.mem_active_title_ar : R.string.mem_active_title);
        ((TextView) findViewById(R.id.tv_active_access_sub)).setText(rtl ? R.string.mem_active_sub_ar : R.string.mem_active_sub);
        ((TextView) findViewById(R.id.tv_active_ctx_title)).setText(rtl ? R.string.mem_active_ctx_title_ar : R.string.mem_active_ctx_title);
        ((TextView) findViewById(R.id.tv_empty_title)).setText(rtl ? R.string.mem_empty_title_ar : R.string.mem_empty_title);
        ((TextView) findViewById(R.id.tv_empty_sub)).setText(rtl ? R.string.mem_empty_sub_ar : R.string.mem_empty_sub);
        ((TextView) findViewById(R.id.tv_add_memory)).setText(rtl ? R.string.mem_btn_add_ar : R.string.mem_btn_add);
        ((TextView) findViewById(R.id.btn_docs)).setText(rtl ? R.string.mem_btn_docs_ar : R.string.mem_btn_docs);

        findViewById(R.id.btn_docs).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/docs/guides/function-calling"))));

        switchSavedAccess = findViewById(R.id.switch_saved_access);
        switchActiveAccess = findViewById(R.id.switch_active_access);
        tvActiveCtxSub = findViewById(R.id.tv_active_ctx_sub);
        cardEmpty = findViewById(R.id.card_empty);
        containerSaved = findViewById(R.id.container_saved);

        // TajSwitch listener fires ONLY on user tap (programmatic setChecked never triggers it).
        switchSavedAccess.setOnCheckedChangeListener(isChecked -> {
            store.setSavedAccessEnabled(isChecked); // single writer (UI)
            ToolRegistry.syncMemory(this);           // add/remove the 5 CRUD tools now
        });
        switchActiveAccess.setOnCheckedChangeListener(isChecked -> {
            store.setActiveAccessEnabled(isChecked); // single writer (UI)
            ToolRegistry.syncMemory(this);           // add/remove update_active_memory now
        });

        findViewById(R.id.row_active_memory).setOnClickListener(v -> showActiveMemoryDialog());
        findViewById(R.id.btn_add_memory).setOnClickListener(v -> showMemoryDialog(null));

        handleInsetts(findViewById(android.R.id.content));
    }

    @Override
    public void onResume() {
        super.onResume();
        // Model-created memories (tool calling) appear here on next render.
        applyState();
        ToolRegistry.syncMemory(this);
    }

    /** [R16] Single writer: every visible state derives strictly from MemoryStore. */
    private void applyState() {
        boolean rtl = isRtl();
        switchSavedAccess.setChecked(store.isSavedAccessEnabled());
        switchActiveAccess.setChecked(store.isActiveAccessEnabled());

        // Active memory subtitle: empty hint OR first line of the real content.
        String active = store.getActiveContent();
        if (active == null || active.trim().isEmpty()) {
            tvActiveCtxSub.setText(rtl ? R.string.mem_active_ctx_empty_ar : R.string.mem_active_ctx_empty);
        } else {
            tvActiveCtxSub.setText(firstLine(active));
        }

        // Saved memories: rebuild rows strictly from the persisted list.
        List<MemoryEntry> memories = store.list();
        containerSaved.removeAllViews();
        cardEmpty.setVisibility(memories.isEmpty() ? View.VISIBLE : View.GONE);
        for (MemoryEntry entry : memories) {
            containerSaved.addView(buildMemoryRow(entry));
        }
    }

    private View buildMemoryRow(MemoryEntry entry) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_memory, containerSaved, false);
        TextView title = row.findViewById(R.id.tv_memory_title);
        TextView preview = row.findViewById(R.id.tv_memory_preview);
        ImageView overflow = row.findViewById(R.id.btn_memory_overflow);

        title.setText(entry.title);
        String desc = entry.description;
        preview.setText(desc != null && !desc.trim().isEmpty() ? desc.trim() : firstLine(entry.content));

        row.setOnClickListener(v -> showMemoryDialog(entry));
        overflow.setOnClickListener(v -> {
            boolean rtl = isRtl();
            PopupMenu menu = new PopupMenu(this, overflow);
            menu.getMenu().add(Menu.NONE, 0, 0, rtl ? R.string.mem_menu_edit_ar : R.string.mem_menu_edit);
            menu.getMenu().add(Menu.NONE, 1, 1, rtl ? R.string.mem_menu_delete_ar : R.string.mem_menu_delete);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 0) {
                    showMemoryDialog(entry);
                } else {
                    showDeleteConfirm(entry);
                }
                return true;
            });
            menu.show();
        });
        return row;
    }

    /** Edit Active Memory dialog: helper + multiline content; Save stores, empty clears. */
    private void showActiveMemoryDialog() {
        boolean rtl = isRtl();
        int padding = (int) (16 * getResources().getDisplayMetrics().density);

        EditText etContent = new EditText(this);
        etContent.setHint(rtl ? R.string.mem_hint_content_ar : R.string.mem_hint_content);
        etContent.setText(store.getActiveContent());
        etContent.setMinLines(4);
        etContent.setGravity(Gravity.TOP);

        TextView helper = new TextView(this);
        helper.setText(rtl ? R.string.mem_dialog_active_helper_ar : R.string.mem_dialog_active_helper);
        helper.setTextSize(13f);
        helper.setPadding(0, 0, 0, padding / 2);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding / 2);
        container.addView(helper);
        container.addView(etContent);

        new MaterialAlertDialogBuilder(this)
                .setTitle(rtl ? R.string.mem_dialog_active_title_ar : R.string.mem_dialog_active_title)
                .setView(container)
                .setPositiveButton(rtl ? R.string.ai_btn_save_ar : R.string.ai_btn_save, (dialog, which) -> {
                    store.setActiveContent(etContent.getText().toString()); // blank clears honestly
                    applyState();
                })
                .setNegativeButton(rtl ? R.string.ai_btn_cancel_ar : R.string.ai_btn_cancel, null)
                .show();
    }

    /**
     * Add (existing == null) or Edit dialog: Title / Description / Content.
     * Create/Save stays DISABLED until title + content are non-blank; a
     * duplicate title is rejected honestly with a snackbar (no silent dup).
     */
    private void showMemoryDialog(@Nullable MemoryEntry existing) {
        boolean rtl = isRtl();
        boolean isEdit = existing != null;
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        int gap = padding / 2;

        EditText etTitle = new EditText(this);
        etTitle.setHint(rtl ? R.string.mem_hint_title_ar : R.string.mem_hint_title);
        etTitle.setSingleLine(true);

        EditText etDescription = new EditText(this);
        etDescription.setHint(rtl ? R.string.mem_hint_description_ar : R.string.mem_hint_description);
        etDescription.setSingleLine(true);

        EditText etContent = new EditText(this);
        etContent.setHint(rtl ? R.string.mem_hint_content_ar : R.string.mem_hint_content);
        etContent.setMinLines(4);
        etContent.setGravity(Gravity.TOP);

        if (isEdit) {
            etTitle.setText(existing.title);
            etDescription.setText(existing.description == null ? "" : existing.description);
            etContent.setText(existing.content);
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding / 2, padding, padding / 2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = gap;
        container.addView(etTitle, lp);
        container.addView(etDescription, lp);
        container.addView(etContent);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(isEdit ? (rtl ? R.string.mem_dialog_edit_title_ar : R.string.mem_dialog_edit_title)
                        : (rtl ? R.string.mem_dialog_add_title_ar : R.string.mem_dialog_add_title))
                .setView(container)
                .setNegativeButton(rtl ? R.string.ai_btn_cancel_ar : R.string.ai_btn_cancel, null);
        if (isEdit) {
            builder.setNeutralButton(rtl ? R.string.mem_menu_delete_ar : R.string.mem_menu_delete,
                    (dialog, which) -> showDeleteConfirm(existing));
        }
        builder.setPositiveButton(isEdit ? (rtl ? R.string.ai_btn_save_ar : R.string.ai_btn_save)
                        : (rtl ? R.string.mem_btn_create_ar : R.string.mem_btn_create), null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Runnable refreshEnabled = () -> {
                boolean valid = !etTitle.getText().toString().trim().isEmpty()
                        && !etContent.getText().toString().trim().isEmpty();
                positive.setEnabled(valid);
            };
            TextWatcher watcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    refreshEnabled.run();
                }
            };
            etTitle.addTextChangedListener(watcher);
            etContent.addTextChangedListener(watcher);
            refreshEnabled.run(); // dimmed Create in Add mode until valid (mockup)

            positive.setOnClickListener(v -> {
                String title = etTitle.getText().toString().trim();
                String content = etContent.getText().toString().trim();
                if (title.isEmpty() || content.isEmpty()) return; // stays disabled anyway

                MemoryEntry dup = store.findByTitle(title);
                if (dup != null && (existing == null || !dup.id.equals(existing.id))) {
                    Snackbar.make(root, rtl ? R.string.mem_err_duplicate_title_ar : R.string.mem_err_duplicate_title,
                            Snackbar.LENGTH_LONG).show();
                    return; // honest rejection, dialog stays open
                }

                if (isEdit) {
                    existing.title = title;
                    existing.description = etDescription.getText().toString().trim();
                    existing.content = content;
                    existing.updatedAt = System.currentTimeMillis();
                    store.update(existing);
                } else {
                    MemoryEntry entry = new MemoryEntry();
                    entry.id = UUID.randomUUID().toString();
                    entry.title = title;
                    String description = etDescription.getText().toString().trim();
                    entry.description = description.isEmpty() ? null : description;
                    entry.content = content;
                    entry.updatedAt = System.currentTimeMillis();
                    store.add(entry);
                }
                applyState();
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    /** Destructive delete confirm (mockup): names the memory, no silent wipe. */
    private void showDeleteConfirm(MemoryEntry entry) {
        boolean rtl = isRtl();
        String msg = String.format(rtl ? getString(R.string.mem_dialog_delete_msg_ar) : getString(R.string.mem_dialog_delete_msg), entry.title);
        new MaterialAlertDialogBuilder(this)
                .setTitle(rtl ? R.string.mem_dialog_delete_title_ar : R.string.mem_dialog_delete_title)
                .setMessage(msg)
                .setPositiveButton(rtl ? R.string.mem_menu_delete_ar : R.string.mem_menu_delete, (dialog, which) -> {
                    store.delete(entry.id); // single writer (UI)
                    applyState();
                })
                .setNegativeButton(rtl ? R.string.ai_btn_cancel_ar : R.string.ai_btn_cancel, null)
                .show();
    }

    private static String firstLine(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        int idx = trimmed.indexOf('\n');
        return idx >= 0 ? trimmed.substring(0, idx).trim() : trimmed;
    }

    private boolean isRtl() {
        return getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }
}
