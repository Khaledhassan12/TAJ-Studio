package pro.sketchware.marketplace.dialogs;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
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
 * [R5-R8 State Maintenance] Library detail sheet - Re-engineered for Hub-driven state.
 * (عربي) بوتم شيت تفاصيل المكتبة - تم إعادة هندسته ليعتمد على الـ Hub؛ فوري وبلا انتظار.
 */
public class LibraryDetailBottomSheet extends BottomSheetDialogFragment {

    private FragmentLibraryDetailBinding binding;
    private MarketplaceLibrary library;
    private Runnable onDismissListener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    
    // R6: Generation token for background disk checks
    private final AtomicLong checkGeneration = new AtomicLong(0);

    private final InstallStateHub.Listener hubListener = (coordinate, entry) -> {
        if (library != null && library.getCoordinate().equals(coordinate)) {
            applyInstallUiState(entry, false); // onDisk will be checked if entry is IDLE
        }
    };

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (LibraryInstallService.ACTION_LIBRARY_INSTALLED.equals(action)) {
                String installedName = intent.getStringExtra(LibraryInstallService.EXTRA_LIBRARY_NAME);
                if (installedName != null && library.getCoordinate().contains(installedName)) {
                    showSuccessFeedback();
                }
            }
            refreshHubDrivenState();
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) actuallyStartService();
                    else Log.w("LibDetail", "Notification permission denied");
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
        
        if (view.getParent() instanceof View) {
            ((View) view.getParent()).setBackgroundResource(R.drawable.shape_rounded_bottom_sheet);
        }
        
        InstallStateHub.get().addListener(hubListener);
        setupUI();
        refreshHubDrivenState();
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(LibraryInstallService.ACTION_STATUS_CHANGE);
        filter.addAction(LibraryInstallService.ACTION_LIBRARY_INSTALLED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(statusReceiver, filter);
        }
    }

    /**
     * WHAT: refreshHubDrivenState - Unified entry point for UI updates.
     * WHY: [R5] Reads from Hub first (memory speed) then falls back to disk cache.
     */
    private void refreshHubDrivenState() {
        if (library == null || binding == null) return;
        
        InstallStateHub.Entry hubEntry = InstallStateHub.get().get(library.getCoordinate());
        if (hubEntry != null && hubEntry.state != InstallStateHub.State.IDLE) {
            applyInstallUiState(hubEntry, false);
        } else {
            // Check disk (should be fast due to optimized refreshCache logic)
            boolean onDisk = pro.sketchware.marketplace.utils.MarketplaceHelper.isInstalledSync(library);
            applyInstallUiState(null, onDisk);
            
            // Deep check in background (R6)
            checkStatusAsync();
        }
    }

    /**
     * WHAT: applyInstallUiState - The ONLY writer for installation-related UI.
     * WHY: Ensures visual consistency and derived states (Cancel/Progress/Button).
     */
    private void applyInstallUiState(@Nullable InstallStateHub.Entry entry, boolean onDisk) {
        if (binding == null) return;

        InstallStateHub.State state = (entry != null) ? entry.state : (onDisk ? InstallStateHub.State.SUCCESS : InstallStateHub.State.IDLE);
        int progress = (entry != null) ? entry.progress : 0;
        String message = (entry != null) ? entry.message : null;

        boolean isBusy = state == InstallStateHub.State.QUEUED || 
                        state == InstallStateHub.State.DOWNLOADING || 
                        state == InstallStateHub.State.EXTRACTING || 
                        state == InstallStateHub.State.DEXING;

        // 1. Button Logic
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
            if (message != null) {
                Snackbar.make(binding.getRoot(), "Failed: " + message, Snackbar.LENGTH_LONG).show();
            }
        } else {
            binding.btnInstall.setText(R.string.lib_install);
            binding.btnInstall.setEnabled(true);
            binding.btnInstall.setAlpha(1.0f);
        }

        // 2. Progress Logic
        binding.pbInstallHorizontal.setVisibility(isBusy ? View.VISIBLE : View.GONE);
        if (isBusy) {
            boolean indeterminate = progress == -1 || state == InstallStateHub.State.EXTRACTING || state == InstallStateHub.State.DEXING;
            binding.pbInstallHorizontal.setIndeterminate(indeterminate);
            if (!indeterminate) binding.pbInstallHorizontal.setProgress(progress);
        }

        // 3. Aux Buttons
        binding.btnBgTask.setVisibility(isBusy ? View.VISIBLE : View.GONE);
        binding.btnCancelInstall.setVisibility(isBusy ? View.VISIBLE : View.GONE);
        
        // 4. Quick Start
        binding.layoutQuickStart.setVisibility(state == InstallStateHub.State.SUCCESS && library.getUsageSnippet() != null ? View.VISIBLE : View.GONE);
        
        // 5. Message overlay
        if (isBusy && message != null) {
            binding.tvDetailCoordinate.setText(message);
        } else {
            binding.tvDetailCoordinate.setText(library.getCoordinate());
        }
    }

    private void setupUI() {
        binding.tvDetailName.setText(library.getDisplayName());
        binding.tvDetailDescription.setText(library.getDescription());
        binding.tvDetailVersion.setText(library.getStableVersion());
        binding.tvDetailAndroidx.setText(library.isAndroidx() ? R.string.lib_androidx_yes : R.string.lib_androidx_no);
        
        if (library.getCategory() != null) binding.tvDetailCategory.setText(library.getCategory());
        binding.tvDetailMinSdk.setText(getString(R.string.lib_min_sdk, library.getMinSdk()));
        if (library.getLicense() != null) binding.tvDetailLicense.setText(library.getLicense());

        binding.chipKotlin.setVisibility(library.isKotlinSupport() ? View.VISIBLE : View.GONE);
        binding.chipJava.setVisibility(library.isJavaSupport() ? View.VISIBLE : View.GONE);
        binding.chipCompose.setVisibility(library.isComposeSupport() ? View.VISIBLE : View.GONE);

        if (library.getGithubUrl() != null) {
            binding.btnGithub.setVisibility(View.VISIBLE);
            binding.btnGithub.setOnClickListener(v -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(library.getGithubUrl()))); }
                catch (Exception e) { SketchwareUtil.toast("Could not open GitHub"); }
            });
        } else {
            binding.btnGithub.setVisibility(View.GONE);
        }

        if (library.getIconRes() != 0) binding.ivDetailIcon.setImageResource(library.getIconRes());

        if (library.getDownloads() != null) {
            binding.rowDownloads.setVisibility(View.VISIBLE);
            binding.tvDetailDownloads.setText(String.format(Locale.getDefault(), "%,d+", library.getDownloads()));
        } else {
            binding.rowDownloads.setVisibility(View.GONE);
        }

        binding.btnDocs.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(library.getDocUrl()))); }
            catch (Exception e) { SketchwareUtil.toast("Could not open documentation"); }
        });

        binding.btnInstall.setOnClickListener(v -> startInstallProcess());
        binding.btnBgTask.setOnClickListener(v -> dismissAllowingStateLoss());
        
        binding.btnCancelInstall.setOnClickListener(v -> {
            LibraryInstallService.cancelInstall(requireContext(), library.getCoordinate());
        });

        if (library.getUsageSnippet() != null) {
            binding.tvUsageSnippet.setText(library.getUsageSnippet());
            binding.btnCopy.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("code", library.getUsageSnippet()));
                SketchwareUtil.toast(getString(R.string.lib_copied));
            });
        }

        binding.btnVersions.setOnClickListener(v -> {
            LibraryVersionPickerSheet.newInstance(library, version -> {
                String[] parts = library.getCoordinate().split(":");
                if (parts.length >= 2) {
                    String newCoordinate = parts[0] + ":" + parts[1] + ":" + version;
                    library.setCoordinate(newCoordinate);
                    library.setStableVersion(version);
                    binding.tvDetailVersion.setText(version);
                    refreshHubDrivenState(); // Re-render for new coordinate
                }
            }).show(getParentFragmentManager(), "version_picker");
        });
    }

    private void checkStatusAsync() {
        long currentGen = checkGeneration.incrementAndGet();
        executor.execute(() -> {
            // Note: pro.sketchware.marketplace.utils.MarketplaceHelper.refreshCache() is NOT called here
            boolean installed = pro.sketchware.marketplace.utils.MarketplaceHelper.isInstalledSync(library);
            String conflictId = getConflictId();
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (binding != null && currentGen == checkGeneration.get()) {
                        binding.pbChecking.setVisibility(View.GONE);
                        showConflict(conflictId);
                        // Only update from disk if Hub doesn't have an active task
                        if (InstallStateHub.get().get(library.getCoordinate()) == null) {
                             applyInstallUiState(null, installed);
                        }
                    }
                });
            }
        });
    }

    private void startInstallProcess() {
        InstallStateHub.Entry entry = InstallStateHub.get().get(library.getCoordinate());
        if (entry != null && entry.state != InstallStateHub.State.IDLE && entry.state != InstallStateHub.State.FAILED && entry.state != InstallStateHub.State.SUCCESS) {
            return; // Busy guard
        }
        
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

    private void showSuccessFeedback() {
        if (binding == null) return;
        binding.btnInstall.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200)
                .withEndAction(() -> binding.btnInstall.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200));
        
        Snackbar snackbar = Snackbar.make(binding.getRoot(), 
                library.getDisplayName() + " installed and verified", Snackbar.LENGTH_SHORT);
        snackbar.setBackgroundTint(0xFF00C853);
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
    public void onDestroyView() {
        super.onDestroyView();
        InstallStateHub.get().removeListener(hubListener);
        requireContext().unregisterReceiver(statusReceiver);
        executor.shutdownNow();
        onDismissListener = null;
        binding = null;
    }
}
