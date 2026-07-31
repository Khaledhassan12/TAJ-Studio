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
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.databinding.FragmentGitUploadsBinding;
import pro.sketchware.github.GitHubManager;
import pro.sketchware.github.GitHubSignInSheet;

/**
 * الشاشة الرئيسية لتاب "Git"؛ تعرض سجل الرفعات وحالة الحساب.
 * تدعم الآن وضع الحذف المتعدد للأرقام القياسية بلمسة بشرية متقنة.
 * Main screen for the "Git" tab; shows upload history and account status.
 * Now supports multi-select delete mode with a refined human touch.
 */
public class GitUploadsFragment extends Fragment implements GitHubManager.AuthStateListener {

    private FragmentGitUploadsBinding binding;
    private GitHubManager gitHubManager;
    private GitUploadsAdapter adapter;
    private OnBackPressedCallback backCallback;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gitHubManager = GitHubManager.getInstance(requireContext());
        
        // معالج زر الرجوع للخروج من وضع الحذف
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (adapter != null) adapter.exitSelectionMode();
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

        // الخروج من وضع التحديد عند نقر منطقة الاسم/الصورة في الهيدر
        binding.headerCard.setOnClickListener(v -> {
            if (adapter != null && adapter.isSelectionMode()) {
                adapter.exitSelectionMode();
            }
        });

        binding.masterDeleteButton.setOnClickListener(v -> openDeleteConfirmDialog());

        refreshUi();
    }

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
                
                updateStatsText(records);

                adapter = new GitUploadsAdapter(requireContext(), records, (enabled, count) -> {
                    backCallback.setEnabled(enabled);
                    binding.masterDeleteButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
                    if (enabled) {
                        // أنيميشن قفزة للسلّة الكبيرة
                        binding.masterDeleteButton.setScaleX(0);
                        binding.masterDeleteButton.setScaleY(0);
                        binding.masterDeleteButton.animate().scaleX(1).scaleY(1).setDuration(250).start();
                        
                        binding.syncStats.setText(getString(R.string.git_selection_count, count));
                        binding.masterDeleteButton.setAlpha(count > 0 ? 1f : 0.4f);
                    } else {
                        updateStatsText(records);
                    }
                });
                binding.recyclerGitUploads.setAdapter(adapter);
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
        List<GitHubManager.GitUploadRecord> selected = adapter.getSelectedRecords();
        if (selected.isEmpty()) return;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(R.string.git_delete_title);
        builder.setMessage(getString(R.string.git_delete_explain));

        View customView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_git_delete_confirm, null);
        MaterialCheckBox githubSwitch = customView.findViewById(R.id.also_delete_from_github_switch);
        builder.setView(customView);

        builder.setNegativeButton(R.string.git_delete_cancel, null);
        builder.setPositiveButton(getString(R.string.git_delete_confirm, selected.size()), (v, which) -> {
            performDeletion(selected, githubSwitch.isChecked());
        });
        builder.show();
    }

    private void performDeletion(List<GitHubManager.GitUploadRecord> selected, boolean fromGithub) {
        List<String> repoKeys = new ArrayList<>();
        for (GitHubManager.GitUploadRecord r : selected) repoKeys.add(r.repoHtmlUrl);

        if (fromGithub) {
            // حذف من GitHub (يتطلب صلاحية delete_repo)
            for (GitHubManager.GitUploadRecord r : selected) {
                String repoPath = r.repoHtmlUrl.replace("https://github.com/", "");
                String[] parts = repoPath.split("/");
                if (parts.length >= 2) {
                    gitHubManager.deleteRepository(parts[0], parts[1], (removed, code, detail) -> {
                        // لا ننتظر هنا، الحذف المحلي سيتم فوراً
                    });
                }
            }
        }

        // الحذف المحلي مضمون وفوري
        gitHubManager.removeUploadRecords(repoKeys);
        Toast.makeText(requireContext(), R.string.git_delete_local_done, Toast.LENGTH_SHORT).show();
        
        if (adapter != null) adapter.exitSelectionMode();
        refreshUi();
    }

    @Override
    public void onAuthStateChanged(boolean signedIn) {
        if (isAdded()) {
            requireActivity().runOnUiThread(this::refreshUi);
        }
    }
}
