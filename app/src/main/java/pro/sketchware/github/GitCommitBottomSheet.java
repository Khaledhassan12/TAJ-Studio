package pro.sketchware.github;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import pro.sketchware.R;
import pro.sketchware.databinding.SheetGitCommitBinding;

/**
 * بوتم شيت احترافي لإنشاء "Commit" جديد للمشروع.
 * يتيح للمستخدم مراجعة الملفات، كتابة رسالة، وإضافة ملف README مخصص.
 * Premium Bottom Sheet for creating a new Git commit.
 * Allows users to review files, write a message, and add a custom README.
 */
public class GitCommitBottomSheet extends BottomSheetDialogFragment {

    private SheetGitCommitBinding binding;
    private GitHubManager.GitUploadRecord record;

    public static GitCommitBottomSheet newInstance(GitHubManager.GitUploadRecord record) {
        GitCommitBottomSheet sheet = new GitCommitBottomSheet();
        sheet.record = record;
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetGitCommitBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (record == null) {
            dismiss();
            return;
        }

        binding.sheetSubtitle.setText(getString(R.string.git_commit_subtitle, 
                record.login + "/" + record.projectTitle));

        // منطق إظهار/إخفاء حقل الـ README المخصص
        // Show/hide logic for custom README field
        binding.checkReadme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.readmeInputLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // ربط اقتراحات الرسائل الذكية بالحقل النصي
        // Link smart message suggestions to the input field
        setupSuggestionChips();

        binding.btnCommitPush.setOnClickListener(v -> performCommit());
    }

    private void setupSuggestionChips() {
        View.OnClickListener chipListener = v -> {
            if (v instanceof Chip) {
                binding.commitMessageField.setText(((Chip) v).getText());
            }
        };
        binding.chipUpdate.setOnClickListener(chipListener);
        binding.chipFix.setOnClickListener(chipListener);
        binding.chipAdd.setOnClickListener(chipListener);
        binding.chipRefactor.setOnClickListener(chipListener);
    }

    private void performCommit() {
        String message = binding.commitMessageField.getText() != null ? 
                binding.commitMessageField.getText().toString().trim() : "";
        
        if (message.isEmpty()) {
            binding.commitMessageLayout.setError(getString(R.string.git_commit_error_empty_message));
            return;
        }
        binding.commitMessageLayout.setError(null);

        // تفعيل حالة التحميل وتعطيل التفاعل
        // Enable loading state and disable interaction
        setLoading(true);

        String readme = binding.checkReadme.isChecked() && binding.readmeContentField.getText() != null ? 
                binding.readmeContentField.getText().toString() : null;

        GitHubManager.getInstance(requireContext()).createCommit(record, message, readme, 
                new GitHubManager.CommitCallback() {
            @Override
            public void onSuccess() {
                if (isAdded()) {
                    Toast.makeText(requireContext(), R.string.git_commit_success, Toast.LENGTH_SHORT).show();
                    dismiss();
                }
            }

            @Override
            public void onError(String error) {
                if (isAdded()) {
                    setLoading(false);
                    Toast.makeText(requireContext(), "Error: " + error, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void setLoading(boolean loading) {
        binding.btnCommitPush.setEnabled(!loading);
        binding.btnCommitPush.setText(loading ? R.string.git_commit_committing : R.string.git_commit_button);
        binding.progressLoader.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.commitMessageField.setEnabled(!loading);
        binding.checkReadme.setEnabled(!loading);
        binding.readmeContentField.setEnabled(!loading);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
