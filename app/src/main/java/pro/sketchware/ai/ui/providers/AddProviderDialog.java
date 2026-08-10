package pro.sketchware.ai.ui.providers;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.ChipGroup;

import pro.sketchware.R;
import pro.sketchware.ai.providers.ProviderRegistry;

/**
 * [WHAT] Dialog to add a custom cloud provider.
 * [WHY] Allows connecting to custom OpenAI/Anthropic/Google-compatible endpoints.
 * [HOW] R16 bound protocol selection; persists to local AI database.
 */
public class AddProviderDialog extends BottomSheetDialogFragment {

    private String selectedProtocol = "openai-wire";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_add_provider_new, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ChipGroup protocolGroup = view.findViewById(R.id.chip_group_protocol);
        EditText edName = view.findViewById(R.id.ed_name);
        EditText edBaseUrl = view.findViewById(R.id.ed_base_url);

        // R16: Bind selection to state writer
        protocolGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                applyProtocolState(protocolGroup, "openai-wire"); // Enforce selection
                return;
            }
            int id = checkedIds.get(0);
            if (id == R.id.chip_google) selectedProtocol = "google-wire";
            else if (id == R.id.chip_anthropic) selectedProtocol = "anthropic-wire";
            else selectedProtocol = "openai-wire";
        });

        // Initial state
        applyProtocolState(protocolGroup, selectedProtocol);

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_add).setOnClickListener(v -> {
            String name = edName.getText().toString().trim();
            String baseUrl = edBaseUrl.getText().toString().trim();

            if (!name.isEmpty() && !baseUrl.isEmpty()) {
                ProviderRegistry.get(requireContext()).addCustom(name, selectedProtocol, baseUrl);
                dismiss();
            }
        });
    }

    /**
     * [R16] Centralized state writer for protocol selection.
     */
    private void applyProtocolState(ChipGroup group, String protocol) {
        this.selectedProtocol = protocol;
        int idToCheck = R.id.chip_openai;
        if ("google-wire".equals(protocol)) idToCheck = R.id.chip_google;
        else if ("anthropic-wire".equals(protocol)) idToCheck = R.id.chip_anthropic;
        
        group.check(idToCheck);
    }
}
