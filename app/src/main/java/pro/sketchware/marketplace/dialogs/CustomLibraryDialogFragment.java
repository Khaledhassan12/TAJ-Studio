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
        
        binding.btnMoreInfo.setOnClickListener(v -> {
            LibraryInfoBottomSheet infoSheet = new LibraryInfoBottomSheet();
            infoSheet.show(getChildFragmentManager(), "LibraryInfo");
        });

        binding.btnInstallCustom.setOnClickListener(v -> {
            String input = binding.etCoordinate.getText().toString().trim();
            if (input.isEmpty()) return;

            // P8: Support GitHub repositories
            if (input.contains("github.com") || input.matches("^[^/:]+/[^/:]+$")) {
                String coordinate = convertGitHubToJitPack(input);
                startInstallService(coordinate);
                dismiss();
            } else if (input.matches("^[^:]+:[^:]+:.+$")) {
                // Maven coordinate
                startInstallService(input);
                dismiss();
            } else {
                binding.tilCoordinate.setError(getString(R.string.lib_custom_invalid));
            }
        });
    }

    /**
     * يحول رابط GitHub أو "user/repo" إلى إحداثيات JitPack.
     * Converts GitHub URL or "user/repo" to JitPack coordinate.
     */
    private String convertGitHubToJitPack(String input) {
        String repo = input.replace("https://github.com/", "")
                          .replace("http://github.com/", "");
        
        // Remove trailing slashes or .git
        if (repo.endsWith("/")) repo = repo.substring(0, repo.length() - 1);
        if (repo.endsWith(".git")) repo = repo.substring(0, repo.length() - 4);

        String[] parts = repo.split("/");
        if (parts.length >= 2) {
            // Format: com.github.user:repo:master-SNAPSHOT (fallback version)
            return "com.github." + parts[0] + ":" + parts[1] + ":master-SNAPSHOT";
        }
        return input; // Fallback
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
