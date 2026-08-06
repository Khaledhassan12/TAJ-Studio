package pro.sketchware.marketplace.dialogs;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import com.google.android.material.snackbar.Snackbar;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import dev.aldi.sayuti.editor.manage.LocalLibrary;
import pro.sketchware.R;
import pro.sketchware.databinding.FragmentLibraryDetailBinding;
import pro.sketchware.marketplace.models.MarketplaceLibrary;
import pro.sketchware.marketplace.services.InstallStateHub;
import pro.sketchware.marketplace.services.LibraryInstallService;
import pro.sketchware.utility.SketchwareUtil;

/**
 * بوتم شيت تفاصيل المكتبة - تم تحسينه بنقل العمليات الثقيلة للخلفية وإضافة مستمع حي للتنزيل.
 * Library detail sheet - improved by moving heavy I/O to background and adding live install listener.
 */
public class LibraryDetailBottomSheet extends BottomSheetDialogFragment {

    private FragmentLibraryDetailBinding binding;
    private MarketplaceLibrary library;
    private Runnable onDismissListener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    private final InstallStateHub.Listener hubListener = (coordinate, entry) -> {
        if (library.getCoordinate().equals(coordinate)) {
            applyInstallUiState(entry.state, entry.progress, entry.message);
        }
    };

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (LibraryInstallService.ACTION_LIBRARY_INSTALLED.equals(action)) {
                String installedName = intent.getStringExtra(LibraryInstallService.EXTRA_LIBRARY_NAME);
                if (installedName != null && library.getCoordinate().contains(installedName)) {
                    showSuccessState();
                }
            }
            // Logic moved to Hub listener mostly, but keep for legacy broadcast compatibility
            checkStatusAsync();
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // WHAT: Notification permission gate for Android 13+.
        // HOW: Registering launcher to handle user response before starting service.
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (!isGranted) {
                        Log.w("LibDetail", "Notification permission denied - progress won't be visible");
                    }
                    // Proceed with installation regardless of permission
                    actuallyStartService();
                }
        );
    }

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
        
        // P3: Apply rounded corners properly
        if (view.getParent() instanceof View) {
            View parent = (View) view.getParent();
            parent.setBackgroundResource(R.drawable.shape_rounded_bottom_sheet);
        }
        
        // R5: Registry Hub Listener for live updates
        InstallStateHub.getInstance().addListener(hubListener);

        setupUI();
        
        // R5: Immediate render from Hub (memory speed)
        InstallStateHub.Entry entry = InstallStateHub.getInstance().get(library.getCoordinate());
        if (entry != null && entry.state != InstallStateHub.State.IDLE) {
            applyInstallUiState(entry.state, entry.progress, entry.message);
        } else {
            // Cold start: Check disk (installedCache should be fast)
            boolean isAlreadyInstalled = pro.sketchware.marketplace.utils.MarketplaceHelper.isInstalledSync(library);
            applyInstallUiState(isAlreadyInstalled ? InstallStateHub.State.SUCCESS : InstallStateHub.State.IDLE, 0, null);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(LibraryInstallService.ACTION_STATUS_CHANGE);
        filter.addAction(LibraryInstallService.ACTION_LIBRARY_INSTALLED);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(statusReceiver, filter);
        }
    }

    private void applyInstallUiState(InstallStateHub.State state, int progress, String message) {
        if (binding == null) return;

        boolean isBusy = state == InstallStateHub.State.QUEUED || 
                        state == InstallStateHub.State.DOWNLOADING || 
                        state == InstallStateHub.State.EXTRACTING || 
                        state == InstallStateHub.State.DEXING;

        // Button state
        if (isBusy) {
            binding.btnInstall.setText("Installing…");
            binding.btnInstall.setEnabled(false);
            binding.btnInstall.setAlpha(0.5f);
        } else if (state == InstallStateHub.State.SUCCESS) {
            binding.btnInstall.setText(R.string.lib_installed);
            binding.btnInstall.setEnabled(false);
            binding.btnInstall.setAlpha(0.6f);
        } else if (state == InstallStateHub.State.FAILED) {
            binding.btnInstall.setText("Retry");
            binding.btnInstall.setEnabled(true);
            binding.btnInstall.setAlpha(1.0f);
        } else {
            binding.btnInstall.setText(R.string.lib_install);
            binding.btnInstall.setEnabled(true);
            binding.btnInstall.setAlpha(1.0f);
        }

        // Progress and helpers
        binding.pbInstallHorizontal.setVisibility(isBusy ? View.VISIBLE : View.GONE);
        if (isBusy) {
            boolean indeterminate = state == InstallStateHub.State.EXTRACTING || state == InstallStateHub.State.DEXING || progress == 0;
            binding.pbInstallHorizontal.setIndeterminate(indeterminate);
            if (!indeterminate) binding.pbInstallHorizontal.setProgress(progress);
        }

        binding.btnBgTask.setVisibility(isBusy ? View.VISIBLE : View.GONE);
        binding.btnCancelInstall.setVisibility(isBusy ? View.VISIBLE : View.GONE);
        
        if (state == InstallStateHub.State.SUCCESS && library.getUsageSnippet() != null) {
            binding.layoutQuickStart.setVisibility(View.VISIBLE);
        }
        
        if (message != null && !message.isEmpty()) {
            binding.tvDetailCoordinate.setText(message); // Temporary status overlay
        } else {
            binding.tvDetailCoordinate.setText(library.getCoordinate());
        }
    }

    private void setupUI() {
        binding.tvDetailName.setText(library.getDisplayName());
        binding.tvDetailCoordinate.setText(library.getCoordinate());
        binding.tvDetailDescription.setText(library.getDescription());
        binding.tvDetailVersion.setText(library.getStableVersion());
        binding.tvDetailAndroidx.setText(library.isAndroidx() ? R.string.lib_androidx_yes : R.string.lib_androidx_no);
        
        // P7: Bind new fields
        if (library.getCategory() != null) {
            binding.tvDetailCategory.setText(library.getCategory());
        }
        
        binding.tvDetailMinSdk.setText(getString(R.string.lib_min_sdk, library.getMinSdk()));
        
        if (library.getLicense() != null) {
            binding.tvDetailLicense.setText(library.getLicense());
        }

        binding.chipKotlin.setVisibility(library.isKotlinSupport() ? View.VISIBLE : View.GONE);
        binding.chipJava.setVisibility(library.isJavaSupport() ? View.VISIBLE : View.GONE);
        binding.chipCompose.setVisibility(library.isComposeSupport() ? View.VISIBLE : View.GONE);

        if (library.getGithubUrl() != null) {
            binding.btnGithub.setVisibility(View.VISIBLE);
            binding.btnGithub.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(library.getGithubUrl())));
                } catch (Exception e) {
                    SketchwareUtil.toast("Could not open GitHub");
                }
            });
        } else {
            binding.btnGithub.setVisibility(View.GONE);
        }

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

        // T5: Check status in background for deeper verification (conflicts)
        checkStatusAsync();

        binding.btnInstall.setOnClickListener(v -> startInstallProcess());
        
        binding.btnBgTask.setOnClickListener(v -> {
            // G5-D: Dismiss and let it run in background with no annoying Toast.
            dismissAllowingStateLoss();
        });

        binding.btnCancelInstall.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), LibraryInstallService.class);
            intent.setAction(LibraryInstallService.ACTION_CANCEL);
            requireContext().startService(intent);
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

        // WHAT: Version Picker Integration.
        // HOW: Opening the picker sheet and updating coordinates/UI on selection.
        binding.btnVersions.setOnClickListener(v -> {
            LibraryVersionPickerSheet.newInstance(library, version -> {
                String[] parts = library.getCoordinate().split(":");
                if (parts.length >= 2) {
                    String newCoordinate = parts[0] + ":" + parts[1] + ":" + version;
                    library.setCoordinate(newCoordinate);
                    library.setStableVersion(version);

                    binding.tvDetailCoordinate.setText(newCoordinate);
                    binding.tvDetailVersion.setText(version);

                    actuallyStartService();
                }
            }).show(getParentFragmentManager(), "version_picker");
        });
    }

    private void checkStatusAsync() {
        if (binding == null) return;
        
        executor.execute(() -> {
            // pro.sketchware.marketplace.utils.MarketplaceHelper.refreshCache(); // Removed from here per user request
            boolean installed = pro.sketchware.marketplace.utils.MarketplaceHelper.isInstalledSync(library);
            String conflictId = getConflictId();
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        binding.pbChecking.setVisibility(View.GONE);
                        // updateInstallButton(installed); // Replaced by applyInstallUiState
                        if (InstallStateHub.getInstance().get(library.getCoordinate()) == null) {
                             applyInstallUiState(installed ? InstallStateHub.State.SUCCESS : InstallStateHub.State.IDLE, 0, null);
                        }
                        showConflict(conflictId);
                    }
                });
            }
        });
    }

    private void startInstallProcess() {
        // WHAT: Check for notification permission before starting foreground service.
        // HOW: Using launcher on SDK 33+, otherwise starting directly.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }
        actuallyStartService();
    }

    private void actuallyStartService() {
        if (binding == null) return;
        
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

    private void showSuccessState() {
        if (binding == null) return;
        
        // WHAT: Visual feedback on successful installation.
        // HOW: Scaling animation + Green Snackbar with disk verification confirmation.
        binding.btnInstall.setText("Installed ✓");
        binding.btnInstall.setEnabled(false);
        binding.btnInstall.setAlpha(0.6f);
        binding.btnInstall.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200)
                .withEndAction(() -> binding.btnInstall.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200));
        
        binding.pbInstallHorizontal.setVisibility(View.GONE);
        binding.btnBgTask.setVisibility(View.GONE);
        
        Snackbar snackbar = Snackbar.make(binding.getRoot(), 
                library.getDisplayName() + " installed and verified on disk", Snackbar.LENGTH_SHORT);
        snackbar.setBackgroundTint(0xFF00C853);
        snackbar.setTextColor(0xFFFFFFFF);
        snackbar.show();
    }

    private String getConflictId() {
        List<String> installed = LocalLibrariesUtil.getAllLocalLibraries().stream()
                .map(LocalLibrary::getName)
                .collect(Collectors.toList());

        for (String conflictId : library.getConflictsWith()) {
            if (installed.stream().anyMatch(name -> name.toLowerCase().contains(conflictId.toLowerCase()))) {
                return conflictId;
            }
        }
        return null;
    }

    private void showConflict(String conflictId) {
        if (conflictId != null) {
            binding.cardConflict.setVisibility(View.VISIBLE);
            binding.tvConflictWarn.setText(getString(R.string.lib_conflict_warn, conflictId));
        } else {
            binding.cardConflict.setVisibility(View.GONE);
        }
    }


    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (onDismissListener != null) onDismissListener.run();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // WHAT: Memory leak prevention - unregistering receiver and clearing references.
        // WHY: Fragment may outlive its view; executor and context-bound receivers must be cleared.
        InstallStateHub.getInstance().removeListener(hubListener);
        requireContext().unregisterReceiver(statusReceiver);
        executor.shutdownNow();
        onDismissListener = null;
        binding = null;
    }
}
