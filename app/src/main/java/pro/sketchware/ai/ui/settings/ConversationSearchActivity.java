package pro.sketchware.ai.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.R;
import pro.sketchware.ai.agent.tools.ToolRegistry;
import pro.sketchware.ai.data.Paths;
import pro.sketchware.ai.search.ConversationSearchSettings;
import pro.sketchware.ai.search.LocalEmbeddingEngine;
import pro.sketchware.ai.search.EmbeddingEngines;
import pro.sketchware.ai.ui.TajSlider;
import pro.sketchware.ai.ui.TajSwitch;
import pro.sketchware.ai.validate.GgufInfo;
import pro.sketchware.ai.validate.GgufValidator;

public class ConversationSearchActivity extends BaseAppCompatActivity {

    private static final int REQ_PICK_GGUF = 4201;

    private ConversationSearchSettings settings;
    private boolean applyingState = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // P2-CS2 local import state (dialog survives the SAF round-trip).
    private androidx.appcompat.app.AlertDialog localDialog;
    private TextInputEditText localEtName, localEtBatchSize;
    private TextView localTvError, localTvLocalPath;
    private MaterialButton localBtnImport;
    private File pendingLocalFile;
    private String pendingLocalArch;

    private TajSwitch switchAccess, switchAutoCache;
    private View rowModelMethod, rowManualMethod;
    private TextView tvModelMethodVal, tvManualMethodVal;
    private LinearLayout containerEmbeddingModels;
    private View viewEmptyModels;
    private MaterialButton btnAddRemote, btnAddLocal;
    private TajSlider sliderContextPerHit, sliderMaxResults, sliderSimilarity;
    private TextView tvContextPerHitSub, tvMaxResultsSub, tvSimilaritySub;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation_search);

        settings = ConversationSearchSettings.get(this);

        initUi();
        applyUiState();
        handleInsetts(findViewById(android.R.id.content));
    }

    @Override
    public void onResume() {
        super.onResume();
        syncTool();
        applyUiState();
    }

    private void initUi() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_docs).setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/docs/guides/embeddings")));
        });

        switchAccess = findViewById(R.id.switch_access);
        switchAutoCache = findViewById(R.id.switch_auto_cache);
        rowModelMethod = findViewById(R.id.row_model_method);
        rowManualMethod = findViewById(R.id.row_manual_method);
        tvModelMethodVal = findViewById(R.id.tv_model_method_val);
        tvManualMethodVal = findViewById(R.id.tv_manual_method_val);
        containerEmbeddingModels = findViewById(R.id.container_embedding_models);
        viewEmptyModels = findViewById(R.id.view_empty_models);
        btnAddRemote = findViewById(R.id.btn_add_remote);
        btnAddLocal = findViewById(R.id.btn_add_local);

        sliderContextPerHit = findViewById(R.id.slider_context_per_hit);
        sliderMaxResults = findViewById(R.id.slider_max_results);
        sliderSimilarity = findViewById(R.id.slider_similarity);

        tvContextPerHitSub = findViewById(R.id.tv_context_per_hit_sub);
        tvMaxResultsSub = findViewById(R.id.tv_max_results_sub);
        tvSimilaritySub = findViewById(R.id.tv_similarity_sub);

        switchAccess.setOnCheckedChangeListener(isChecked -> {
            if (applyingState) return;
            settings.setAccessEnabled(isChecked);
            syncTool();
        });

        switchAutoCache.setOnCheckedChangeListener(isChecked -> {
            if (applyingState) return;
            settings.setAutoCacheEnabled(isChecked);
        });

        rowModelMethod.setOnClickListener(v -> showMethodMenu(v, true));
        rowManualMethod.setOnClickListener(v -> showMethodMenu(v, false));

        btnAddRemote.setOnClickListener(v -> showAddRemoteDialog(null));
        btnAddLocal.setOnClickListener(v -> showAddLocalDialog());

        setupSliders();
    }

    private void setupSliders() {
        sliderContextPerHit.setRange(1, 20);
        sliderContextPerHit.setStops(new float[]{1, 2, 4, 8, 12, 16, 20});
        sliderContextPerHit.setOnSliderChangeListener(val -> {
            if (applyingState) return;
            settings.setContextPerHit((int) val);
            tvContextPerHitSub.setText(String.format(getString(R.string.cs_context_per_hit_sub), (int) val));
        });

        sliderMaxResults.setRange(1, 20);
        sliderMaxResults.setStops(new float[]{1, 5, 10, 15, 20});
        sliderMaxResults.setOnSliderChangeListener(val -> {
            if (applyingState) return;
            settings.setMaxResults((int) val);
            tvMaxResultsSub.setText(String.format(getString(R.string.cs_max_results_sub), (int) val));
        });

        sliderSimilarity.setRange(0, 1);
        sliderSimilarity.setOnSliderChangeListener(val -> {
            if (applyingState) return;
            settings.setSimilarityThreshold(val);
            tvSimilaritySub.setText(String.format(getString(R.string.cs_similarity_sub), val));
        });
    }

    private void applyUiState() {
        applyingState = true;
        try {
            switchAccess.setChecked(settings.isAccessEnabled());
            switchAutoCache.setChecked(settings.isAutoCacheEnabled());

            String modelMethod = settings.getModelSearchMethod();
            tvModelMethodVal.setText(modelMethod.equals(ConversationSearchSettings.METHOD_KEYWORD) ? 
                    R.string.cs_method_keyword : R.string.cs_method_semantic);
            
            String manualMethod = settings.getManualSearchMethod();
            tvManualMethodVal.setText(manualMethod.equals(ConversationSearchSettings.METHOD_KEYWORD) ? 
                    R.string.cs_method_keyword : R.string.cs_method_semantic);

            renderEmbeddingModels();

            int ctxHit = settings.getContextPerHit();
            sliderContextPerHit.setValue(ctxHit);
            tvContextPerHitSub.setText(String.format(getString(R.string.cs_context_per_hit_sub), ctxHit));

            int maxRes = settings.getMaxResults();
            sliderMaxResults.setValue(maxRes);
            tvMaxResultsSub.setText(String.format(getString(R.string.cs_max_results_sub), maxRes));

            float sim = settings.getSimilarityThreshold();
            sliderSimilarity.setValue(sim);
            tvSimilaritySub.setText(String.format(getString(R.string.cs_similarity_sub), sim));

        } finally {
            applyingState = false;
        }
    }

    private void renderEmbeddingModels() {
        containerEmbeddingModels.removeAllViews();
        List<ConversationSearchSettings.EmbeddingModel> models = settings.getEmbeddingModels();
        
        if (models.isEmpty()) {
            containerEmbeddingModels.addView(viewEmptyModels);
        } else {
            viewEmptyModels.setVisibility(View.GONE);
            for (ConversationSearchSettings.EmbeddingModel m : models) {
                View row = getLayoutInflater().inflate(R.layout.item_embedding_model_row, containerEmbeddingModels, false);
                ((TextView) row.findViewById(R.id.tv_name)).setText(m.name);
                TextView tvDetails = row.findViewById(R.id.tv_details);
                if ("local".equals(m.type)) {
                    // P2-CS2: Local badge + file-based details.
                    row.findViewById(R.id.tv_badge_local).setVisibility(View.VISIBLE);
                    String fileName = m.localPath != null ? new File(m.localPath).getName() : "";
                    tvDetails.setText("Local GGUF | " + fileName);
                } else {
                    tvDetails.setText(m.provider + " | " + m.modelName);
                }
                
                row.findViewById(R.id.btn_overflow).setOnClickListener(v -> showModelOverflow(v, m));
                containerEmbeddingModels.addView(row);
                
                // Add divider if not last
                if (models.indexOf(m) < models.size() - 1) {
                    View div = new View(this);
                    div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                    div.setBackgroundColor(getThemeColor(R.attr.colorOutlineVariant));
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) div.getLayoutParams();
                    lp.setMarginStart(dp(56));
                    containerEmbeddingModels.addView(div);
                }
            }
        }
    }

    private void showModelOverflow(View v, ConversationSearchSettings.EmbeddingModel model) {
        PopupMenu popup = new PopupMenu(this, v);
        // P2-CS2: local models are replaced wholesale — Delete only (no fake Edit).
        if (!"local".equals(model.type)) {
            popup.getMenu().add("Edit");
        }
        popup.getMenu().add("Delete");
        popup.setOnMenuItemClickListener(item -> {
            if ("Edit".equals(item.getTitle())) {
                showAddRemoteDialog(model);
            } else if ("Delete".equals(item.getTitle())) {
                String message = "local".equals(model.type)
                        ? "Remove this local embedding model? Its file and all stored index vectors will be deleted."
                        : "Remove this embedding model? Its stored index vectors will be deleted.";
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Delete Model")
                        .setMessage(message)
                        .setPositiveButton("Delete", (d, w) -> {
                            // Single writer (R5): config + key + vectors + local file.
                            settings.removeEmbeddingModel(model.id);
                            applyUiState();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
            return true;
        });
        popup.show();
    }

    private void showMethodMenu(View anchor, boolean isModel) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 0, 0, R.string.cs_method_keyword);
        popup.getMenu().add(0, 1, 1, R.string.cs_method_semantic);
        
        boolean hasModel = settings.hasEmbeddingModel();
        popup.getMenu().getItem(1).setEnabled(hasModel);
        
        String current = isModel ? settings.getModelSearchMethod() : settings.getManualSearchMethod();
        if (current.equals(ConversationSearchSettings.METHOD_KEYWORD)) {
            popup.getMenu().getItem(0).setCheckable(true).setChecked(true);
        } else {
            popup.getMenu().getItem(1).setCheckable(true).setChecked(true);
        }

        popup.setOnMenuItemClickListener(item -> {
            String method = item.getItemId() == 0 ? ConversationSearchSettings.METHOD_KEYWORD : ConversationSearchSettings.METHOD_SEMANTIC;
            if (isModel) settings.setModelSearchMethod(method);
            else settings.setManualSearchMethod(method);
            applyUiState();
            return true;
        });
        popup.show();
    }

    private void showAddRemoteDialog(@Nullable ConversationSearchSettings.EmbeddingModel existing) {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_remote_embedding, null);
        AutoCompleteTextView actvProvider = view.findViewById(R.id.actv_provider);
        AutoCompleteTextView actvModel = view.findViewById(R.id.actv_model);
        TextInputEditText etName = view.findViewById(R.id.et_name);
        TextInputEditText etKey = view.findViewById(R.id.et_api_key);
        TextInputEditText etBaseUrl = view.findViewById(R.id.et_base_url);
        TextInputEditText etBatchSize = view.findViewById(R.id.et_batch_size);
        TextView tvError = view.findViewById(R.id.tv_error);

        String[] providers = {"OpenAI", "Mistral", "Voyage AI", "SiliconFlow", "Open Router", "Ollama", "Custom"};
        actvProvider.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, providers));

        actvProvider.setOnItemClickListener((parent, v, position, id) -> {
            String p = providers[position];
            updateRemoteProviderFields(p, etBaseUrl, actvModel);
        });

        if (existing != null) {
            etName.setText(existing.name);
            etBaseUrl.setText(existing.baseUrl);
            actvProvider.setText(existing.provider, false);
            actvModel.setText(existing.modelName, false);
            etBatchSize.setText(String.valueOf(existing.batchSize));
            etKey.setHint("••••••••");
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? "Add Remote Model" : "Edit Remote Model")
                .setView(view)
                .setPositiveButton(existing == null ? "Add" : "Save", null)
                .setNegativeButton("Cancel", null);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String key = etKey.getText().toString().trim();
            String baseUrl = etBaseUrl.getText().toString().trim();
            String modelName = actvModel.getText().toString().trim();
            String provider = actvProvider.getText().toString().trim();
            int batchSize = 8;
            try { batchSize = Integer.parseInt(etBatchSize.getText().toString()); } catch (Exception ignored) {}

            if (name.isEmpty() || baseUrl.isEmpty() || modelName.isEmpty() || provider.isEmpty() || (existing == null && key.isEmpty())) {
                tvError.setText("All fields are required");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            final int finalBatchSize = batchSize;
            tvError.setText("Testing connection...");
            tvError.setVisibility(View.VISIBLE);
            v.setEnabled(false);

            executor.execute(() -> {
                String testKey = key.isEmpty() && existing != null ? settings.getRemoteKey(existing.id) : key;
                boolean ok = testEmbeddingConnection(baseUrl, modelName, testKey);
                runOnUiThread(() -> {
                    if (ok) {
                        ConversationSearchSettings.EmbeddingModel m = existing != null ? existing : new ConversationSearchSettings.EmbeddingModel();
                        if (existing == null) m.id = UUID.randomUUID().toString();
                        m.name = name;
                        m.type = "remote";
                        m.provider = provider;
                        m.modelName = modelName;
                        m.baseUrl = baseUrl;
                        m.batchSize = finalBatchSize;
                        
                        if (existing == null) settings.addEmbeddingModel(m, key);
                        else {
                            List<ConversationSearchSettings.EmbeddingModel> list = settings.getEmbeddingModels();
                            for (int i = 0; i < list.size(); i++) {
                                if (list.get(i).id.equals(existing.id)) {
                                    list.set(i, m);
                                    break;
                                }
                            }
                            settings.setEmbeddingModels(list);
                            if (!key.isEmpty()) pro.sketchware.ai.data.SecureKeyStore.get(this).putKey("emb:" + m.id, key);
                        }
                        applyUiState();
                        dialog.dismiss();
                    } else {
                        tvError.setText("Connection failed. Check key and URL.");
                        v.setEnabled(true);
                    }
                });
            });
        });
    }

    private void updateRemoteProviderFields(String provider, TextInputEditText etBaseUrl, AutoCompleteTextView actvModel) {
        String url = "";
        String[] models = {};
        switch (provider) {
            case "OpenAI":
                url = "https://api.openai.com/v1";
                models = new String[]{"text-embedding-3-small", "text-embedding-3-large", "text-embedding-ada-002"};
                break;
            case "Mistral":
                url = "https://api.mistral.ai/v1";
                models = new String[]{"mistral-embed"};
                break;
            case "Voyage AI":
                url = "https://api.voyageai.com/v1";
                models = new String[]{"voyage-3-lite", "voyage-3"};
                break;
            case "SiliconFlow":
                url = "https://api.siliconflow.cn/v1";
                models = new String[]{"BAAI/bge-m3"};
                break;
            case "Open Router":
                url = "https://openrouter.ai/api/v1";
                models = new String[]{"openai/text-embedding-3-small"};
                break;
            case "Ollama":
                url = "http://localhost:11434/v1";
                models = new String[]{"nomic-embed-text"};
                break;
        }
        etBaseUrl.setText(url);
        actvModel.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, models));
        if (models.length > 0) actvModel.setText(models[0], false);
    }

    private boolean testEmbeddingConnection(String baseUrl, String model, String key) {
        try {
            String endpoint = baseUrl + "/embeddings";
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("input", new JSONArray().put("ping"));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes("UTF-8"));
            }

            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * P2-CS2: real local model import (§2 real binding, §11 no fake UI).
     * Dialog survives the SAF round-trip via the local* member fields.
     */
    private void showAddLocalDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_local_embedding, null);
        localEtName = view.findViewById(R.id.et_name);
        localEtBatchSize = view.findViewById(R.id.et_batch_size);
        localTvError = view.findViewById(R.id.tv_error);
        localTvLocalPath = view.findViewById(R.id.tv_local_path);
        localBtnImport = view.findViewById(R.id.btn_import_gguf);
        pendingLocalFile = null;
        pendingLocalArch = null;

        localBtnImport.setOnClickListener(v -> launchGgufPicker());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("Add Local Model")
                .setView(view)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .setOnDismissListener(d -> {
                    // Imported but never saved → clean the file up (no leftovers).
                    if (pendingLocalFile != null) {
                        pendingLocalFile.delete();
                        pendingLocalFile = null;
                    }
                });

        localDialog = builder.create();
        localDialog.show();

        localDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = localEtName.getText().toString().trim();
            int batchSize = 8;
            try { batchSize = Integer.parseInt(localEtBatchSize.getText().toString().trim()); } catch (Exception ignored) {}

            if (name.isEmpty()) {
                showLocalError("Name is required");
                return;
            }
            if (batchSize < 1 || batchSize > 100) {
                showLocalError("Batch size must be between 1 and 100");
                return;
            }
            if (pendingLocalFile == null || !pendingLocalFile.exists()) {
                showLocalError("Import a .gguf embedding model file first");
                return;
            }

            ConversationSearchSettings.EmbeddingModel m = new ConversationSearchSettings.EmbeddingModel();
            m.id = UUID.randomUUID().toString();
            m.name = name;
            m.type = "local";
            m.provider = "Local";
            m.modelName = pendingLocalFile.getName();
            m.localPath = pendingLocalFile.getAbsolutePath();
            m.batchSize = batchSize;

            pendingLocalFile = null; // consumed — dismiss listener must not delete it
            settings.addEmbeddingModel(m, null);
            applyUiState();
            localDialog.dismiss();
        });
    }

    private void launchGgufPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // .gguf has no registered MIME type
        startActivityForResult(intent, REQ_PICK_GGUF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_GGUF && resultCode == RESULT_OK && data != null && data.getData() != null) {
            handleGgufPicked(data.getData());
        }
    }

    /**
     * P2-CS2 import pipeline (background): copy → GGUF magic+metadata →
     * generative-arch gate (RISK-20) → rename to final → test-embed "ping".
     * Any failure = honest error + copied file deleted (§16 error states).
     */
    private void handleGgufPicked(Uri uri) {
        localBtnImport.setEnabled(false);
        localTvError.setVisibility(View.GONE);
        localTvLocalPath.setVisibility(View.VISIBLE);
        localTvLocalPath.setText("Copying and validating model…");

        executor.execute(() -> {
            String error = null;
            File finalFile = null;
            String arch = null;

            File dir = Paths.embeddingsDir();
            if (!dir.exists() && !dir.mkdirs()) {
                error = "Could not create the local models directory";
            } else {
                File tmp = new File(dir, "import_" + UUID.randomUUID() + ".tmp");
                try {
                    // 1. Copy from the SAF Uri into our private models dir.
                    try (InputStream in = getContentResolver().openInputStream(uri);
                         FileOutputStream out = new FileOutputStream(tmp)) {
                        if (in == null) throw new Exception("Could not read the selected file");
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }

                    // 2. GGUF magic + metadata validation (pure Java).
                    GgufInfo info = GgufValidator.validate(tmp);
                    if (!info.valid) {
                        error = "Not a valid GGUF file: " + info.error;
                    } else if (GgufValidator.isLikelyGenerativeArch(info.arch)) {
                        // RISK-20 gate 1: chat/generative GGUFs cannot embed.
                        error = "This is a chat model (architecture: " + info.arch + "), not an embedding model";
                    } else {
                        // 3. Promote to the final file, then run the test gate.
                        finalFile = uniqueGgufFile(dir, displayName(uri));
                        if (!tmp.renameTo(finalFile)) {
                            error = "Could not finalize the imported file";
                            finalFile = null;
                        } else {
                            arch = info.arch;
                            // 4. RISK-20 gate 2: mandatory test-embed before saving config.
                            ConversationSearchSettings.EmbeddingModel probe = new ConversationSearchSettings.EmbeddingModel();
                            probe.id = "probe";
                            probe.name = finalFile.getName();
                            probe.type = "local";
                            probe.localPath = finalFile.getAbsolutePath();
                            try {
                                List<float[]> vectors = new LocalEmbeddingEngine(getApplicationContext(), probe)
                                        .embed(Collections.singletonList("ping"));
                                if (vectors.isEmpty() || vectors.get(0) == null || vectors.get(0).length == 0) {
                                    error = "Test embedding produced no vector";
                                }
                            } catch (Exception e) {
                                error = e.getMessage() == null ? "Test embedding failed" : e.getMessage();
                            }
                        }
                    }
                } catch (Exception e) {
                    error = "Import failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                }

                // Honest cleanup on any failure: never leave a dead file behind.
                if (error != null) {
                    tmp.delete();
                    if (finalFile != null) finalFile.delete();
                    finalFile = null;
                }
            }

            final String fError = error;
            final File fFile = finalFile;
            final String fArch = arch;
            runOnUiThread(() -> {
                localBtnImport.setEnabled(true);
                if (fError != null) {
                    pendingLocalFile = null;
                    localTvLocalPath.setVisibility(View.GONE);
                    showLocalError(fError);
                } else {
                    pendingLocalFile = fFile;
                    pendingLocalArch = fArch;
                    long kb = fFile.length() / 1024L;
                    String size = kb >= 1024 ? String.format(Locale.US, "%.1f MB", kb / 1024.0) : kb + " KB";
                    String archPart = (fArch != null && !"unknown".equals(fArch)) ? " | arch: " + fArch : "";
                    localTvLocalPath.setText(fFile.getName() + " | " + size + archPart);
                }
            });
        });
    }

    /** Display name from a SAF Uri, guaranteed .gguf suffix for the saved file. */
    private String displayName(Uri uri) {
        String segment = uri.getLastPathSegment();
        if (segment != null && segment.contains("/")) segment = segment.substring(segment.lastIndexOf('/') + 1);
        if (segment == null || segment.isEmpty()) segment = "model.gguf";
        if (!segment.toLowerCase(Locale.US).endsWith(".gguf")) segment = segment + ".gguf";
        return segment;
    }

    /** Collision-safe destination file inside the embeddings dir. */
    private File uniqueGgufFile(File dir, String name) {
        File f = new File(dir, name);
        String stem = name.toLowerCase(Locale.US).endsWith(".gguf") ? name.substring(0, name.length() - 5) : name;
        int i = 1;
        while (f.exists()) {
            f = new File(dir, stem + "_" + i + ".gguf");
            i++;
        }
        return f;
    }

    private void showLocalError(String message) {
        if (localTvError == null) return;
        localTvError.setText(message);
        localTvError.setVisibility(View.VISIBLE);
    }

    private void syncTool() {
        ToolRegistry.syncConversationSearch(this);
    }

    private int getThemeColor(int attrId) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(attrId, typedValue, true);
        if (typedValue.resourceId != 0) {
            return androidx.core.content.ContextCompat.getColor(this, typedValue.resourceId);
        }
        return typedValue.data;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
