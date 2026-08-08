package pro.sketchware.ai.ui;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.*;
import pro.sketchware.R;
import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.data.SecureKeyStore;
import pro.sketchware.ai.models.AiModel;

/**
 * [WHAT] Settings screen for AI Manager.
 * [WHY] Allows users to configure cloud API keys and manage local models.
 * [HOW] Uses Material 3 cards and sections. Reuses AiStorage for model listing.
 */
public class AiManagerActivity extends BaseAppCompatActivity {

    private SecureKeyStore keyStore;
    private AiStorage storage;
    private EditText etOpenAi, etAnthropic, etGemini, etCompatUrl, etCompatKey, etHf;
    private ModelAdapter adapter;
    private final OkHttpClient client = new OkHttpClient();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_manager);
        keyStore = SecureKeyStore.get(this);
        storage = AiStorage.get(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etOpenAi = findViewById(R.id.et_openai);
        etAnthropic = findViewById(R.id.et_anthropic);
        etGemini = findViewById(R.id.et_gemini);
        etCompatUrl = findViewById(R.id.et_compat_url);
        etCompatKey = findViewById(R.id.et_compat_key);
        etHf = findViewById(R.id.et_hf);

        RecyclerView recyclerModels = findViewById(R.id.recycler_local_models);
        adapter = new ModelAdapter();
        recyclerModels.setAdapter(adapter);

        findViewById(R.id.btn_save_cloud).setOnClickListener(v -> saveCloudKeys());
        findViewById(R.id.btn_save_hf).setOnClickListener(v -> saveHfToken());

        findViewById(R.id.btn_test_openai).setOnClickListener(v -> testProvider("https://api.openai.com/v1/models", etOpenAi.getText().toString(), "Bearer"));
        findViewById(R.id.btn_test_anthropic).setOnClickListener(v -> testProvider("https://api.anthropic.com/v1/messages", etAnthropic.getText().toString(), "x-api-key"));
        findViewById(R.id.btn_test_gemini).setOnClickListener(v -> testGemini(etGemini.getText().toString()));

        loadKeys();
        refreshModels();
    }

    private void loadKeys() {
        etOpenAi.setText(keyStore.getKey("openai"));
        etAnthropic.setText(keyStore.getKey("anthropic"));
        etGemini.setText(keyStore.getKey("gemini"));
        etCompatUrl.setText(keyStore.getKey("openai-compatible-url"));
        etCompatKey.setText(keyStore.getKey("openai-compatible"));
        etHf.setText(keyStore.getHfToken());
    }

    private void saveCloudKeys() {
        keyStore.putKey("openai", etOpenAi.getText().toString().trim());
        keyStore.putKey("anthropic", etAnthropic.getText().toString().trim());
        keyStore.putKey("gemini", etGemini.getText().toString().trim());
        keyStore.putKey("openai-compatible-url", etCompatUrl.getText().toString().trim());
        keyStore.putKey("openai-compatible", etCompatKey.getText().toString().trim());
        Toast.makeText(this, "Cloud keys saved", Toast.LENGTH_SHORT).show();
    }

    private void saveHfToken() {
        keyStore.putHfToken(etHf.getText().toString().trim());
        Toast.makeText(this, "HF token saved", Toast.LENGTH_SHORT).show();
    }

    private void testProvider(String url, String key, String authHeader) {
        if (key.isEmpty()) {
            Toast.makeText(this, "Key is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        Request.Builder rb = new Request.Builder().url(url);
        if ("Bearer".equals(authHeader)) rb.addHeader("Authorization", "Bearer " + key);
        else rb.addHeader(authHeader, key);

        if (url.contains("anthropic")) {
             // Anthropic requires a POST to messages, but we can't do a full completion here easily.
             // Just doing a HEAD or something might not work. Let's just say "coming soon" or do a simple GET if available.
             Toast.makeText(this, "Anthropic test coming in P3 full", Toast.LENGTH_SHORT).show();
             return;
        }

        client.newCall(rb.build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(AiManagerActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
            @Override public void onResponse(Call call, Response response) {
                runOnUiThread(() -> Toast.makeText(AiManagerActivity.this, response.isSuccessful() ? "Success!" : "Error: " + response.code(), Toast.LENGTH_SHORT).show());
                response.close();
            }
        });
    }

    private void testGemini(String key) {
        if (key.isEmpty()) {
            Toast.makeText(this, "Key is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + key;
        testProvider(url, "", ""); // No extra auth header needed, it's in URL
    }

    private void refreshModels() {
        List<AiModel> models = new ArrayList<>();
        try (Cursor c = storage.listModels()) {
            while (c.moveToNext()) {
                AiModel m = new AiModel();
                m.id = c.getString(c.getColumnIndexOrThrow("id"));
                m.name = c.getString(c.getColumnIndexOrThrow("name"));
                m.filePath = c.getString(c.getColumnIndexOrThrow("filePath"));
                m.sizeBytes = new File(m.filePath).length();
                models.add(m);
            }
        }
        adapter.setItems(models);
    }

    private class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.ViewHolder> {
        private final List<AiModel> items = new ArrayList<>();
        public void setItems(List<AiModel> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ai_model, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AiModel m = items.get(position);
            holder.name.setText(m.name);
            holder.meta.setText((m.sizeBytes / 1024 / 1024) + " MB");
            holder.delete.setOnClickListener(v -> {
                new File(m.filePath).delete();
                storage.deleteModel(m.id);
                refreshModels();
            });
        }
        @Override public int getItemCount() { return items.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, meta;
            View delete;
            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.tv_name);
                meta = v.findViewById(R.id.tv_meta);
                delete = v.findViewById(R.id.btn_delete);
            }
        }
    }
}
