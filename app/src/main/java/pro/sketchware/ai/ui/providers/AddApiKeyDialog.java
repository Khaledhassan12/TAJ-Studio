package pro.sketchware.ai.ui.providers;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputLayout;

import pro.sketchware.R;
import pro.sketchware.ai.data.SecureKeyStore;
import pro.sketchware.ai.providers.ProviderConfig;
import pro.sketchware.ai.providers.ProviderRegistry;

public class AddApiKeyDialog extends BottomSheetDialogFragment {

    public interface Listener { void onKeyAdded(); }
    private Listener listener;
    private String providerId;

    public static AddApiKeyDialog newInstance(String providerId) {
        AddApiKeyDialog fragment = new AddApiKeyDialog();
        Bundle args = new Bundle();
        args.putString("provider_id", providerId);
        fragment.setArguments(args);
        return fragment;
    }

    public void setListener(Listener listener) { this.listener = listener; }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_add_api_key, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        providerId = getArguments().getString("provider_id");
        ProviderConfig config = ProviderRegistry.get(requireContext()).findById(providerId);
        
        TextInputLayout tilKey = view.findViewById(R.id.til_key);
        if (config != null && tilKey != null) {
            tilKey.setHint(config.displayName + " API Key");
        }

        EditText edName = view.findViewById(R.id.ed_name);
        EditText edKey = view.findViewById(R.id.ed_key);
        
        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_add).setOnClickListener(v -> {
            String name = edName.getText().toString().trim();
            String key = edKey.getText().toString().trim();
            if (!name.isEmpty() && !key.isEmpty()) {
                SecureKeyStore.get(requireContext()).addKey(providerId, name, key);
                if (listener != null) listener.onKeyAdded();
                dismiss();
            }
        });
    }
}
