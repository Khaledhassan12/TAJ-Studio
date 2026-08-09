package pro.sketchware.ai.ui.models;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

import pro.sketchware.R;
import pro.sketchware.ai.data.Paths;
import pro.sketchware.ai.models.LocalChatModelConfig;
import pro.sketchware.ai.models.ModelManager;
import pro.sketchware.ai.validate.GgufInfo;
import pro.sketchware.ai.validate.GgufValidator;

/**
 * [WHAT] Dialog for adding or editing local AI models (P1-D).
 * [WHY] Allows configuring sampling parameters and attaching vision projectors.
 * [HOW] BottomSheetDialogFragment with SAF for .mmproj selection.
 */
public class AddLocalModelDialog extends BottomSheetDialogFragment {

    private static final int REQ_PICK_MMPROJ = 2001;

    private TextInputEditText etModelId, etAlias, etCtx, etMaxTokens, etTemp, etTopP;
    private TextInputLayout tilModelId, tilAlias, tilCtx, tilMaxTokens, tilTemp, tilTopP;
    private TextView tvVisionFile;
    private MaterialButton btnVision, btnAdd;

    private File ggufSource;
    private File tempMmproj;
    private LocalChatModelConfig existingConfig;

    public static AddLocalModelDialog newInstance(@Nullable File ggufSource, @Nullable String existingModelId) {
        AddLocalModelDialog fragment = new AddLocalModelDialog();
        Bundle args = new Bundle();
        if (ggufSource != null) args.putString("gguf_path", ggufSource.getAbsolutePath());
        if (existingModelId != null) args.putString("model_id", existingModelId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_add_local_model, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etModelId = view.findViewById(R.id.et_model_id);
        etAlias = view.findViewById(R.id.et_alias);
        etCtx = view.findViewById(R.id.et_ctx);
        etMaxTokens = view.findViewById(R.id.et_max_tokens);
        etTemp = view.findViewById(R.id.et_temp);
        etTopP = view.findViewById(R.id.et_top_p);

        tilModelId = view.findViewById(R.id.til_model_id);
        tilAlias = view.findViewById(R.id.til_alias);
        tilCtx = view.findViewById(R.id.til_ctx);
        tilMaxTokens = view.findViewById(R.id.til_max_tokens);
        tilTemp = view.findViewById(R.id.til_temp);
        tilTopP = view.findViewById(R.id.til_top_p);

        tvVisionFile = view.findViewById(R.id.tv_vision_file);
        btnVision = view.findViewById(R.id.btn_vision);
        btnAdd = view.findViewById(R.id.btn_add);

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());

        String ggufPath = getArguments() != null ? getArguments().getString("gguf_path") : null;
        String modelId = getArguments() != null ? getArguments().getString("model_id") : null;

        if (modelId != null) {
            existingConfig = ModelManager.get(getContext()).getModelConfig(modelId);
            prefill(existingConfig);
            etModelId.setEnabled(false);
            btnAdd.setText("Save");
            ((TextView) view.findViewById(R.id.title)).setText("Edit Local Model");
        } else if (ggufPath != null) {
            ggufSource = new File(ggufPath);
            String slug = ggufSource.getName().replaceAll("[^a-zA-Z0-9.-]", "_").toLowerCase();
            etModelId.setText(slug);
            etAlias.setText(ggufSource.getName());
            etCtx.setText("2048");
            etMaxTokens.setText("4096");
            etTemp.setText("0.7");
            etTopP.setText("0.9");
        }

        btnVision.setOnClickListener(v -> pickMmproj());
        btnAdd.setOnClickListener(v -> save());
    }

