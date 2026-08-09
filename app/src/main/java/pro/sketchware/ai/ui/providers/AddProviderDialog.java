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

public class AddProviderDialog extends BottomSheetDialogFragment {

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

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_add).setOnClickListener(v -> {
            String name = edName.getText().toString().trim();
            String baseUrl = edBaseUrl.getText().toString().trim();
            String protocol = "openai-wire";
            int checkedId = protocolGroup.getCheckedChipId();
            if (checkedId == R.id.chip_google) protocol = "google-wire";
            else if (checkedId == R.id.chip_anthropic) protocol = "anthropic-wire";

            if (!name.isEmpty() && !baseUrl.isEmpty()) {
                ProviderRegistry.get(requireContext()).addCustom(name, protocol, baseUrl);
                dismiss();
            }
        });
    }
}
