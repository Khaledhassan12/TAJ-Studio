package pro.sketchware.github;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.databinding.SheetGitCommitBinding;

/**
 * بوتم شيت احترافي لإرسال التحديثات (Commits) للمستودع.
 * يتميز بدعم إرفاق الصور، معاينة التغييرات، وتصميم Premium متفاعل مع الكيبورد.
 * Professional bottom sheet for pushing updates (Commits) to the repository.
 * Features image attachments, changes preview, and a Premium keyboard-aware design.
 */
public class GitCommitBottomSheet extends BottomSheetDialogFragment {

    private SheetGitCommitBinding binding;
    private GitHubManager.GitUploadRecord record;
    private final List<File> attachedFiles = new ArrayList<>();
    private static final int MAX_ATTACHMENTS = 5;

    // مشغل منتقي الصور؛ يحفظ الصور في مجلد كاش داخلي لسهولة الرفع
    // Image picker launcher; saves images to an internal cache folder for easier upload.
    private final ActivityResultLauncher<Intent> pickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            handlePickerResult(result.getData());
        }
    });

    public static GitCommitBottomSheet newInstance(GitHubManager.GitUploadRecord record) {
        GitCommitBottomSheet sheet = new GitCommitBottomSheet();
        sheet.record = record;
        return sheet;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // نضبط وضع الكيبورد لضمان تكيّف الواجهة دون تشويه العناصر
        // Set soft input mode to ensure the UI adapts without distorting elements.
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
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
        if (record == null) { dismiss(); return; }

        setupIdentity();
        setupChangesPreview();
        setupSuggestions();
        setupAttachments();

        binding.checkReadme.setOnCheckedChangeListener((v, checked) -> {
            binding.readmeInputLayout.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (checked) {
                binding.commitScrollContent.post(() -> 
                        binding.commitScrollContent.smoothScrollTo(0, binding.readmeInputLayout.getBottom()));
            }
        });

        binding.btnCommitPush.setOnClickListener(v -> performCommit());
        
        // حركة دخول ناعمة للهيدر لتعزيز شعور الـ Premium
        // Smooth entry animation for the header to enhance the Premium feel.
        binding.sheetHeader.setAlpha(0);
        binding.sheetHeader.setTranslationY(20);
        binding.sheetHeader.animate().alpha(1).translationY(0).setDuration(400).start();
    }

    private void setupIdentity() {
        String repoName = record.repoHtmlUrl.substring(record.repoHtmlUrl.lastIndexOf("/") + 1);
        binding.repoDisplayName.setText(repoName);
        binding.pushTargetLabel.setText(getString(R.string.git_commit_pushing_to, record.login, "main"));
    }

    private void setupChangesPreview() {
        GitHubManager manager = GitHubManager.getInstance(requireContext());
        File root = new File(a.a.a.wq.d(record.projectId));
        List<GitHubManager.UploadFile> files = manager.collectUploadFiles(root);
        
        int java = 0, res = 0, assets = 0, other = 0;
        for (GitHubManager.UploadFile f : files) {
            String p = f.relativePath;
            if (p.contains("/src/main/java/")) java++;
            else if (p.contains("/src/main/res/")) res++;
            else if (p.contains("/src/main/assets/")) assets++;
            else other++;
        }
        
        binding.changesCountBadge.setText(getString(R.string.git_commit_changes_files, files.size()));
        binding.changesSummaryText.setText(getString(R.string.git_commit_changes_summary, java, res, assets, other));
        
        // ملاحظة: حالياً نعرض إحصائيات عامة؛ مقارنة diff الدقيقة هي خطوة مستقبلية
        // Note: Currently displaying general stats; precise diff comparison is a future step.
        binding.noChangesView.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setupSuggestions() {
        View.OnClickListener l = v -> {
            if (v instanceof Chip) binding.commitMessageField.setText(((Chip) v).getText());
        };
        binding.chipUpdate.setOnClickListener(l);
        binding.chipFix.setOnClickListener(l);
        binding.chipAdd.setOnClickListener(l);
        binding.chipRefactor.setOnClickListener(l);
    }

    private void setupAttachments() {
        binding.btnAddAttachment.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            pickerLauncher.launch(intent);
        });
        updateAttachmentsRow();
    }

    private void handlePickerResult(Intent data) {
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                uris.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }

        for (Uri uri : uris) {
            if (attachedFiles.size() >= MAX_ATTACHMENTS) break;
            File file = saveUriToCache(uri);
            if (file != null) {
                if (file.length() > 5 * 1024 * 1024) {
                    Toast.makeText(requireContext(), getString(R.string.git_commit_attachments_too_big, file.getName()), Toast.LENGTH_SHORT).show();
                    continue;
                }
                attachedFiles.add(file);
            }
        }
        updateAttachmentsRow();
    }

    private File saveUriToCache(Uri uri) {
        try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
            String name = "commit_img_" + System.currentTimeMillis() + ".png";
            File file = new File(requireContext().getCacheDir(), name);
            try (FileOutputStream os = new FileOutputStream(file)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
            }
            return file;
        } catch (Exception e) { return null; }
    }

    private void updateAttachmentsRow() {
        binding.attachmentsRow.removeAllViews();
        for (int i = 0; i < attachedFiles.size(); i++) {
            File f = attachedFiles.get(i);
            View view = LayoutInflater.from(requireContext()).inflate(R.layout.item_attachment_thumbnail, binding.attachmentsRow, false);
            ImageView img = view.findViewById(R.id.thumb_image);
            View del = view.findViewById(R.id.btn_remove);
            
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            img.setImageBitmap(b);
            
            final int index = i;
            del.setOnClickListener(v -> {
                attachedFiles.remove(index);
                updateAttachmentsRow();
            });
            binding.attachmentsRow.addView(view);
        }
        binding.attachmentsRow.addView(binding.btnAddAttachment);
        binding.attachmentsCounter.setText(getString(R.string.git_commit_attachments_count, attachedFiles.size(), MAX_ATTACHMENTS));
        binding.btnAddAttachment.setVisibility(attachedFiles.size() >= MAX_ATTACHMENTS ? View.GONE : View.VISIBLE);
    }

    private void performCommit() {
        String msg = binding.commitMessageField.getText() != null ? binding.commitMessageField.getText().toString().trim() : "";
        if (msg.isEmpty()) {
            binding.commitMessageLayout.setError(getString(R.string.git_commit_error_empty));
            return;
        }
        binding.commitMessageLayout.setError(null);

        setLoading(true);
        String readme = binding.checkReadme.isChecked() ? 
                (binding.readmeContentField.getText() != null ? binding.readmeContentField.getText().toString() : "") : null;

        GitHubManager.getInstance(requireContext()).createCommit(record, msg, readme, attachedFiles, 
                new GitHubManager.CommitCallback() {
            @Override public void onSuccess() {
                if (isAdded()) {
                    Toast.makeText(requireContext(), R.string.git_commit_success, Toast.LENGTH_SHORT).show();
                    dismiss();
                }
            }
            @Override public void onError(String err) {
                if (isAdded()) {
                    setLoading(false);
                    Toast.makeText(requireContext(), "Error: " + err, Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onProgress(String stage) {
                if (isAdded()) requireActivity().runOnUiThread(() -> binding.btnCommitPush.setText(stage));
            }
        });
    }

    private void setLoading(boolean l) {
        binding.btnCommitPush.setEnabled(!l);
        binding.progressLoader.setVisibility(l ? View.VISIBLE : View.GONE);
        binding.btnCommitPush.setText(l ? R.string.git_commit_committing : R.string.git_commit_button);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
