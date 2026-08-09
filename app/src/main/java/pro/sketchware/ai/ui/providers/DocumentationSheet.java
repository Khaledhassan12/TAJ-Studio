package pro.sketchware.ai.ui.providers;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import pro.sketchware.R;

/**
 * [WHAT] Bottom sheet listing official documentation links.
 * [WHY] Provides quick access to API docs for all supported providers (Step 4).
 * [HOW] Grid of buttons; ACTION_VIEW on tap.
 */
public class DocumentationSheet extends BottomSheetDialogFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_documentation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Map as requested in Step 4
        view.findViewById(R.id.btn_google).setOnClickListener(v -> openUrl("https://ai.google.dev/"));
        view.findViewById(R.id.btn_openai).setOnClickListener(v -> openUrl("https://platform.openai.com/docs"));
        view.findViewById(R.id.btn_anthropic).setOnClickListener(v -> openUrl("https://docs.anthropic.com"));
        view.findViewById(R.id.btn_deepseek).setOnClickListener(v -> openUrl("https://api-docs.deepseek.com"));
        view.findViewById(R.id.btn_qwen).setOnClickListener(v -> openUrl("https://qwen.readthedocs.io"));
        view.findViewById(R.id.btn_groq).setOnClickListener(v -> openUrl("https://console.groq.com/docs"));
        view.findViewById(R.id.btn_ollama).setOnClickListener(v -> openUrl("https://github.com/ollama/ollama"));
        view.findViewById(R.id.btn_openrouter).setOnClickListener(v -> openUrl("https://openrouter.ai/docs"));
        view.findViewById(R.id.btn_llama_cpp).setOnClickListener(v -> openUrl("https://github.com/ggerganov/llama.cpp"));
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {}
    }
}
