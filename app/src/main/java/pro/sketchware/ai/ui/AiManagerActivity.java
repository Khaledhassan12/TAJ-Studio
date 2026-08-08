package pro.sketchware.ai.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import pro.sketchware.R;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import pro.sketchware.ai.data.SecureKeyStore;

/**
 * [WHAT] Settings screen for AI Manager.
 * [WHY] Allows users to configure cloud API keys and manage local models.
 */
public class AiManagerActivity extends BaseAppCompatActivity {

    private SecureKeyStore keyStore;
    private EditText etOpenAi, etHf;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_manager);
        keyStore = SecureKeyStore.get(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etOpenAi = findViewById(R.id.et_openai);
        etHf = findViewById(R.id.et_hf);

        findViewById(R.id.btn_save_cloud).setOnClickListener(v -> saveKeys());
        findViewById(R.id.btn_test_openai).setOnClickListener(v -> testOpenAi());

        loadKeys();
    }

    private void loadKeys() {
        etOpenAi.setText(keyStore.getKey("openai"));
        etHf.setText(keyStore.getHfToken());
    }

    private void saveKeys() {
        String openai = etOpenAi.getText().toString().trim();
        String hf = etHf.getText().toString().trim();

        if (openai.isEmpty()) keyStore.removeKey("openai");
        else keyStore.putKey("openai", openai);

        if (hf.isEmpty()) keyStore.removeHfToken();
        else keyStore.putHfToken(hf);

        Toast.makeText(this, "Keys saved successfully", Toast.LENGTH_SHORT).show();
    }

    private void testOpenAi() {
        // Honest placeholder: real test call would happen here
        Toast.makeText(this, "OpenAI connection test arrives in P3", Toast.LENGTH_SHORT).show();
    }
}
