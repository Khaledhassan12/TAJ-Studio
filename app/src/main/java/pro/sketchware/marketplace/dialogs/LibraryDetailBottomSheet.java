package pro.sketchware.marketplace.dialogs;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import dev.aldi.sayuti.editor.manage.LocalLibrary;
import pro.sketchware.R;
import pro.sketchware.databinding.FragmentLibraryDetailBinding;
import pro.sketchware.marketplace.models.MarketplaceLibrary;
import pro.sketchware.marketplace.services.LibraryInstallService;
import pro.sketchware.utility.SketchwareUtil;

/**
 * بوتم شيت تفاصيل المكتبة - تم تحسينه بحواف مدورة وأزرار صلبة ومعالجة حقيقية للتثبيت.
 * Library detail sheet - refined with rounded corners, solid buttons, and real install handling.
 */
public class LibraryDetailBottomSheet extends BottomSheetDialogFragment {

    private FragmentLibraryDetailBinding binding;
    private MarketplaceLibrary library;
    private Runnable onDismissListener;

    public static LibraryDetailBottomSheet newInstance(MarketplaceLibrary library) {
        LibraryDetailBottomSheet fragment = new LibraryDetailBottomSheet();
        fragment.library = library;
        return fragment;
    }

    public void setOnDismissListener(Runnable listener) {
        this.onDismissListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLibraryDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // T1 & T3: Apply rounded corners properly
        if (view.getParent() instanceof View) {
            View parent = (View) view.getParent();
            parent.setBackgroundResource(R.drawable.shape_rounded_bottom_sheet);
        }
        
        setupUI();
    }

    private void setupUI() {
        binding.tvDetailName.setText(library.getDisplayName());
        binding.tvDetailCoordinate.setText(library.getCoordinate());
        binding.tvDetailDescription.setText(library.getDescription());
        binding.tvDetailVersion.setText(library.getStableVersion());
        binding.tvDetailAndroidx.setText(library.isAndroidx() ? R.string.lib_androidx_yes : R.string.lib_androidx_no);

        // T4: Real Icons
        if (library.getIconRes() != 0) {
            binding.ivDetailIcon.setImageResource(library.getIconRes());
        }

        if (library.getDownloads() != null) {
            binding.rowDownloads.setVisibility(View.VISIBLE);
            binding.tvDetailDownloads.setText(String.format(Locale.getDefault(), "%,d+", library.getDownloads()));
        } else {
            binding.rowDownloads.setVisibility(View.GONE);
        }

        binding.btnDocs.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(library.getDocUrl()));
                startActivity(intent);
            } catch (Exception e) {
                SketchwareUtil.toast("Could not open documentation");
            }
        });

        checkConflicts();
        updateInstallButton();

        binding.btnInstall.setOnClickListener(v -> startInstallProcess());
        
        binding.btnBgTask.setOnClickListener(v -> {
            // T8: Dismiss and let it run in background
            SketchwareUtil.toast(getString(R.string.lib_install_check_notif));
            dismissAllowingStateLoss();
        });

        if (library.getUsageSnippet() != null) {
            binding.tvUsageSnippet.setText(library.getUsageSnippet());
            binding.btnCopy.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("code", library.getUsageSnippet());
                clipboard.setPrimaryClip(clip);
                SketchwareUtil.toast(getString(R.string.lib_copied));
            });
        }
    }

    private void startInstallProcess() {
        // Update UI to installing state
        binding.btnInstall.setEnabled(false);
        binding.btnInstall.setAlpha(0.5f);
        binding.btnBgTask.setVisibility(View.VISIBLE);
        binding.pbInstallHorizontal.setVisibility(View.VISIBLE);

        // T10: Start service
        Intent intent = new Intent(requireContext(), LibraryInstallService.class);
        intent.setAction(LibraryInstallService.ACTION_INSTALL);
        ArrayList<String> coords = new ArrayList<>();
        coords.add(library.getCoordinate());
        intent.putStringArrayListExtra(LibraryInstallService.EXTRA_COORDINATES, coords);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent);
        } else {
            requireContext().startService(intent);
        }
    }

    private void checkConflicts() {
        List<String> installed = LocalLibrariesUtil.getAllLocalLibraries().stream()
                .map(LocalLibrary::getName)
                .collect(Collectors.toList());

        for (String conflictId : library.getConflictsWith()) {
            if (installed.stream().anyMatch(name -> name.toLowerCase().contains(conflictId.toLowerCase()))) {
                binding.cardConflict.setVisibility(View.VISIBLE);
                binding.tvConflictWarn.setText(getString(R.string.lib_conflict_warn, conflictId));
                return;
            }
        }
        binding.cardConflict.setVisibility(View.GONE);
    }

    private boolean isInstalled() {
        return LocalLibrariesUtil.getAllLocalLibraries().stream()
                .anyMatch(l -> l.getName().equalsIgnoreCase(library.getId()) || l.getName().toLowerCase().contains(library.getId().toLowerCase()));
    }

    private void updateInstallButton() {
        if (isInstalled()) {
            binding.btnInstall.setText(R.string.lib_installed);
            binding.btnInstall.setEnabled(false);
            binding.btnInstall.setAlpha(0.6f);
            if (library.getUsageSnippet() != null) {
                binding.layoutQuickStart.setVisibility(View.VISIBLE);
            }
        } else {
            binding.btnInstall.setText(R.string.lib_install);
            binding.btnInstall.setEnabled(true);
            binding.btnInstall.setAlpha(1.0f);
            binding.layoutQuickStart.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (onDismissListener != null) onDismissListener.run();
        binding = null;
    }
}
