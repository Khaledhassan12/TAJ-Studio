package pro.sketchware.activities.main.fragments.git;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pro.sketchware.R;
import pro.sketchware.databinding.FragmentGitUploadsBinding;
import pro.sketchware.github.GitHubManager;
import pro.sketchware.github.GitHubSignInSheet;

/**
 * [R5-R8 State Maintenance] GitUploadsFragment - Centralized selection state.
 * (عربي) الشاشة الرئيسية لتاب "Git" - إعادة هندسة الحالة: كاتب وحيد للتحديد، ومصدر حقيقة صلب.
 */
public class GitUploadsFragment extends Fragment implements GitHubManager.AuthStateListener {

    private FragmentGitUploadsBinding binding;
    private GitHubManager gitHubManager;
    private GitUploadsAdapter adapter;
    private OnBackPressedCallback backCallback;

    // R5: Selection SSOT
    private boolean selectionMode = false;
    private final Set<String> selectedRepoUrls = new HashSet<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gitHubManager = GitHubManager.getInstance(requireContext());
        
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                exitSelectionMode();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(this, backCallback);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentGitUploadsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        binding.swipeRefresh.setOnRefreshListener(this::refreshUi);
        binding.btnConnect.setOnClickListener(v -> {
            GitHubSignInSheet.newInstance("GitHub")
                    .show(getParentFragmentManager(), "GitHubSignIn");
        });

        binding.headerCard.setOnClickListener(v -> {
            if (selectionMode) exitSelectionMode();
        });

        binding.masterDeleteButton.setOnClickListener(v -> openDeleteConfirmDialog());