    private void prefill(LocalChatModelConfig config) {
        etModelId.setText(config.modelId);
        etAlias.setText(config.alias);
        etCtx.setText(String.valueOf(config.nCtx));
        etMaxTokens.setText(String.valueOf(config.maxTokens));
        etTemp.setText(String.valueOf(config.temperature));
        etTopP.setText(String.valueOf(config.topP));
        if (config.mmprojPath != null && !config.mmprojPath.isEmpty()) {
            tvVisionFile.setText(new File(config.mmprojPath).getName());
            tvVisionFile.setVisibility(View.VISIBLE);
        }
    }

    private void pickMmproj() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_PICK_MMPROJ);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQ_PICK_MMPROJ && resultCode == Activity.RESULT_OK && data != null) {
            handleMmprojUri(data.getData());
        }
    }

    private void handleMmprojUri(Uri uri) {
        String fileName = "projector.mmproj";
        try (Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex("_display_name");
                if (nameIndex != -1) fileName = cursor.getString(nameIndex);
            }
        } catch (Exception ignored) {}

        if (!fileName.toLowerCase().endsWith(".mmproj")) {
            Toast.makeText(getContext(), "Only .mmproj files accepted", Toast.LENGTH_SHORT).show();
            return;
        }

        try (InputStream in = getContext().getContentResolver().openInputStream(uri)) {
            File temp = Paths.tempDownloadFile("mmproj_" + UUID.randomUUID().toString());
            try (FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            
            GgufInfo info = GgufValidator.validate(temp);
            if (info.valid) {
                tempMmproj = temp;
                tvVisionFile.setText(fileName);
                tvVisionFile.setVisibility(View.VISIBLE);
            } else {
                temp.delete();
                Toast.makeText(getContext(), "Invalid magic: " + info.error, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Read failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void save() {
        if (!validateFields()) return;

        String modelId = etModelId.getText().toString().trim();
        String alias = etAlias.getText().toString().trim();
        int ctx = Integer.parseInt(etCtx.getText().toString());
        int maxTokens = Integer.parseInt(etMaxTokens.getText().toString());
        float temp = Float.parseFloat(etTemp.getText().toString());
        float topP = Float.parseFloat(etTopP.getText().toString());
        
        String ggufPath = ggufSource != null ? Paths.modelFile(modelId).getAbsolutePath() : (existingConfig != null ? existingConfig.localFilePath : "");
        String mmproj = existingConfig != null ? existingConfig.mmprojPath : "";

        LocalChatModelConfig config = new LocalChatModelConfig(
                existingConfig != null ? existingConfig.id : null,
                modelId, alias, ggufPath, mmproj, ctx, temp, topP, maxTokens
        );

        ModelManager.get(getContext()).addOrUpdateLocalModel(config, ggufSource, tempMmproj);
        dismiss();
    }

    private boolean validateFields() {
        boolean ok = true;
        tilModelId.setError(null);
        tilAlias.setError(null);
        tilCtx.setError(null);
        tilTemp.setError(null);
        tilTopP.setError(null);

        String id = etModelId.getText().toString().trim();
        if (id.isEmpty()) {
            tilModelId.setError("Required");
            ok = false;
        }
        
        String alias = etAlias.getText().toString().trim();
        if (alias.isEmpty()) {
            tilAlias.setError("Required");
            ok = false;
        }

        try {
            int ctx = Integer.parseInt(etCtx.getText().toString());
            if (ctx < 128) { tilCtx.setError("Too small (min 128)"); ok = false; }
        } catch (Exception e) { tilCtx.setError("Invalid number"); ok = false; }

        try {
            float temp = Float.parseFloat(etTemp.getText().toString());
            if (temp < 0 || temp > 2) { tilTemp.setError("Must be 0-2"); ok = false; }
        } catch (Exception e) { tilTemp.setError("Invalid number"); ok = false; }

        try {
            float topP = Float.parseFloat(etTopP.getText().toString());
            if (topP < 0 || topP > 1) { tilTopP.setError("Must be 0-1"); ok = false; }
        } catch (Exception e) { tilTopP.setError("Invalid number"); ok = false; }

        return ok;
    }
}
