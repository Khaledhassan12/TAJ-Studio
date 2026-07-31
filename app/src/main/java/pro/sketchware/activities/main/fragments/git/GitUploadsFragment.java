package pro.sketchware.activities.main.fragments.git;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

import java.util.List;

import pro.sketchware.R;
import pro.sketchware.databinding.FragmentGitUploadsBinding;
import pro.sketchware.github.GitHubManager;
import pro.sketchware.github.GitHubSignInSheet;

/**
 * الشاشة الرئيسية لتاب "Git"؛ تعرض سجل الرفعات وحالة الحساب.
 * نراقب حالة تسجيل الدخول لتحديث الواجهة ديناميكياً.
 * Main screen for the "Git" tab; shows upload history and account status.
 * We monitor the auth state to update the UI dynamically.
 */
public class GitUploadsFragment extends Fragment implements GitHubManager.AuthStateListener {

    private FragmentGitUploadsBinding binding;
    private GitHubManager gitHubManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gitHubManager = GitHubManager.getInstance(requireContext());
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
            // نفتح ورقة تسجيل الدخول المبدعة الموجودة مسبقاً
            // Open the existing creative sign-in sheet.
            GitHubSignInSheet.newInstance("GitHub")
                    .show(getParentFragmentManager(), "GitHubSignIn");
        });

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

    /**
     * نقوم هنا بتحديث كافة عناصر الواجهة بناءً على حالة المستخدم الحالية (مسجل أم لا) وقائمة السجلات.
     * Updates all UI elements based on the current user state (signed in or not) and the record list.
     */
    private void refreshUi() {
        if (binding == null) return;
        binding.swipeRefresh.setRefreshing(false);

        boolean signedIn = gitHubManager.isSignedIn();
        
        if (signedIn) {
            binding.headerCard.setVisibility(View.VISIBLE);
            binding.emptySignedOut.setVisibility(View.GONE);
            
            binding.userLogin.setText(gitHubManager.getUserLogin());
            
            // تحميل صورة البروفايل الكبيرة في الهيدر
            // Load the large profile avatar in the header.
            if (gitHubManager.getUserAvatar() != null) {
                Glide.with(this)
                        .load(gitHubManager.getUserAvatar())
                        .circleCrop()
                        .placeholder(R.drawable.ic_github_brand)
                        .into(binding.userAvatar);
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
                
                // تحديث نص الإحصائيات في الهيدر
                // Update stats text in the header.
                GitHubManager.GitUploadRecord last = records.get(0);
                CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                        last.uploadedAtMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
                binding.syncStats.setText(getString(R.string.git_header_stats, records.size(), relativeTime));

                binding.recyclerGitUploads.setAdapter(new GitUploadsAdapter(requireContext(), records));
            }
        } else {
            // حالة عدم تسجيل الدخول: نخفي الهيدر ونعرض واجهة الاتصال
            // Signed out state: hide header and show connection UI.
            binding.headerCard.setVisibility(View.GONE);
            binding.emptySignedOut.setVisibility(View.VISIBLE);
            binding.emptyNoRecords.setVisibility(View.GONE);
            binding.recyclerGitUploads.setVisibility(View.GONE);
        }
    }

    @Override
    public void onAuthStateChanged(boolean signedIn) {
        // نحدث الواجهة فوراً عند تغير حالة الجلسة (مثلاً بعد تسجيل الدخول بنجاح)
        // Refresh UI immediately when session state changes (e.g., after successful login).
        if (isAdded()) {
            requireActivity().runOnUiThread(this::refreshUi);
        }
    }
}