        refreshUi();
    }

    // --- Monopoly Writers (Single Source of Truth) ---

    /**
     * WHAT: applySelectionState - The ONLY writer for multi-selection UI.
     * WHY: [R5] Synchronizes toolbar, stats, back button, and adapter state.
     * (عربي) الكاتب الوحيد لحالة التحديد - يضمن تزامن شريط الأدوات والعدّاد وزر الرجوع.
     */
    private void applySelectionState() {
        if (binding == null) return;

        backCallback.setEnabled(selectionMode);
        binding.swipeRefresh.setEnabled(!selectionMode);
        
        if (selectionMode) {
            binding.masterDeleteButton.setVisibility(View.VISIBLE);
            if (binding.masterDeleteButton.getScaleX() == 0) {
                binding.masterDeleteButton.setScaleX(0f);
                binding.masterDeleteButton.setScaleY(0f);
                binding.masterDeleteButton.animate().scaleX(1f).scaleY(1f).setDuration(250).start();
            }
            binding.syncStats.setText(getString(R.string.git_selection_count, selectedRepoUrls.size()));
            binding.masterDeleteButton.setAlpha(selectedRepoUrls.isEmpty() ? 0.4f : 1.0f);
            binding.masterDeleteButton.setClickable(!selectedRepoUrls.isEmpty());
        } else {
            binding.masterDeleteButton.animate().scaleX(0f).scaleY(0f).setDuration(200)
                    .withEndAction(() -> { if (binding != null) binding.masterDeleteButton.setVisibility(View.GONE); }).start();
            updateStatsText(gitHubManager.getUploadRecords());
        }

        if (adapter != null) {
            adapter.setSelectionState(selectionMode, selectedRepoUrls);
        }
    }

    public void enterSelectionMode(String initialUrl) {
        selectionMode = true;
        selectedRepoUrls.clear();
        selectedRepoUrls.add(initialUrl);
        applySelectionState();
    }

    public void toggleSelection(String url) {
        if (selectedRepoUrls.contains(url)) {
            selectedRepoUrls.remove(url);
        } else {
            selectedRepoUrls.add(url);
        }
        
        if (selectedRepoUrls.isEmpty()) {
            exitSelectionMode();
        } else {
            applySelectionState();
        }
    }

    public void exitSelectionMode() {
        selectionMode = false;
        selectedRepoUrls.clear();
        applySelectionState();
    }

    // --- Data Logic ---

    @Override
    public void onResume() {
        super.onResume();
        gitHubManager.addListener(this);
        refreshUi();
    }

    @Override
    public void onPause() {
        super.onPause();
        gitHubManager.removeListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void refreshUi() {
        if (binding == null) return;
        binding.swipeRefresh.setRefreshing(false);
        
        boolean signedIn = gitHubManager.isSignedIn();
        if (signedIn) {
            binding.headerCard.setVisibility(View.VISIBLE);
            binding.emptySignedOut.setVisibility(View.GONE);
            binding.userLogin.setText(gitHubManager.getUserLogin());
            
            if (gitHubManager.getUserAvatar() != null) {
                Glide.with(this).load(gitHubManager.getUserAvatar()).circleCrop()
                        .placeholder(R.drawable.ic_github_brand).into(binding.userAvatar);
            } else {
                binding.userAvatar.setImageResource(R.drawable.ic_github_brand);
            }

            List<GitHubManager.GitUploadRecord> records = gitHubManager.getUploadRecords();
            if (records.isEmpty()) {
                binding.syncStats.setText(R.string.git_empty_signedin);
                binding.emptyNoRecords.setVisibility(View.VISIBLE);
                binding.recyclerGitUploads.setVisibility(View.GONE);
            } else {
                binding.emptyNoRecords.setVisibility(View.GONE);
                binding.recyclerGitUploads.setVisibility(View.VISIBLE);
                
                if (adapter == null) {
                    adapter = new GitUploadsAdapter(requireContext(), this);
                    binding.recyclerGitUploads.setAdapter(adapter);
                }
                adapter.setRecords(records);
                applySelectionState();
            }
        } else {
            binding.headerCard.setVisibility(View.GONE);
            binding.emptySignedOut.setVisibility(View.VISIBLE);
            binding.emptyNoRecords.setVisibility(View.GONE);
            binding.recyclerGitUploads.setVisibility(View.GONE);
        }
    }

    private void updateStatsText(List<GitHubManager.GitUploadRecord> records) {
        if (records.isEmpty()) return;
        GitHubManager.GitUploadRecord last = records.get(0);
        CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                last.uploadedAtMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        binding.syncStats.setText(getString(R.string.git_header_stats, records.size(), relativeTime));
    }

    private void openDeleteConfirmDialog() {
        if (selectedRepoUrls.isEmpty()) return;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(R.string.git_delete_title);
        builder.setMessage(getString(R.string.git_delete_explain));

        View customView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_git_delete_confirm, null);
        MaterialCheckBox githubSwitch = customView.findViewById(R.id.also_delete_from_github_switch);
        builder.setView(customView);

        builder.setNegativeButton(R.string.git_delete_cancel, null);
        builder.setPositiveButton(getString(R.string.git_delete_confirm, selectedRepoUrls.size()), (v, which) -> {
            performDeletion(new ArrayList<>(selectedRepoUrls), githubSwitch.isChecked());
        });
        builder.show();
    }

    private void performDeletion(List<String> repoKeys, boolean fromGithub) {
        if (fromGithub) {
            for (String url : repoKeys) {
                String repoPath = url.replace("https://github.com/", "");
                String[] parts = repoPath.split("/");
                if (parts.length >= 2) {
                    gitHubManager.deleteRepository(parts[0], parts[1], (removed, code, detail) -> {});
                }
            }
        }

        gitHubManager.removeUploadRecords(repoKeys);
        Toast.makeText(requireContext(), R.string.git_delete_local_done, Toast.LENGTH_SHORT).show();
        exitSelectionMode();
        refreshUi();
    }

    @Override
    public void onAuthStateChanged(boolean signedIn) {
        if (isAdded()) {
            requireActivity().runOnUiThread(this::refreshUi);
        }
    }
}
