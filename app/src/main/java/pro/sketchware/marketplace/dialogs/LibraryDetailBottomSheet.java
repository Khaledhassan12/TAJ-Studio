package pro.sketchware.marketplace.dialogs;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.gson.Gson;

import org.cosmic.ide.dependency.resolver.api.Artifact;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import dev.aldi.sayuti.editor.manage.LocalLibrary;
import mod.hey.studios.build.BuildSettings;
import mod.jbk.build.BuiltInLibraries;
import mod.pranav.dependency.resolver.DependencyResolver;
import pro.sketchware.R;
import pro.sketchware.databinding.FragmentLibraryDetailBinding;
import pro.sketchware.marketplace.models.MarketplaceLibrary;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

/**
 * بوتم شيت تفاصيل المكتبة وعملية التثبيت.
 * Bottom sheet for library details and the installation process.
 */
public class LibraryDetailBottomSheet extends BottomSheetDialogFragment {

    private FragmentLibraryDetailBinding binding;
    private MarketplaceLibrary library;
    private Runnable onDismissListener;
    private final Gson gson = new Gson();

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
        setupUI();
    }

    private void setupUI() {
        binding.tvDetailName.setText(library.getDisplayName());
        binding.tvDetailCoordinate.setText(library.getCoordinate());
        binding.tvDetailDescription.setText(library.getDescription());
        binding.tvDetailVersion.setText(library.getStableVersion());
        binding.tvDetailInitial.setText(library.getDisplayName().substring(0, 1).toUpperCase());
        binding.tvDetailAndroidx.setText(library.isAndroidx() ? R.string.lib_androidx_yes : R.string.lib_androidx_no);

        if (library.getDownloads() != null) {
            binding.rowDownloads.setVisibility(View.VISIBLE);
            binding.tvDetailDownloads.setText(String.format(Locale.getDefault(), "%,d+", library.getDownloads()));
        } else {
            binding.rowDownloads.setVisibility(View.GONE);
        }

        binding.btnDocs.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(library.getDocUrl()));
            startActivity(intent);
        });

        checkConflicts();
        updateInstallButton();

        binding.btnInstall.setOnClickListener(v -> startInstall());

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

    private void startInstall() {
        binding.btnInstall.setVisibility(View.GONE);
        binding.pbInstall.setVisibility(View.VISIBLE);

        String[] parts = library.getCoordinate().split(":");
        if (parts.length != 3) {
            SketchwareUtil.toast("Invalid coordinate format");
            resetInstallUI();
            return;
        }

        // We use "system" as scId if not in a project context, or we could just use a dummy.
        // DependencyResolver needs BuildSettings.
        BuildSettings buildSettings = new BuildSettings("system");
        DependencyResolver resolver = new DependencyResolver(parts[0], parts[1], parts[2], false, buildSettings);
        Handler handler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            BuiltInLibraries.maybeExtractAndroidJar((message, progress) -> {});
            BuiltInLibraries.maybeExtractCoreLambdaStubsJar();

            resolver.resolveDependency(new DependencyResolver.DependencyResolverCallback() {
                @Override
                public void onResolutionComplete(@NonNull Artifact dep) {
                    // One part complete
                }

                @Override
                public void onTaskCompleted(@NonNull List<String> dependencies) {
                    handler.post(() -> {
                        SketchwareUtil.toast(getString(R.string.lib_installed_toast, library.getDisplayName()));
                        SketchwareUtil.toast(getString(R.string.lib_next_step));
                        resetInstallUI();
                        updateInstallButton();
                        if (onDismissListener != null) onDismissListener.run();
                    });
                }

                @Override
                public void onDownloadError(@NonNull Artifact dep, @NonNull Throwable e) {
                    handler.post(() -> {
                        SketchwareUtil.toast(getString(R.string.lib_install_failed, library.getDisplayName()));
                        resetInstallUI();
                    });
                }
                
                @Override
                public void onArtifactNotFound(@NonNull Artifact dep) {
                    handler.post(() -> {
                        SketchwareUtil.toast("Artifact not found");
                        resetInstallUI();
                    });
                }
            });
        }).start();
    }

    private void resetInstallUI() {
        binding.btnInstall.setVisibility(View.VISIBLE);
        binding.pbInstall.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
