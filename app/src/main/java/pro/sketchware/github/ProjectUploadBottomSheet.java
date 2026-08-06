package pro.sketchware.github;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import a.a.a.wq;
import pro.sketchware.R;
import pro.sketchware.databinding.SheetProjectUploadBinding;
import pro.sketchware.github.readme.MarkdownRenderer;
import pro.sketchware.github.readme.ReadmeGenerator;

/**
 * [R5-R8 State Maintenance] Upload Studio — Centralized state management & single-source readiness.
 * (عربي) استوديو الرفع - إعادة هندسة الحالة: كتّاب مركزيون، مصدر وحيد للحقيقة، ومقاومة زوال الحالة.
 */
public class ProjectUploadBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_PROJECT_ID = "project_id";
    private static final String ARG_TITLE = "title";
    private static final String ARG_ROOT_PATH = "root_path";
    
    private static final String STATE_MESSAGE = "state_message";
    private static final String STATE_TITLE = "state_title";
    private static final String STATE_README_MODE = "state_readme_mode";
    private static final String STATE_PRIVATE = "state_private";
    private static final String STATE_REPO_NAME = "state_repo_name";
    private static final String STATE_LICENSE = "state_license";

    private static final int MAX_ATTACHMENTS = 5;
    private static final long MAX_ATTACH_BYTES = 25 * 1024 * 1024;

    private SheetProjectUploadBinding binding;
    private String projectId;
    private String initialProjectTitle;
    private String rootPath;
    
    // R5: Single Source of Truth (SSOT) Model
    private String currentMessage = "";
    private String currentTitle = "";
    
    /**
     * WHAT: readmeMode & repoPrivate - Source of truth for UI state.
     * WHY: Centralizing these prevents synchronization issues between toggles, checkboxes, and containers.
     * (عربي) مصدر الحقيقة لحالة الواجهة؛ يمنع تضارب التزامن بين المفاتيح والحاويات.
     */
    private int readmeMode = 0;            // 0=None 1=Custom 2=Auto
    private boolean repoPrivate = false;
    private boolean applyingState = false; // Reentrancy guard

    private String selectedLicense = "MIT";
    private String targetRepoFullName = null;
    private boolean useGitignore = true;
    
    private final List<File> attachedFiles = new ArrayList<>();
    private int resolvedFileCount = -1;
    private String generatedReadmeMarkdown;
    private List<File> generatedBanners = new ArrayList<>();
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // R6: Generation tokens to prevent stale updates
    private final AtomicLong scanGeneration = new AtomicLong(0);
    
    // R8: Double-tap protection
    private boolean isUploading = false;

    private int uploadMode = MODE_FIRST_PUSH;
    private static final int MODE_FIRST_PUSH = 0;
    private static final int MODE_COMMIT_PUSH = 1;
    private GitHubManager.GitUploadRecord existingRecord;

    private ActivityResultLauncher<PickVisualMediaRequest> pickMultipleMedia;

    public static ProjectUploadBottomSheet newInstance(String projectId, String title, String rootPath) {
        ProjectUploadBottomSheet fragment = new ProjectUploadBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_PROJECT_ID, projectId);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_ROOT_PATH, rootPath);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            projectId = getArguments().getString(ARG_PROJECT_ID);
            initialProjectTitle = getArguments().getString(ARG_TITLE);
            rootPath = getArguments().getString(ARG_ROOT_PATH);
            currentTitle = initialProjectTitle;
        }

        pickMultipleMedia = registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS), uris -> {
            if (uris != null && !uris.isEmpty()) handlePickedUris(uris);
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetProjectUploadBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        detectUploadMode();
        
        // R7: Restore state if available
        if (savedInstanceState != null) {
            currentMessage = savedInstanceState.getString(STATE_MESSAGE, "");
            currentTitle = savedInstanceState.getString(STATE_TITLE, initialProjectTitle);
            readmeMode = savedInstanceState.getInt(STATE_README_MODE, 0);
            repoPrivate = savedInstanceState.getBoolean(STATE_PRIVATE, false);
            targetRepoFullName = savedInstanceState.getString(STATE_REPO_NAME);
            selectedLicense = savedInstanceState.getString(STATE_LICENSE, "MIT");
        }

        setupListeners();
        setupKeyboardAwareness();
        
        // Initial Rendering pass (Monopoly Writers)
        applyInitialUI();
        refreshChangesOffThread();
        
        startEntranceAnimations();
        startPulseAnimation();
    }

    private void applyInitialUI() {
        binding.projectTitle.setText(initialProjectTitle);
        binding.editProjectTitle.setText(currentTitle);
        binding.messageInput.setText(currentMessage);
        applyReadmeMode(readmeMode);
        applyVisibility(repoPrivate);
        applyDestination();
        applyReadinessState();
        applyBarVisibility(false);
    }

    private void setupListeners() {
        binding.btnClose.setOnClickListener(v -> dismiss());
        binding.uploadButton.setOnClickListener(v -> startUploadProcess());
        
        binding.editProjectTitle.addTextChangedListener(new SimpleTextWatcher(s -> {
            currentTitle = s;
            if (targetRepoFullName == null) applyDestination();
        }));

        binding.messageInput.addTextChangedListener(new SimpleTextWatcher(s -> {
            currentMessage = s;
            binding.descriptionCounter.setText(s.length() + "/500");
            applyReadinessState();
        }));

        binding.readmeCustomInput.addTextChangedListener(new SimpleTextWatcher(s -> {
            int lines = binding.readmeCustomInput.getLineCount();
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= Math.max(1, lines); i++) sb.append(i).append("\n");
            binding.readmeLineNumbers.setText(sb.toString());
            applyReadinessState();
        }));

        binding.readmeModeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || applyingState) return;
            if (checkedId == R.id.readme_auto) showLicenseDialog();
            else applyReadmeMode(checkedId == R.id.readme_custom ? 1 : 0);
        });

        binding.btnRepoPicker.setOnClickListener(v -> {
            UserRepoPicker.show(requireContext(), currentTitle, (repoFullName, createNew) -> {
                targetRepoFullName = createNew ? null : repoFullName;
                applyDestination();
            });
        });

        binding.visibilityToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || applyingState) return;
            applyVisibility(checkedId == R.id.btn_private);
        });

        binding.checkboxAutoReadme.setOnCheckedChangeListener((bv, isChecked) -> {
            if (applyingState) return;
            applyReadmeMode(isChecked ? 2 : 0);
        });

        binding.checkboxGitignore.setOnCheckedChangeListener((bv, isChecked) -> useGitignore = isChecked);
        binding.btnPreviewReadme.setOnClickListener(v -> showReadmePreview());
        binding.addAttachment.setOnClickListener(v -> {
            if (attachedFiles.size() < MAX_ATTACHMENTS) {
                pickMultipleMedia.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE).build());
            }
        });
    }

    // --- Monopoly Writers (Single Source of Truth) ---

    private void applyReadmeMode(int mode) {
        readmeMode = mode;
        applyingState = true;
        
        binding.readmeNone.setChecked(mode == 0);
        binding.readmeCustom.setChecked(mode == 1);
        binding.readmeAuto.setChecked(mode == 2);
        
        binding.checkboxAutoReadme.setChecked(mode == 2);

        binding.readmeNoneHint.setVisibility(mode == 0 ? View.VISIBLE : View.GONE);
        binding.readmeCustomContainer.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);
        binding.readmeAutoContainer.setVisibility(mode == 2 ? View.VISIBLE : View.GONE);

        if (mode == 2 && generatedReadmeMarkdown == null) buildAutoReadmePreview();
        
        applyingState = false;
        applyReadinessState();
    }

    private void applyVisibility(boolean priv) {
        repoPrivate = priv;
        applyingState = true;
        binding.btnPublic.setChecked(!priv);
        binding.btnPrivate.setChecked(priv);
        applyingState = false;
    }

    private void applyReadinessState() {
        if (binding == null) return;
        boolean messageReady = !currentMessage.trim().isEmpty();
        boolean readmeReady = (readmeMode != 0 && (readmeMode != 1 || !binding.readmeCustomInput.getText().toString().trim().isEmpty()));
        if (readmeMode == 2 && generatedReadmeMarkdown == null) readmeReady = false;
        boolean attachmentsReady = !attachedFiles.isEmpty();

        // 1. Files Chip
        String filesText = (resolvedFileCount == -1) ? "Checking files…" : (resolvedFileCount > 0 ? "Files ✔ " + resolvedFileCount : "No local files");
        int filesColor = (resolvedFileCount > 0) ? com.google.android.material.R.attr.colorPrimaryContainer : (resolvedFileCount == -1 ? com.google.android.material.R.attr.colorSurfaceContainerHigh : com.google.android.material.R.attr.colorErrorContainer);
        updateChip(binding.chipReadyFiles, filesText, filesColor, resolvedFileCount > 0);

        // 2. Message Chip
        updateChip(binding.chipReadyMessage, "Message" + (messageReady ? " ✔" : ""), messageReady ? com.google.android.material.R.attr.colorPrimaryContainer : com.google.android.material.R.attr.colorSurfaceContainerHigh, messageReady);

        // 3. README Chip
        updateChip(binding.chipReadyReadme, "README" + (readmeReady ? " ✔" : ""), readmeReady ? com.google.android.material.R.attr.colorPrimaryContainer : com.google.android.material.R.attr.colorSurfaceContainerHigh, readmeReady);

        // 4. Attachments Chip
        updateChip(binding.chipReadyAttachments, "Attachments" + (attachmentsReady ? " ✔ " + attachedFiles.size() : ""), attachmentsReady ? com.google.android.material.R.attr.colorPrimaryContainer : com.google.android.material.R.attr.colorSurfaceContainerHigh, attachmentsReady);

        int readyCount = (resolvedFileCount > 0 ? 1 : 0) + (messageReady ? 1 : 0) + (readmeMode != 0 ? 1 : 0) + (attachmentsReady ? 1 : 0);
        binding.iconPulseRing.setAlpha(0.1f + (readyCount * 0.15f));
        applyUploadState();
    }

    private void applyUploadState() {
        if (binding == null) return;
        boolean isCommit = uploadMode == MODE_COMMIT_PUSH;
        boolean hasFiles = resolvedFileCount > 0, hasMessage = !currentMessage.trim().isEmpty(), hasAttachments = !attachedFiles.isEmpty();
        boolean hasReadme = (readmeMode == 1 && !binding.readmeCustomInput.getText().toString().trim().isEmpty()) || (readmeMode == 2 && generatedReadmeMarkdown != null);
        boolean canUpload = hasFiles || hasAttachments || hasReadme, canAct = isCommit || canUpload;

        if (isUploading) {
            binding.uploadButton.setEnabled(false);
            binding.uploadButton.setText(null);
            binding.uploadProgress.setVisibility(View.VISIBLE);
            return;
        }

        if (resolvedFileCount == -1) {
            binding.uploadButton.setEnabled(false);
            binding.uploadButton.setText(R.string.upload_button_preparing);
            binding.uploadButtonHint.setVisibility(View.GONE);
            return;
        }

        binding.uploadButton.setEnabled(canAct);
        binding.uploadButton.setAlpha(canAct ? 1.0f : 0.5f);
        binding.uploadButtonHint.setVisibility(View.VISIBLE);
        
        if (!canAct) {
            binding.uploadButton.setText(R.string.upload_button_nothing);
            binding.uploadButtonHint.setText(R.string.upload_button_needs_input);
            binding.uploadButtonHint.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_error));
        } else if (isCommit) {
            binding.uploadButton.setText(R.string.upload_commit_button);
            binding.uploadButton.setIconResource(R.drawable.ic_mtrl_history);
            if (!canUpload && !hasMessage) {
                binding.uploadButtonHint.setText(R.string.upload_empty_commit_warn);
                binding.uploadButtonHint.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_error));
            } else buildCustomizationHint(hasAttachments, readmeMode != 0, hasMessage);
        } else {
            boolean isCustom = hasMessage || hasAttachments || (readmeMode != 0);
            binding.uploadButton.setText(isCustom ? R.string.upload_button_custom : R.string.upload_button_as_is);
            binding.uploadButton.setIconResource(isCustom ? R.drawable.ic_github_brand : R.drawable.ic_stars);
            if (!isCustom) binding.uploadButtonHint.setText(R.string.upload_hint_defaults);
            else buildCustomizationHint(hasAttachments, readmeMode != 0, hasMessage);
        }
        binding.uploadProgress.setVisibility(View.GONE);
    }

    private void applyDestination() {
        GitHubManager manager = GitHubManager.getInstance(requireContext());
        String avatar = manager.getUserAvatar();
        if (avatar != null) Glide.with(this).load(avatar).circleCrop().into(binding.userAvatar);

        if (uploadMode == MODE_COMMIT_PUSH) {
            binding.textRepoName.setText(existingRecord.repoHtmlUrl.replace("https://github.com/", ""));
            binding.btnRepoPicker.setVisibility(View.GONE);
        } else {
            binding.textRepoName.setText(targetRepoFullName != null ? targetRepoFullName : "➕ Create new: " + currentTitle);
            binding.btnRepoPicker.setVisibility(View.VISIBLE);
        }
    }

    private void applyChangesState() {
        if (binding == null) return;
        binding.changesLoadingState.setVisibility(resolvedFileCount == -1 ? View.VISIBLE : View.GONE);
        binding.changesSummary.setVisibility(resolvedFileCount == -1 ? View.GONE : View.VISIBLE);
        binding.filesBadge.setText(resolvedFileCount == -1 ? "…" : getString(R.string.upload_changes_files, resolvedFileCount));
    }

    private void applyBarVisibility(boolean imeVisible) {
        View bar = binding.uploadButtonContainer;
        if (imeVisible) {
            bar.animate().translationY(bar.getHeight()).alpha(0f).setDuration(200).withEndAction(() -> bar.setVisibility(View.GONE)).start();
        } else {
            bar.setVisibility(View.VISIBLE);
            bar.animate().translationY(0f).alpha(1f).setDuration(200).start();
        }
    }

    // --- R6: Logic with Generation Tokens ---

    private void refreshChangesOffThread() {
        long currentGen = scanGeneration.incrementAndGet();
        applyChangesState();
        executor.execute(() -> {
            GitHubManager manager = GitHubManager.getInstance(requireContext());
            File root = new File(rootPath);
            int count = 0;
            String summaryText = "";
            if (root.exists()) {
                List<GitHubManager.UploadFile> files = manager.collectUploadFiles(root);
                int java = 0, res = 0, assets = 0, other = 0;
                for (GitHubManager.UploadFile f : files) {
                    String p = f.relativePath;
                    if (p.contains("/src/main/java/")) java++;
                    else if (p.contains("/src/main/res/")) res++;
                    else if (p.contains("/src/main/assets/")) assets++;
                    else other++;
                }
                count = files.size();
                summaryText = (count == 0) ? getString(R.string.upload_changes_none) : getString(R.string.upload_changes_summary, java, res, assets, other);
            } else {
                summaryText = getString(R.string.upload_changes_missing);
            }

            final int fCount = count;
            final String fSummary = summaryText;
            mainHandler.post(() -> {
                if (binding == null || currentGen != scanGeneration.get()) return;
                resolvedFileCount = fCount;
                binding.changesSummary.setText(fSummary);
                applyChangesState();
                applyReadinessState();
            });
        });
    }

    // --- R7: State Resilience ---

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_MESSAGE, currentMessage);
        outState.putString(STATE_TITLE, currentTitle);
        outState.putInt(STATE_README_MODE, readmeMode);
        outState.putBoolean(STATE_PRIVATE, repoPrivate);
        outState.putString(STATE_REPO_NAME, targetRepoFullName);
        outState.putString(STATE_LICENSE, selectedLicense);
    }

    // --- Helpers & Logic ---

    private void detectUploadMode() {
        GitHubManager manager = GitHubManager.getInstance(requireContext());
        String login = manager.getUserLogin();
        String slug = initialProjectTitle.replaceAll("[^a-zA-Z0-9._-]", "-");
        if (slug.isEmpty()) slug = "project";
        String expectedUrl = "https://github.com/" + login + "/" + slug;
        existingRecord = manager.findUploadRecord(projectId, expectedUrl);
        uploadMode = (existingRecord != null) ? MODE_COMMIT_PUSH : MODE_FIRST_PUSH;
    }

    private void setupKeyboardAwareness() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            applyBarVisibility(insets.isVisible(WindowInsetsCompat.Type.ime()));
            return insets;
        });
    }

    private void buildAutoReadmePreview() {
        binding.readmeAutoStatus.setText("Generating README…");
        executor.execute(() -> {
            GitHubManager manager = GitHubManager.getInstance(requireContext());
            ReadmeGenerator.ReadmeResult result = ReadmeGenerator.generate(requireContext(), initialProjectTitle, projectId, rootPath, manager.getUserLogin(), manager.getUserAvatar(), selectedLicense, attachedFiles);
            mainHandler.post(() -> {
                if (binding != null) {
                    generatedReadmeMarkdown = result.markdown;
                    generatedBanners = result.banners;
                    binding.readmeAutoStatus.setText("README ready ✔ (" + generatedReadmeMarkdown.split("\n").length + " lines)");
                    binding.autoReadmePreview.setText(generatedReadmeMarkdown.substring(0, Math.min(200, generatedReadmeMarkdown.length())) + "...");
                    applyReadinessState();
                }
            });
        });
    }

    private void updateAttachmentStrip() {
        binding.attachmentsStrip.removeAllViews();
        for (File file : attachedFiles) {
            View view = LayoutInflater.from(requireContext()).inflate(R.layout.item_upload_attachment, binding.attachmentsStrip, false);
            ImageView thumb = view.findViewById(R.id.thumbnail);
            View remove = view.findViewById(R.id.remove_btn), videoIcon = view.findViewById(R.id.video_icon);
            if (file.getName().toLowerCase().endsWith(".mp4") || file.getName().toLowerCase().endsWith(".mov")) {
                videoIcon.setVisibility(View.VISIBLE); thumb.setBackgroundColor(Color.BLACK);
            } else Glide.with(this).load(file).centerCrop().into(thumb);
            remove.setOnClickListener(v -> { attachedFiles.remove(file); file.delete(); updateAttachmentStrip(); applyReadinessState(); });
            binding.attachmentsStrip.addView(view);
        }
        if (attachedFiles.size() < MAX_ATTACHMENTS) binding.attachmentsStrip.addView(binding.addAttachment);
        binding.attachmentsCount.setText(getString(R.string.upload_attachments_count, attachedFiles.size(), MAX_ATTACHMENTS));
    }

    private void handlePickedUris(List<Uri> uris) {
        int remaining = MAX_ATTACHMENTS - attachedFiles.size();
        List<Uri> toProcess = uris.size() > remaining ? uris.subList(0, remaining) : uris;
        for (Uri uri : toProcess) {
            try {
                String name = getFileName(uri);
                if (getFileSize(uri) > MAX_ATTACH_BYTES) continue;
                File cacheFile = new File(requireContext().getCacheDir(), "attach_" + System.currentTimeMillis() + "_" + name);
                try (InputStream is = requireContext().getContentResolver().openInputStream(uri); FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    if (is != null) { byte[] buf = new byte[8192]; int len; while ((len = is.read(buf)) != -1) fos.write(buf, 0, len); }
                }
                attachedFiles.add(cacheFile);
            } catch (Exception ignored) {}
        }
        updateAttachmentStrip();
        applyReadinessState();
    }

    private void startUploadProcess() {
        if (isUploading) return;
        isUploading = true;
        applyUploadState();

        String message = currentMessage.isEmpty() ? (uploadMode == MODE_COMMIT_PUSH ? "Update " + currentTitle : "Upload project: " + currentTitle) : currentMessage;
        String readme = (readmeMode == 1) ? binding.readmeCustomInput.getText().toString() : (readmeMode == 2 ? generatedReadmeMarkdown : null);
        File iconFile = new File(wq.e() + File.separator + projectId, "icon.png");
        String iconPath = iconFile.exists() ? iconFile.getAbsolutePath() : null;

        List<String> allPaths = new ArrayList<>();
        for (File f : attachedFiles) allPaths.add(f.getAbsolutePath());
        for (File f : generatedBanners) allPaths.add(f.getAbsolutePath());

        Intent intent;
        if (uploadMode == MODE_COMMIT_PUSH) {
            intent = new Intent(requireContext(), GitCommitService.class);
            intent.setAction(GitCommitService.ACTION_COMMIT);
            intent.putExtra(GitCommitService.EXTRA_RECORD_JSON, new com.google.gson.Gson().toJson(existingRecord));
            intent.putExtra(GitCommitService.EXTRA_MESSAGE, message);
            intent.putExtra(GitCommitService.EXTRA_README, readme);
            intent.putExtra(GitCommitService.EXTRA_ATTACHED_PATHS, allPaths.toArray(new String[0]));
        } else {
            intent = new Intent(requireContext(), GitHubUploadService.class);
            intent.setAction(GitHubUploadService.ACTION_START_UPLOAD);
            intent.putExtra(GitHubUploadService.EXTRA_PROJECT_TITLE, currentTitle);
            intent.putExtra(GitHubUploadService.EXTRA_PROJECT_ROOT, rootPath);
            intent.putExtra(GitHubUploadService.EXTRA_COMMIT_MESSAGE, message);
            intent.putExtra(GitHubUploadService.EXTRA_README_CONTENT, readme);
            intent.putExtra(GitHubUploadService.EXTRA_README_ICON_PATH, iconPath);
            intent.putExtra(GitHubUploadService.EXTRA_ATTACHED_PATHS, allPaths.toArray(new String[0]));
            intent.putExtra(GitHubUploadService.EXTRA_REPO_PRIVATE, repoPrivate);
            intent.putExtra(GitHubUploadService.EXTRA_TARGET_REPO_FULL_NAME, targetRepoFullName);
            intent.putExtra(GitHubUploadService.EXTRA_USE_GITIGNORE, useGitignore);
        }
        ContextCompat.startForegroundService(requireContext(), intent);
        dismissAllowingStateLoss();
    }

    private void showLicenseDialog() {
        String[] licenses = {"MIT", "Apache-2.0", "GPL-3.0", "BSD-3-Clause", "No License"};
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Choose a license").setSingleChoiceItems(licenses, 0, (d, w) -> {
            selectedLicense = licenses[w];
            applyReadmeMode(2);
            d.dismiss();
        }).setNegativeButton("Cancel", null).show();
    }

    private void showReadmePreview() {
        if (generatedReadmeMarkdown == null) return;
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);
        MarkdownRenderer.render(requireContext(), layout, generatedReadmeMarkdown, getLocalFilesForPreview());
        NestedScrollView scroll = new NestedScrollView(requireContext());
        scroll.addView(layout);
        new MaterialAlertDialogBuilder(requireContext()).setTitle(selectedLicense + " Readme").setView(scroll)
                .setNeutralButton("Copy", (d, w) -> {
                    android.content.ClipboardManager cb = (android.content.ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cb != null) cb.setPrimaryClip(android.content.ClipData.newPlainText("README", generatedReadmeMarkdown));
                    Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
                }).setPositiveButton("Close", null).show();
    }

    private List<File> getLocalFilesForPreview() {
        List<File> list = new ArrayList<>(attachedFiles);
        list.addAll(generatedBanners);
        return list;
    }

    private void updateChip(Chip chip, String text, int colorAttr, boolean ready) {
        chip.setText(text);
        int color = com.google.android.material.color.MaterialColors.getColor(requireContext(), colorAttr, 0);
        int textColorAttr = ready ? com.google.android.material.R.attr.colorOnPrimaryContainer : com.google.android.material.R.attr.colorOnSurfaceVariant;
        int textColor = com.google.android.material.color.MaterialColors.getColor(requireContext(), textColorAttr, 0);
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(color));
        chip.setTextColor(textColor);
        chip.setChipStrokeWidth(ready ? 0 : getResources().getDisplayMetrics().density);
        if (ready && (chip.getTag() == null || !(boolean)chip.getTag())) {
            chip.setScaleX(0.8f); chip.setScaleY(0.8f);
            chip.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(new OvershootInterpolator()).start();
            chip.setTag(true);
        } else if (!ready) chip.setTag(false);
    }

    private void startEntranceAnimations() {
        binding.pillHandleContainer.setTranslationY(-20f);
        binding.pillHandleContainer.animate().translationY(0f).setDuration(600).setInterpolator(new OvershootInterpolator(2f)).start();
        View[] cards = { binding.cardRepository, binding.cardProject, binding.cardReadme, binding.contextInfoCard };
        for (int i = 0; i < cards.length; i++) {
            if (cards[i] == null) continue;
            cards[i].setAlpha(0f); cards[i].setTranslationY(60f);
            cards[i].animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(150 + (i * 40)).setInterpolator(new OvershootInterpolator(0.8f)).start();
        }
    }

    private void startPulseAnimation() {
        android.animation.ObjectAnimator scaleX = android.animation.ObjectAnimator.ofFloat(binding.iconPulseRing, "scaleX", 1f, 1.4f);
        android.animation.ObjectAnimator scaleY = android.animation.ObjectAnimator.ofFloat(binding.iconPulseRing, "scaleY", 1f, 1.4f);
        android.animation.ValueAnimator alphaAnim = android.animation.ValueAnimator.ofFloat(1f, 0f);
        alphaAnim.addUpdateListener(a -> {
            if (binding == null) return;
            float val = (float) a.getAnimatedValue();
            boolean messageReady = !currentMessage.trim().isEmpty(), readmeReady = (readmeMode != 0);
            int readyCount = (resolvedFileCount > 0 ? 1 : 0) + (messageReady ? 1 : 0) + (readmeReady ? 1 : 0) + (attachedFiles.isEmpty() ? 0 : 1);
            binding.iconPulseRing.setAlpha(val * (0.2f + (readyCount * 0.15f)));
        });
        scaleX.setRepeatCount(-1); scaleY.setRepeatCount(-1); alphaAnim.setRepeatCount(-1);
        android.animation.AnimatorSet set = new android.animation.AnimatorSet();
        set.playTogether(scaleX, scaleY, alphaAnim); set.setDuration(2000); set.start();
    }

    private String getFileName(Uri uri) {
        String res = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor c = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (i != -1) res = c.getString(i);
                }
            }
        }
        if (res == null) {
            res = uri.getPath();
            int cut = res.lastIndexOf('/');
            if (cut != -1) res = res.substring(cut + 1);
        }
        return res;
    }

    private long getFileSize(Uri uri) {
        try (android.database.Cursor c = requireContext().getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.SIZE);
                if (i != -1) return c.getLong(i);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private void buildCustomizationHint(boolean hasAttachments, boolean hasReadme, boolean hasMessage) {
        List<String> details = new ArrayList<>();
        if (hasAttachments) details.add(attachedFiles.size() + " attachments");
        if (hasReadme) details.add(readmeMode == 2 ? "Auto README" : "Custom README");
        if (hasMessage) details.add("Custom message");
        binding.uploadButtonHint.setText(getString(R.string.upload_hint_summary, String.join(" · ", details)));
        binding.uploadButtonHint.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_on_surface));
    }

    @Override
    public void onStart() {
        super.onStart();
        View sheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            com.google.android.material.bottomsheet.BottomSheetBehavior<View> b = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet);
            b.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            b.setSkipCollapsed(true);
            b.setFitToContents(false);
            sheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private interface TextConsumer {
        void accept(String s);
    }

    private static class SimpleTextWatcher implements android.text.TextWatcher {
        private final TextConsumer consumer;
        SimpleTextWatcher(TextConsumer consumer) { this.consumer = consumer; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { consumer.accept(s.toString()); }
        @Override public void afterTextChanged(android.text.Editable s) {}
    }
}
