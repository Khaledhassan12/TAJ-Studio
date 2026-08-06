package pro.sketchware.github;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;
import pro.sketchware.R;
import pro.sketchware.databinding.SheetGitCommitBinding;

/**
 * [R5-R8 State Maintenance] GitCommitBottomSheet - Centralized state management.
 * (عربي) بوتم شيت إرسال التحديثات - إعادة هندسة الحالة: رندر مركزي، وحماية من تداخل جلب التغييرات.
 */
public class GitCommitBottomSheet extends BottomSheetDialogFragment {

    private SheetGitCommitBinding binding;
    private GitHubManager.GitUploadRecord record;
    private final List<File> attachedFiles = new ArrayList<>();
    private static final int MAX_ATTACHMENTS = 5;

    // R5: State Model
    private enum CommitUiState { LOADING_CHANGES, READY, COMMITTING, SUCCESS, FAILED }
    private CommitUiState currentState = CommitUiState.LOADING_CHANGES;
    
    // R6: Generation token for async changes scanning
    private final AtomicLong scanGeneration = new AtomicLong(0);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        
        loadChangesAsync();

        binding.sheetHeader.setAlpha(0);
        binding.sheetHeader.setTranslationY(20);
        binding.sheetHeader.animate().alpha(1).translationY(0).setDuration(400).start();
    }

    // --- Monopoly Writers (Single Source of Truth) ---

    /**
     * WHAT: applyCommitState - The ONLY writer for commit UI.
     * WHY: [R5] Synchronizes loading state, buttons, and progress.
     * (عربي) الكاتب الوحيد لحالة الـ Commit - يضمن ثبات الواجهة ومنع التداخل أثناء الرفع.
     */
    private void applyCommitState() {
        if (binding == null) return;

        boolean loading = currentState == CommitUiState.LOADING_CHANGES;
        boolean busy = currentState == CommitUiState.COMMITTING;
        
        binding.changesLoadingState.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnCommitPush.setEnabled(!loading && !busy);
        binding.btnCommitPush.setAlpha(busy ? 0.6f : 1.0f);
        binding.btnCommitPush.setText(busy ? "Pushing…" : getString(R.string.git_commit_button));
        
        if (currentState == CommitUiState.READY) {
            binding.changesCountBadge.setVisibility(View.VISIBLE);
        }
    }

    private void loadChangesAsync() {
        currentState = CommitUiState.LOADING_CHANGES;
        applyCommitState();
        
        long currentGen = scanGeneration.incrementAndGet();
        executor.execute(() -> {
            GitHubManager manager = GitHubManager.getInstance(requireContext());
            File root = new File(wq.d(record.projectId));
            List<GitHubManager.UploadFile> files = manager.collectUploadFiles(root);
            
            int java = 0, res = 0, assets = 0, other = 0;
            for (GitHubManager.UploadFile f : files) {
                String p = f.relativePath;
                if (p.contains("/src/main/java/")) java++;
                else if (p.contains("/src/main/res/")) res++;
                else if (p.contains("/src/main/assets/")) assets++;
                else other++;
            }
            
            final int fSize = files.size();
            final int fJava = java, fRes = res, fAssets = assets, fOther = other;
            
            mainHandler.post(() -> {
                if (binding == null || !isAdded() || currentGen != scanGeneration.get()) return;
                
                binding.changesCountBadge.setText(getString(R.string.git_commit_changes_files, fSize));
                binding.changesSummaryText.setText(getString(R.string.git_commit_changes_summary, fJava, fRes, fAssets, fOther));
                binding.noChangesView.setVisibility(fSize == 0 ? View.VISIBLE : View.GONE);
                
                currentState = CommitUiState.READY;
                applyCommitState();
            });
        });
    }

    // --- Identity & Assets ---

    private void setupIdentity() {
        String repoName = record.repoHtmlUrl.substring(record.repoHtmlUrl.lastIndexOf("/") + 1);
        binding.repoDisplayName.setText(repoName);
        binding.pushTargetLabel.setText(getString(R.string.git_commit_pushing_to, record.login, "main"));

        boolean iconSet = false;
        if (record.projectId != null) {
            HashMap<String, Object> projectMap = lC.b(record.projectId);
            if (projectMap != null && yB.a(projectMap, "custom_icon")) {
                File iconFile = new File(wq.e() + File.separator + record.projectId, "icon.png");
                if (iconFile.exists()) {
                    String providerPath = requireContext().getPackageName() + ".provider";
                    Uri uri = FileProvider.getUriForFile(requireContext(), providerPath, iconFile);
                    binding.projectIconView.setImageURI(uri);
                    iconSet = true;
                }
            }
        }
        if (!iconSet) binding.projectIconView.setImageResource(R.drawable.ic_github_brand);
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
            for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) uris.add(data.getData());

        for (Uri uri : uris) {
            if (attachedFiles.size() >= MAX_ATTACHMENTS) break;
            File file = saveUriToCache(uri);
            if (file != null) {
                if (file.length() > 5 * 1024 * 1024) continue;
                attachedFiles.add(file);
            }
        }
        updateAttachmentsRow();
    }

    private File saveUriToCache(Uri uri) {
        File dir = new File(requireContext().getCacheDir(), "commit_attachments");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "img_" + System.currentTimeMillis() + "_" + uri.getLastPathSegment() + ".png");
        try (InputStream is = requireContext().getContentResolver().openInputStream(uri); FileOutputStream os = new FileOutputStream(file)) {
            byte[] buf = new byte[8192]; int len;
            while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
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
            
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 4;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            img.setImageBitmap(b);
            
            final int index = i;
            del.setOnClickListener(v -> {
                attachedFiles.get(index).delete();
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
        if (currentState == CommitUiState.COMMITTING) return;
        
        String msg = binding.commitMessageField.getText() != null ? binding.commitMessageField.getText().toString().trim() : "";
        if (msg.isEmpty()) {
            binding.commitMessageLayout.setError(getString(R.string.git_commit_error_empty));
            return;
        }
        binding.commitMessageLayout.setError(null);

        currentState = CommitUiState.COMMITTING;
        applyCommitState();

        Intent intent = new Intent(requireContext(), GitCommitService.class);
        intent.setAction(GitCommitService.ACTION_COMMIT);
        intent.putExtra(GitCommitService.EXTRA_RECORD_JSON, new com.google.gson.Gson().toJson(record));
        intent.putExtra(GitCommitService.EXTRA_MESSAGE, msg);
        if (binding.checkReadme.isChecked()) {
            intent.putExtra(GitCommitService.EXTRA_README, 
                    binding.readmeContentField.getText() != null ? binding.readmeContentField.getText().toString() : "");
        }
        
        String[] paths = new String[attachedFiles.size()];
        for (int i = 0; i < attachedFiles.size(); i++) paths[i] = attachedFiles.get(i).getAbsolutePath();
        intent.putExtra(GitCommitService.EXTRA_ATTACHED_PATHS, paths);

        ContextCompat.startForegroundService(requireContext(), intent);
        Toast.makeText(requireContext(), R.string.git_commit_preparing, Toast.LENGTH_SHORT).show();
        dismissAllowingStateLoss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdownNow();
        binding = null;
    }
}
