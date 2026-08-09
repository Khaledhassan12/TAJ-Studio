package pro.sketchware.ai.ui.models;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.ai.bus.AiEventHub;
import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.data.Paths;
import pro.sketchware.ai.models.AiModel;
import pro.sketchware.ai.models.LocalModelConfig;
import pro.sketchware.ai.models.ModelManager;
import pro.sketchware.ai.validate.GgufInfo;
import pro.sketchware.ai.validate.GgufValidator;

/**
 * [WHAT] Local model management activity (GGUF-only).
 * [WHY] Allows importing .gguf models from device storage with bulletproof validation and UI updates.
 * [HOW] OpenDocument contract -> background copy -> byte-by-byte magic check -> atomic move -> DB persist -> main thread refresh.
 *
 * [العربية]
 * شاشة إدارة النماذج المحلية (GGUF فقط).
 * تتيح استيراد الموديلات المحلية من ذاكرة الجهاز مع التثبت الصارم وتحديث الواجهة مباشرة بدون خطأ.
 */
public class LocalModelsActivity extends BaseAppCompatActivity implements AiEventHub.Listener {

    private static final String TAG = "LocalModelsActivity";

    private ModelAdapter adapter;
    private ModelManager manager;
    private final List<AiModel> models = new ArrayList<>();
    private ActivityResultLauncher<String[]> importLauncher;
    private AlertDialog progressDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_models);

        manager = ModelManager.get(this);
        AiEventHub.get().addListener(this);

        importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::onFilePicked
        );

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        RecyclerView recyclerLocal = findViewById(R.id.recycler_local);
        recyclerLocal.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ModelAdapter();
        recyclerLocal.setAdapter(adapter);

        findViewById(R.id.btn_import).setOnClickListener(v -> importLauncher.launch(new String[]{"*/*"}));

        refreshModels();
        handleInsetts(findViewById(android.R.id.content));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissProgress();
        AiEventHub.get().removeListener(this);
    }

    @Override
    public void onAiEvent(AiEventHub.Entry entry) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (entry.event == AiEventHub.Event.MODELS_CHANGED) {
                refreshModels();
            } else if (entry.event == AiEventHub.Event.MODEL_IMPORT_VALIDATED) {
                File file = (File) entry.payload;
                AddLocalModelDialog.newInstance(file, null).show(getSupportFragmentManager(), "add_model");
            } else if (entry.event == AiEventHub.Event.ERROR) {
                Log.e(TAG, "AI Event Error: " + entry.payload);
                showErrorDialog(entry.payload != null ? entry.payload.toString() : "Unknown AI error");
            }
        });
    }

    private void refreshModels() {
        models.clear();
        models.addAll(manager.listLocal());
        applyLocalList();
    }

    private void applyLocalList() {
        adapter.notifyDataSetChanged();
        View emptyView = findViewById(R.id.empty_state);
        if (emptyView != null) {
            emptyView.setVisibility(models.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void onFilePicked(@Nullable Uri uri) {
        if (uri == null) {
            Log.d(TAG, "File pick cancelled by user");
            return;
        }

        String name = queryDisplayName(uri);
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".gguf")) {
            showErrorDialog("Not a .gguf file: " + (name != null ? name : "unknown file"));
            return;
        }

        showProgress("Importing " + name);
        final String modelId = slugify(stripExtension(name));

        new Thread(() -> {
            File temp = Paths.tempDownloadFile(modelId);
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(temp)) {

                if (in == null) throw new IOException("Cannot open stream for " + name);

                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) {
                    out.write(buf, 0, r);
                }
                out.flush();
            } catch (Exception e) {
                Log.e(TAG, "Error copying GGUF file from URI: " + uri, e);
                if (temp.exists()) temp.delete();
                runOnUiThread(() -> {
                    dismissProgress();
                    showErrorDialog("Copy failed: " + e.getMessage());
                });
                return;
            }

            GgufInfo vr = GgufValidator.validate(temp);
            if (!vr.valid) {
                Log.e(TAG, "GGUF validation failed for file " + temp.getAbsolutePath() + ": " + vr.error);
                if (temp.exists()) temp.delete();
                runOnUiThread(() -> {
                    dismissProgress();
                    showErrorDialog("Invalid GGUF: " + (vr.error != null ? vr.error : "Validation failed"));
                });
                return;
            }

            File dest = Paths.modelFile(modelId);
            if (dest.exists()) dest.delete();
            if (!temp.renameTo(dest)) {
                Log.e(TAG, "Failed to rename temp GGUF file " + temp.getAbsolutePath() + " to " + dest.getAbsolutePath());
                if (temp.exists()) temp.delete();
                runOnUiThread(() -> {
                    dismissProgress();
                    showErrorDialog("Finalize failed: rename error");
                });
                return;
            }

            LocalModelConfig config = new LocalModelConfig();
            config.modelId = modelId;
            config.alias = stripExtension(name);
            config.contextSize = 2048;
            config.temperature = 0.7f;
            config.topP = 0.9f;
            config.maxTokens = 4096;
            config.mmprojPath = null;

            ContentValues cv = new ContentValues();
            cv.put("id", modelId);
            cv.put("kind", AiModel.Kind.LOCAL.name());
            cv.put("provider", "local");
            cv.put("name", config.alias);
            cv.put("filePath", dest.getAbsolutePath());
            cv.put("metadataJson", config.toJson());
            cv.put("installedAt", System.currentTimeMillis());

            AiStorage.get(LocalModelsActivity.this).insertModel(cv);

            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    dismissProgress();
                    refreshModels();
                    View root = findViewById(android.R.id.content);
                    if (root != null) {
                        Snackbar.make(root, "Model imported: " + config.alias, Snackbar.LENGTH_LONG).show();
                    }
                    AddLocalModelDialog.newInstance(null, modelId).show(getSupportFragmentManager(), "edit_model");
                }
            });
        }).start();
    }

    private String queryDisplayName(Uri uri) {
        if (uri == null) return null;
        String name = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        name = cursor.getString(index);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to query display name for URI: " + uri, e);
            }
        }
        if (name == null) {
            name = uri.getLastPathSegment();
        }
        return name;
    }

    private String stripExtension(String fileName) {
        if (fileName == null) return "model";
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String slugify(String raw) {
        if (raw == null || raw.isEmpty()) return "model_" + System.currentTimeMillis();
        String slug = raw.replaceAll("[^a-zA-Z0-9.-]", "_").toLowerCase(Locale.ROOT);
        return slug.isEmpty() ? "model_" + System.currentTimeMillis() : slug;
    }

    private void showProgress(String message) {
        if (isFinishing() || isDestroyed()) return;
        dismissProgress();

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setPadding(48, 36, 48, 36);
        container.setGravity(android.view.Gravity.CENTER_VERTICAL);

        CircularProgressIndicator progress = new CircularProgressIndicator(this);
        progress.setIndeterminate(true);
        container.addView(progress);

        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setPadding(32, 0, 0, 0);
        androidx.core.widget.TextViewCompat.setTextAppearance(tv, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        container.addView(tv);

        progressDialog = new MaterialAlertDialogBuilder(this)
                .setView(container)
                .setCancelable(false)
                .show();
    }

    private void dismissProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            try {
                progressDialog.dismiss();
            } catch (Exception e) {
                Log.e(TAG, "Error dismissing progress dialog", e);
            }
        }
        progressDialog = null;
    }

    private void showErrorDialog(String msg) {
        if (isFinishing() || isDestroyed()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Import Failed")
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private class ModelAdapter extends RecyclerView.Adapter<ModelViewHolder> {
        @NonNull
        @Override
        public ModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ModelViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_local_model_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ModelViewHolder holder, int position) {
            AiModel entry = models.get(position);
            holder.name.setText(entry.localConfig != null ? entry.localConfig.alias : entry.name);

            if (entry.localConfig != null) {
                holder.chipCtx.setText("Context=" + entry.localConfig.contextSize);
                holder.chipTemp.setText("T=" + entry.localConfig.temperature);
                holder.chipVision.setVisibility(entry.localConfig.mmprojPath != null ? View.VISIBLE : View.GONE);
            }

            holder.btnMenu.setOnClickListener(v -> {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(LocalModelsActivity.this, v);
                popup.getMenu().add("Edit settings");
                popup.getMenu().add("Delete");
                popup.setOnMenuItemClickListener(item -> {
                    if (item.getTitle().equals("Edit settings")) {
                        AddLocalModelDialog.newInstance(null, entry.id).show(getSupportFragmentManager(), "edit_model");
                    } else if (item.getTitle().equals("Delete")) {
                        new MaterialAlertDialogBuilder(LocalModelsActivity.this)
                                .setTitle("Delete Model")
                                .setMessage("Are you sure you want to delete this model and its settings?")
                                .setPositiveButton("Delete", (d, w) -> manager.delete(entry.id))
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                    return true;
                });
                popup.show();
            });

            holder.itemView.setOnClickListener(v -> manager.setActive(entry.id));
            holder.itemView.setAlpha(entry.isActive ? 1.0f : 0.6f);
        }

        @Override
        public int getItemCount() {
            return models.size();
        }
    }

    private static class ModelViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        com.google.android.material.chip.Chip chipCtx, chipTemp, chipVision;
        View btnMenu;

        ModelViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.name);
            chipCtx = v.findViewById(R.id.chip_ctx);
            chipTemp = v.findViewById(R.id.chip_temp);
            chipVision = v.findViewById(R.id.chip_vision);
            btnMenu = v.findViewById(R.id.btn_menu);
        }
    }
}
