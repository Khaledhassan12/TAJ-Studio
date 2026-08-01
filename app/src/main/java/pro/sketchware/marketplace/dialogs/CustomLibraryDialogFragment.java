package pro.sketchware.marketplace.dialogs;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;

import pro.sketchware.R;
import pro.sketchware.databinding.SheetCustomLibraryBinding;
import pro.sketchware.marketplace.services.LibraryInstallService;
import pro.sketchware.utility.SketchwareUtil;

/**
 * دايالوج مخصص (بشكل بوتم شيت) لإضافة مكتبات خارجية عبر الـ Maven Coordinate.
 * Custom bottom sheet dialog to add external libraries via Maven Coordinate.
 */
public class CustomLibraryDialogFragment extends BottomSheetDialogFragment {

    private SheetCustomLibraryBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetCustomLibraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        if (view.getParent() instanceof View) {
            ((View) view.getParent()).setBackgroundResource(R.drawable.shape_rounded_bottom_sheet);
        }

        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnInstallCustom.setOnClickListener(v -> {
            String coordinate = binding.etCoordinate.getText().toString().trim();
            if (coordinate.matches("^[^:]+:[^:]+:.+$")) {
                startInstallService(coordinate);
                dismiss();
            } else {
                binding.tilCoordinate.setError(getString(R.string.lib_custom_invalid));
            }
        });
    }

    private void startInstallService(String coordinate) {
        Intent intent = new Intent(requireContext(), LibraryInstallService.class);
        intent.setAction(LibraryInstallService.ACTION_INSTALL);
        ArrayList<String> coords = new ArrayList<>();
        coords.add(coordinate);
        intent.putStringArrayListExtra(LibraryInstallService.EXTRA_COORDINATES, coords);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent);
        } else {
            requireContext().startService(intent);
        }
        SketchwareUtil.toast(getString(R.string.lib_install_check_notif));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
