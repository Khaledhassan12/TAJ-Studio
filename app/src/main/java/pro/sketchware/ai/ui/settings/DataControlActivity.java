package pro.sketchware.ai.ui.settings;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.ai.data.AiDatabase;
import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.data.DataControlSettings;
import pro.sketchware.ai.data.SecureKeyStore;
import pro.sketchware.ai.data.TajBackupManager;
import pro.sketchware.ai.data.importers.ChatGptImporter;
import pro.sketchware.ai.data.importers.ClaudeImporter;
import pro.sketchware.ai.memory.MemoryStore;
import pro.sketchware.ai.prompts.PromptTemplateStore;
import pro.sketchware.ai.ui.AiHeaderInsets;
import pro.sketchware.databinding.ActivityDataControlBinding;

/**
 * [WHAT] UI for Data Control, Import/Export, and Auto Backup.
 * [WHY] Provides user control over their AI data (P2-DC).
 * [HOW] R16 binding; M3 cards and dialogs; SAF for file picking.
 */
public class DataControlActivity extends BaseAppCompatActivity {

    private ActivityDataControlBinding binding;
    private DataControlSettings dc;
    private TajBackupManager manager;

    private final ActivityResultLauncher<Intent> importPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    onFilePicked(result.getData().getData(), "taj");
                }
            });

    private final ActivityResultLauncher<Intent> chatGptPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    onFilePicked(result.getData().getData(), "chatgpt");
                }
            });

    private final ActivityResultLauncher<Intent> claudePicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    onFilePicked(result.getData().getData(), "claude");
                }
            });

    private final ActivityResultLauncher<Intent> treePicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri treeUri = result.getData().getData();
                    getContentResolver().takePersistableUriPermission(treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    dc.setBackupTreeUri(treeUri.toString());
                    applyState();
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityDataControlBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        AiHeaderInsets.apply(binding.appBar);

        dc = DataControlSettings.get(this);
        manager = TajBackupManager.get(this);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.rowImport.setOnClickListener(v -> openPicker("application/octet-stream", importPicker));
        binding.rowExport.setOnClickListener(v -> showExportDialog());
        binding.rowImportChatgpt.setOnClickListener(v -> openPicker("application/zip", chatGptPicker));
        binding.rowImportClaude.setOnClickListener(v -> openPicker("application/zip", claudePicker));
        
        binding.switchAutoBackup.setOnCheckedChangeListener((v, checked) -> {
            dc.setAutoBackupEnabled(checked);
            manager.rescheduleAutoBackup();
            applyState();
        });
        
        binding.rowBackupFreq.setOnClickListener(v -> showFrequencyDialog());
        binding.rowBackupContent.setOnClickListener(v -> showContentDialog());
        binding.rowBackupLocation.setOnClickListener(v -> openTreePicker());
        
        binding.switchAutoDelete.setOnCheckedChangeListener((v, checked) -> {
            dc.setAutoDeleteEnabled(checked);
            applyState();
        });
        
        binding.rowRetention.setOnClickListener(v -> showRetentionDialog());

        applyState();
    }

    private void applyState() {
        boolean autoOn = dc.isAutoBackupEnabled();
        binding.switchAutoBackup.setChecked(autoOn);
        binding.panelAutoBackup.setVisibility(autoOn ? View.VISIBLE : View.GONE);
        
        long last = dc.getLastBackupAt();
        if (last == 0) {
            binding.tvLastBackup.setText(getString(R.string.dc_last_backup_never));
        } else {
            String date = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(new Date(last));
            binding.tvLastBackup.setText(getString(R.string.dc_last_backup_format, date));
        }

        binding.tvBackupFreq.setText(getFrequencyLabel(dc.getBackupFreqDays()));
        binding.tvBackupLocation.setText(manager.getResolvedLocationText());

        boolean deleteOn = dc.isAutoDeleteEnabled();
        binding.switchAutoDelete.setChecked(deleteOn);
        binding.panelAutoDelete.setVisibility(deleteOn ? View.VISIBLE : View.GONE);
        binding.tvRetention.setText(getRetentionLabel(dc.getRetentionDays()));
    }


    private void openPicker(String mime, ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        launcher.launch(intent);
    }

    private void openTreePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        treePicker.launch(intent);
    }

    private void onFilePicked(Uri uri, String type) {
        if (uri == null) return;
        switch (type) {
            case "taj":
                manager.importAsync(uri, (ok, details, error) -> {
                    runOnUiThread(() -> {
                        if (ok) Toast.makeText(this, details, Toast.LENGTH_LONG).show();
                        else showError(error);
                    });
                });
                break;
            case "chatgpt":
                ChatGptImporter.importAsync(this, uri, (ok, count, error) -> {
                    runOnUiThread(() -> {
                        if (ok) Toast.makeText(this, "Imported " + count + " ChatGPT chats", Toast.LENGTH_LONG).show();
                        else showError(error);
                    });
                });
                break;
            case "claude":
                ClaudeImporter.importAsync(this, uri, (ok, count, error) -> {
                    runOnUiThread(() -> {
                        if (ok) Toast.makeText(this, "Imported " + count + " Claude chats", Toast.LENGTH_LONG).show();
                        else showError(error);
                    });
                });
                break;
        }
    }

    private void showExportDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_export_data, null);
        CheckBox cbConvos = v.findViewById(R.id.cb_convos);
        CheckBox cbMemories = v.findViewById(R.id.cb_memories);
        CheckBox cbPrompts = v.findViewById(R.id.cb_prompts);
        CheckBox cbSettings = v.findViewById(R.id.cb_settings);
        CheckBox cbSecrets = v.findViewById(R.id.cb_secrets);

        // Update counts honestly
        cbConvos.setText("Conversations & Messages (" + getCount("conversations") + ")");
        cbMemories.setText("Memories (" + MemoryStore.get(this).list().size() + ")");
        cbPrompts.setText("System Prompts (" + PromptTemplateStore.get(this).loadAll().size() + ")");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Export Data")
                .setView(v)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Export", (d, w) -> {
                    DataControlSettings.BackupContent flags = new DataControlSettings.BackupContent();
                    flags.conversations = cbConvos.isChecked();
                    flags.memories = cbMemories.isChecked();
                    flags.prompts = cbPrompts.isChecked();
                    flags.settings = cbSettings.isChecked();
                    flags.secrets = cbSecrets.isChecked();
                    
                    Toast.makeText(this, "Exporting...", Toast.LENGTH_SHORT).show();
                    manager.exportAsync(flags, (ok, path, error) -> {
                        runOnUiThread(() -> {
                            if (ok) Snackbar.make(binding.getRoot(), "Exported to: " + path, Snackbar.LENGTH_LONG).show();
                            else showError(error);
                        });
                    });
                })
                .show();
    }

    private void showContentDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_export_data, null);
        CheckBox cbConvos = v.findViewById(R.id.cb_convos);
        CheckBox cbMemories = v.findViewById(R.id.cb_memories);
        CheckBox cbPrompts = v.findViewById(R.id.cb_prompts);
        CheckBox cbSettings = v.findViewById(R.id.cb_settings);
        CheckBox cbSecrets = v.findViewById(R.id.cb_secrets);

        DataControlSettings.BackupContent current = dc.getBackupContent();
        cbConvos.setChecked(current.conversations);
        cbMemories.setChecked(current.memories);
        cbPrompts.setChecked(current.prompts);
        cbSettings.setChecked(current.settings);
        cbSecrets.setChecked(current.secrets);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Backup Content")
                .setView(v)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK", (d, w) -> {
                    DataControlSettings.BackupContent next = new DataControlSettings.BackupContent();
                    next.conversations = cbConvos.isChecked();
                    next.memories = cbMemories.isChecked();
                    next.prompts = cbPrompts.isChecked();
                    next.settings = cbSettings.isChecked();
                    next.secrets = cbSecrets.isChecked();
                    dc.setBackupContent(next);
                })
                .show();
    }

    private void showFrequencyDialog() {
        String[] items = {"1 day", "3 days", "5 days", "1 week", "1 month"};
        int[] days = {1, 3, 5, 7, 30};
        int current = 0;
        int currentDays = dc.getBackupFreqDays();
        for (int i = 0; i < days.length; i++) if (days[i] == currentDays) current = i;

        new MaterialAlertDialogBuilder(this)
                .setTitle("Backup Frequency")
                .setSingleChoiceItems(items, current, (d, w) -> {
                    dc.setBackupFreqDays(days[w]);
                    manager.rescheduleAutoBackup();
                    applyState();
                    d.dismiss();
                })
                .show();
    }

    private void showRetentionDialog() {
        String[] items = {"1 week", "1 month", "1 year"};
        int[] days = {7, 30, 365};
        int current = 0;
        int currentDays = dc.getRetentionDays();
        for (int i = 0; i < days.length; i++) if (days[i] == currentDays) current = i;

        new MaterialAlertDialogBuilder(this)
                .setTitle("Retention Period")
                .setSingleChoiceItems(items, current, (d, w) -> {
                    dc.setRetentionDays(days[w]);
                    applyState();
                    d.dismiss();
                })
                .show();
    }

    private String getFrequencyLabel(int d) {
        if (d == 1) return "Every day";
        if (d < 7) return "Every " + d + " days";
        if (d == 7) return "Every week";
        return "Every month";
    }

    private String getRetentionLabel(int d) {
        if (d == 7) return "1 week";
        if (d == 30) return "1 month";
        return "1 year";
    }

    private int getCount(String table) {
        try (android.database.Cursor c = AiDatabase.get(this).getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            if (c.moveToFirst()) return c.getInt(0);
        } catch (Exception ignored) {}
        return 0;
    }

    private void showError(String msg) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Error")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }
}
