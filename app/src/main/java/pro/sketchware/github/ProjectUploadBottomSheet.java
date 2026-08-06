package pro.sketchware.github;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.wq;
import pro.sketchware.R;
import pro.sketchware.databinding.SheetProjectUploadBinding;
import pro.sketchware.github.readme.MarkdownRenderer;
import pro.sketchware.github.readme.ReadmeGenerator;

/**
 * TODO_AGENT.md: Upload Studio — compact merge pass, hint-overlap fix & semantic icons — 2026-08-06
 * 
 * بوتم شيت استوديو الرفع (Upload Studio) — يوفر واجهة متقدمة لرفع المشاريع إلى GitHub
 * مع دعم الإرفاقات (صور/فيديو) وتوليد احترافي لملف README.
 * [AR] تم تقليص عدد البطاقات من 7 إلى 5 مع دمج الميزات المتشابهة وحل مشكلة تداخل تلميحات النص.
 * [EN] Card count reduced from 7 to 5 with merged features and resolved hint-overlap issues.
 */
public class ProjectUploadBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_PROJECT_ID = "project_id";
    private static final String ARG_TITLE = "title";
    private static final String ARG_ROOT_PATH = "root_path";
    
    private static final int MAX_ATTACHMENTS = 5;
    private static final long MAX_ATTACH_BYTES = 25 * 1024 * 1024;

    private SheetProjectUploadBinding binding;
    private String projectId;
    private String projectTitle;
    private String rootPath;
    
    private List<File> attachedFiles = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final int MODE_FIRST_PUSH = 0;
    private static final int MODE_COMMIT_PUSH = 1;
    private int uploadMode = MODE_FIRST_PUSH;
    private GitHubManager.GitUploadRecord existingRecord;

    private int resolvedFileCount = -1;

    private enum ReadmeMode { NONE, CUSTOM, AUTO }
    private ReadmeMode currentReadmeMode = ReadmeMode.NONE;
    private String generatedReadmeMarkdown;
    private List<File> generatedBanners = new ArrayList<>();
    private String selectedLicense = "MIT";

    private boolean isPrivate = false;
    private String targetRepoFullName = null;
    private boolean useGitignore = true;

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
            projectTitle = getArguments().getString(ARG_TITLE);
            rootPath = getArguments().getString(ARG_ROOT_PATH);
        }

        pickMultipleMedia = registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS), uris -> {
            if (uris != null && !uris.isEmpty()) {
                handlePickedUris(uris);
            }
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
        setupHeader();
        applyModeLanguage();
        refreshChangesOffThread();
        setupSuggestions();
        setupReadmeControls();
        setupAttachmentLogic();
        setupRepoPicker();
        setupVisibilityToggle();
        setupOptionsCheckboxes();
        setupDescriptionCounter();
        setupReadmeEditor();
        setupKeyboardAwareness();
        
        binding.uploadButton.setOnClickListener(v -> startUploadProcess());
        binding.btnClose.setOnClickListener(v -> dismiss());

        binding.messageInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { 
                refreshUploadButtonState();
                updateReadinessChips();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        binding.readmeCustomInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { 
                refreshUploadButtonState();
                updateReadinessChips();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        refreshUploadButtonState();
        updateReadinessChips();

        // تطبيق حركات سينمائية
        startEntranceAnimations();
        startPulseAnimation();
        setupParallax();
    }

    private void setupKeyboardAwareness() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            boolean keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            View bar = binding.uploadButtonContainer;
            if (keyboardVisible) {
                bar.animate().translationY(bar.getHeight()).alpha(0f).setDuration(200).withEndAction(() -> bar.setVisibility(View.GONE)).start();
            } else {
                bar.setVisibility(View.VISIBLE);
                bar.animate().translationY(0f).alpha(1f).setDuration(200).start();
            }
            return insets;
        });
    }

    private void updateReadinessChips() {
        if (binding == null) return;

        boolean messageReady = !binding.messageInput.getText().toString().trim().isEmpty();
        boolean readmeReady = (currentReadmeMode != ReadmeMode.NONE && (currentReadmeMode != ReadmeMode.CUSTOM || !binding.readmeCustomInput.getText().toString().trim().isEmpty()));
        boolean attachmentsReady = !attachedFiles.isEmpty();
        boolean filesReady = resolvedFileCount > 0;

        // [AR] تحديث شريحة الملفات؛ إظهار خطأ في حال عدم وجود ملفات محلية.
        // [EN] Update files chip; show error if no local files found.
        String filesText = filesReady ? "Files ✔ " + resolvedFileCount : (resolvedFileCount == 0 ? "No local files" : "Checking files…");
        setChipReady(binding.chipReadyFiles, filesReady, filesText);
        if (resolvedFileCount == 0) {
            binding.chipReadyFiles.setChipBackgroundColorResource(R.color.md_theme_light_error_container);
            binding.chipReadyFiles.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_on_error_container));
        }

        setChipReady(binding.chipReadyMessage, messageReady, "Message");
        setChipReady(binding.chipReadyReadme, readmeReady, "README");
        setChipReady(binding.chipReadyAttachments, attachmentsReady, "Attachments");

        // [AR] تحديث شدة النبض بناءً على الجاهزية.
        // [EN] Update pulse intensity based on readiness.
        int readyCount = (filesReady ? 1 : 0) + (messageReady ? 1 : 0) + (readmeReady ? 1 : 0) + (attachmentsReady ? 1 : 0);
        float pulseAlpha = 0.1f + (readyCount * 0.15f);
        binding.iconPulseRing.setAlpha(pulseAlpha);
    }

    private void setChipReady(Chip chip, boolean ready, String text) {
        chip.setText(text);
        if (ready) {
            chip.setChipBackgroundColorResource(R.color.md_theme_light_primary_container);
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_on_primary_container));
            chip.setChipStrokeWidth(0);
            if (chip.getTag() == null || !(boolean)chip.getTag()) {
                chip.setScaleX(0.8f); chip.setScaleY(0.8f);
                chip.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(new OvershootInterpolator()).start();
                chip.setTag(true);
            }
        } else {
            chip.setChipBackgroundColorResource(android.R.color.transparent);
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_on_surface_variant));
            chip.setChipStrokeWidth(getResources().getDisplayMetrics().density);
            chip.setTag(false);
        }
    }

    private void startEntranceAnimations() {
        binding.pillHandleContainer.setTranslationY(-20f);
        binding.pillHandleContainer.animate().translationY(0f).setDuration(600).setInterpolator(new OvershootInterpolator(2f)).start();

        // [AR] تحريك البطاقات بحركة متدرجة سينمائية (البطاقات المدمجة الجديدة).
        // [EN] Animate cards with a cinematic staggered entry (new merged cards).
        View[] cards = { binding.cardRepository, binding.cardProject, binding.cardAttachments, binding.cardReadme, binding.contextInfoCard };
        for (int i = 0; i < cards.length; i++) {
            View card = cards[i];
            card.setAlpha(0f);
            card.setTranslationY(60f);
            card.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(150 + (i * 40)).setInterpolator(new OvershootInterpolator(0.8f)).start();
        }
    }

    private void startPulseAnimation() {
        android.animation.ObjectAnimator scaleX = android.animation.ObjectAnimator.ofFloat(binding.iconPulseRing, "scaleX", 1f, 1.4f);
        android.animation.ObjectAnimator scaleY = android.animation.ObjectAnimator.ofFloat(binding.iconPulseRing, "scaleY", 1f, 1.4f);
        
        android.animation.ValueAnimator alphaAnim = android.animation.ValueAnimator.ofFloat(1f, 0f);
        alphaAnim.addUpdateListener(animation -> {
            if (binding == null) return;
            float val = (float) animation.getAnimatedValue();
            boolean filesReady = resolvedFileCount > 0;
            boolean messageReady = !binding.messageInput.getText().toString().trim().isEmpty();
            boolean readmeReady = (currentReadmeMode != ReadmeMode.NONE && (currentReadmeMode != ReadmeMode.CUSTOM || !binding.readmeCustomInput.getText().toString().trim().isEmpty()));
            boolean attachmentsReady = !attachedFiles.isEmpty();
            int readyCount = (filesReady ? 1 : 0) + (messageReady ? 1 : 0) + (readmeReady ? 1 : 0) + (attachmentsReady ? 1 : 0);
            
            float intensity = 0.2f + (readyCount * 0.15f);
            binding.iconPulseRing.setAlpha(val * intensity);
        });

        scaleX.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        scaleY.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        alphaAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);

        android.animation.AnimatorSet set = new android.animation.AnimatorSet();
        set.playTogether(scaleX, scaleY, alphaAnim);
        set.setDuration(2000);
        set.start();
    }

    private void setupParallax() {
        binding.contentScrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            float factor = 0.5f;
            // Parallax disabled for simple header but logic kept for structure
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        View sheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet);
            behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            behavior.setFitToContents(false);
            ViewGroup.LayoutParams lp = sheet.getLayoutParams();
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            sheet.setLayoutParams(lp);
        }
    }

    private void detectUploadMode() {
        GitHubManager manager = GitHubManager.getInstance(requireContext());
        String login = manager.getUserLogin();
        String expectedRepoName = projectTitle.replaceAll("[^a-zA-Z0-9._-]", "-");
        if (expectedRepoName.isEmpty()) expectedRepoName = "project";
        String expectedRepoUrl = "https://github.com/" + login + "/" + expectedRepoName;

        existingRecord = manager.findUploadRecord(projectId, expectedRepoUrl);
        uploadMode = (existingRecord != null) ? MODE_COMMIT_PUSH : MODE_FIRST_PUSH;
    }

    private void applyModeLanguage() {
        boolean isCommit = uploadMode == MODE_COMMIT_PUSH;
        GitHubManager manager = GitHubManager.getInstance(requireContext());
        String login = manager.getUserLogin();
        
        String avatar = manager.getUserAvatar();
        if (avatar != null) {
            Glide.with(this).load(avatar).circleCrop().into(binding.userAvatar);
        }

        binding.textRepoName.setText(isCommit ? existingRecord.repoHtmlUrl.replace("https://github.com/", "") : "➕ Create new: " + projectTitle);
        binding.editProjectTitle.setText(projectTitle);
        
        binding.messageInput.setHint(isCommit ? "Describe what changed…" : "What is this project about?");
        
        binding.suggestionChips.removeAllViews();
        String[] suggestions = isCommit ? new String[]{"Update project", "Fix bug", "Add feature", "Refactor code", "Improve UI"} :
                new String[]{getString(R.string.upload_suggestion_initial), getString(R.string.upload_suggestion_release), getString(R.string.upload_suggestion_import), getString(R.string.upload_suggestion_backup)};
        
        for (String s : suggestions) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.view_chip_suggestion, binding.suggestionChips, false);
            chip.setText(s);
            chip.setCheckable(true);
            chip.setOnClickListener(v -> {
                binding.messageInput.setText(s);
                binding.messageInput.setSelection(s.length());
            });
            binding.suggestionChips.addView(chip);
        }

        binding.readmeNoneHint.setText(isCommit ? "No release notes for this commit." : getString(R.string.upload_readme_none_hint));

        if (isCommit) {
            binding.contextIcon.setImageResource(R.drawable.ic_mtrl_history);
            binding.contextTitle.setText(R.string.upload_context_already);
            String relativeTime = DateUtils.getRelativeTimeSpanString(existingRecord.uploadedAtMillis).toString();
            binding.contextSubtitle.setText(getString(R.string.upload_context_last_sync, relativeTime));
            binding.contextHint.setText(R.string.upload_context_already_sub);
        } else {
            binding.contextIcon.setImageResource(R.drawable.ic_github_brand);
            binding.contextTitle.setText(R.string.upload_context_first);
            binding.contextSubtitle.setText(getString(R.string.upload_context_first_sub, projectTitle));
            binding.contextHint.setText(R.string.upload_context_first_hint);
        }
    }

    private void setupHeader() {
        binding.projectTitle.setText(projectTitle);
    }

    private void refreshChangesOffThread() {
        binding.changesLoadingState.setVisibility(View.VISIBLE);
        binding.changesSummary.setVisibility(View.GONE);
        binding.filesBadge.setText("…");
        executor.execute(() -> {
            GitHubManager manager = GitHubManager.getInstance(requireContext());
            File root = new File(rootPath);
            if (!root.exists()) {
                mainHandler.post(() -> {
                    if (binding == null) return;
                    binding.changesLoadingState.setVisibility(View.GONE);
                    binding.changesSummary.setVisibility(View.VISIBLE);
                    binding.changesSummary.setText(R.string.upload_changes_missing);
                    binding.filesBadge.setText("0");
                    resolvedFileCount = 0;
                    refreshUploadButtonState();
                });
                return;
            }
            List<GitHubManager.UploadFile> files = manager.collectUploadFiles(root);
            int java = 0, res = 0, assets = 0, other = 0;
            for (GitHubManager.UploadFile f : files) {
                String p = f.relativePath;
                if (p.contains("/src/main/java/")) java++;
                else if (p.contains("/src/main/res/")) res++;
                else if (p.contains("/src/main/assets/")) assets++;
                else other++;
            }
            int fJ = java, fR = res, fA = assets, fO = other, total = files.size();
            mainHandler.post(() -> {
                if (binding == null) return;
                binding.changesLoadingState.setVisibility(View.GONE);
                binding.changesSummary.setVisibility(View.VISIBLE);
                binding.changesSummary.setText(getString(R.string.upload_changes_summary, fJ, fR, fA, fO));
                if (total == 0) binding.changesSummary.setText(R.string.upload_changes_none);
                binding.filesBadge.setText(getString(R.string.upload_changes_files, total));
                resolvedFileCount = total;
                refreshUploadButtonState();
            });
        });
    }

    private void refreshUploadButtonState() {
        if (binding == null) return;
        boolean hasFiles = resolvedFileCount > 0, hasMessage = !binding.messageInput.getText().toString().trim().isEmpty(), hasAttachments = !attachedFiles.isEmpty();
        boolean hasReadme = (currentReadmeMode == ReadmeMode.CUSTOM && !binding.readmeCustomInput.getText().toString().trim().isEmpty()) || currentReadmeMode == ReadmeMode.AUTO;
        boolean isCommit = uploadMode == MODE_COMMIT_PUSH, canUpload = hasFiles || hasAttachments || hasReadme, canAct = isCommit || canUpload;

        if (resolvedFileCount == -1) {
            binding.uploadButton.setEnabled(false);
            binding.uploadButton.setText(R.string.upload_button_preparing);
            binding.uploadButton.setAlpha(0.6f);
            binding.uploadButtonHint.setVisibility(View.GONE);
            return;
        }

        if (!canAct) {
            binding.uploadButton.setEnabled(false);
            binding.uploadButton.setText(R.string.upload_button_nothing);
            binding.uploadButton.setAlpha(0.5f);
            binding.uploadButtonHint.setVisibility(View.VISIBLE);
            binding.uploadButtonHint.setText(R.string.upload_button_needs_input);
            binding.uploadButtonHint.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_error));
            return;
        }

        binding.uploadButton.setEnabled(true);
        binding.uploadButton.setAlpha(1.0f);
        binding.uploadButtonHint.setVisibility(View.VISIBLE);
        binding.uploadButtonHint.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_on_surface));

        if (isCommit) {
            binding.uploadButton.setText(R.string.upload_commit_button);
            binding.uploadButton.setIconResource(R.drawable.ic_mtrl_history);
            if (!canUpload && !hasMessage) {
                binding.uploadButtonHint.setText(R.string.upload_empty_commit_warn);
                binding.uploadButtonHint.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_error));
            } else if (!hasMessage) binding.uploadButtonHint.setText(R.string.upload_no_message_commit);
            else buildCustomizationHint(hasAttachments, hasReadme, true);
        } else {
            boolean isCustomized = hasMessage || hasAttachments || hasReadme;
            if (!isCustomized) {
                binding.uploadButton.setText(R.string.upload_button_as_is);
                binding.uploadButton.setIconResource(R.drawable.ic_stars);
                binding.uploadButtonHint.setText(R.string.upload_hint_defaults);
            } else {
                binding.uploadButton.setText(R.string.upload_button_custom);
                binding.uploadButton.setIconResource(R.drawable.ic_github_brand);
                buildCustomizationHint(hasAttachments, hasReadme, hasMessage);
            }
        }
        binding.uploadProgress.setVisibility(View.GONE);
    }

    private void buildCustomizationHint(boolean hasAttachments, boolean hasReadme, boolean hasMessage) {
        List<String> details = new ArrayList<>();
        if (hasAttachments) details.add(attachedFiles.size() + " attachments");
        if (hasReadme) details.add(currentReadmeMode == ReadmeMode.AUTO ? "Auto README" : "Custom README");
        if (hasMessage) details.add("Custom message");
        binding.uploadButtonHint.setText(getString(R.string.upload_hint_summary, String.join(" · ", details)));
    }

    private void setupSuggestions() {
        for (int i = 0; i < binding.suggestionChips.getChildCount(); i++) {
            View child = binding.suggestionChips.getChildAt(i);
            if (child instanceof Chip) {
                child.setOnClickListener(v -> {
                    String text = String.valueOf(((Chip) v).getText());
                    binding.messageInput.setText(text);
                    binding.messageInput.setSelection(text.length());
                    updateReadinessChips();
                });
            }
        }
    }

    // [AR] تهيئة منتقي المستودع؛ يسمح للمستخدم باختيار ريبو موجود أو إنشاء جديد.
    // [EN] Initializes the repository picker; allows user to pick an existing repo or create new.
    private void setupRepoPicker() {
        binding.btnRepoPicker.setOnClickListener(v -> {
            String slug = binding.editProjectTitle.getText().toString();
            UserRepoPicker.show(requireContext(), slug, (repoFullName, createNew) -> {
                if (createNew) {
                    targetRepoFullName = null;
                    binding.textRepoName.setText("➕ Create new: " + slug);
                } else {
                    targetRepoFullName = repoFullName;
                    binding.textRepoName.setText(repoFullName);
                }
            });
        });
    }

    // [AR] تهيئة مبدّل الخصوصية (عام/خاص) للمستودع الجديد.
    // [EN] Initializes the visibility toggle (Public/Private) for the new repository.
    private void setupVisibilityToggle() {
        binding.visibilityToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                isPrivate = (checkedId == R.id.btn_private);
            }
        });
    }

    // [AR] تهيئة خيارات الرفع المتقدمة (README تلقائي و .gitignore).
    // [EN] Initializes advanced upload options (Auto README and .gitignore).
    private void setupOptionsCheckboxes() {
        binding.checkboxAutoReadme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && currentReadmeMode != ReadmeMode.AUTO) {
                binding.readmeModeToggleGroup.check(R.id.readme_auto);
            } else if (!isChecked && currentReadmeMode == ReadmeMode.AUTO) {
                binding.readmeModeToggleGroup.check(R.id.readme_none);
            }
        });
        binding.checkboxGitignore.setOnCheckedChangeListener((buttonView, isChecked) -> useGitignore = isChecked);
    }

    // [AR] تهيئة عداد أحرف الوصف؛ يضمن عدم تجاوز الحد المسموح.
    // [EN] Initializes description character counter; ensures limit isn't exceeded.
    private void setupDescriptionCounter() {
        binding.messageInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.descriptionCounter.setText(s.length() + "/500");
                updateReadinessChips();
                refreshUploadButtonState();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    // [AR] تهيئة محرر README بأسلوب كود مع أرقام أسطر حية.
    // [EN] Initializes README editor with code-style live line numbering.
    private void setupReadmeEditor() {
        binding.readmeCustomInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                int lines = binding.readmeCustomInput.getLineCount();
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= Math.max(1, lines); i++) sb.append(i).append("\n");
                binding.readmeLineNumbers.setText(sb.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void setupReadmeControls() {
        segmentedTransition(ReadmeMode.NONE);
        binding.readmeModeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.readme_none) {
                segmentedTransition(ReadmeMode.NONE);
                binding.checkboxAutoReadme.setChecked(false);
            } else if (checkedId == R.id.readme_custom) {
                segmentedTransition(ReadmeMode.CUSTOM);
                binding.checkboxAutoReadme.setChecked(false);
            } else if (checkedId == R.id.readme_auto) {
                showLicenseDialog();
                binding.checkboxAutoReadme.setChecked(true);
            }
            updateReadinessChips();
        });
        binding.btnPreviewReadme.setOnClickListener(v -> showReadmePreview());
    }

    private void showLicenseDialog() {
        String[] licenses = {"MIT", "Apache-2.0", "GPL-3.0", "BSD-3-Clause", "No License"};
        final int[] selectedIdx = {0};
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Choose a license").setSingleChoiceItems(licenses, 0, (dialog, which) -> selectedIdx[0] = which)
                .setPositiveButton("Accept", (dialog, which) -> { selectedLicense = licenses[selectedIdx[0]]; segmentedTransition(ReadmeMode.AUTO); })
                .setNegativeButton("Cancel", (dialog, which) -> { selectedLicense = "MIT"; segmentedTransition(ReadmeMode.AUTO); }).show();
    }

    private void showReadmePreview() {
        if (generatedReadmeMarkdown == null) return;
        
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);
        
        MarkdownRenderer.render(requireContext(), layout, generatedReadmeMarkdown, getLocalFilesForPreview());
        
        NestedScrollView scroll = new NestedScrollView(requireContext());
        scroll.addView(layout);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(selectedLicense + " Readme")
                .setView(scroll)
                .setNeutralButton("Copy", (dialog, which) -> {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("README", generatedReadmeMarkdown);
                    if (clipboard != null) clipboard.setPrimaryClip(clip);
                    Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton("Close", null)
                .show();
    }

    private List<File> getLocalFilesForPreview() {
        List<File> list = new ArrayList<>(attachedFiles);
        list.addAll(generatedBanners);
        return list;
    }

    private void segmentedTransition(ReadmeMode mode) {
        currentReadmeMode = mode;
        binding.readmeNoneHint.animate().alpha(0f).setDuration(150).withEndAction(() -> binding.readmeNoneHint.setVisibility(View.GONE)).start();
        binding.readmeCustomContainer.animate().alpha(0f).setDuration(150).withEndAction(() -> binding.readmeCustomContainer.setVisibility(View.GONE)).start();
        binding.readmeAutoContainer.animate().alpha(0f).setDuration(150).withEndAction(() -> binding.readmeAutoContainer.setVisibility(View.GONE)).start();
        View toShow = null;
        switch (mode) {
            case NONE: toShow = binding.readmeNoneHint; break;
            case CUSTOM: toShow = binding.readmeCustomContainer; break;
            case AUTO: toShow = binding.readmeAutoContainer; buildAutoReadmePreview(); break;
        }
        if (toShow != null) {
            toShow.setAlpha(0f); toShow.setVisibility(View.VISIBLE); toShow.animate().alpha(1f).setDuration(200).start();
        }
        refreshUploadButtonState();
    }

    private void buildAutoReadmePreview() {
        binding.readmeAutoStatus.setText("Generating README…");
        executor.execute(() -> {
            GitHubManager manager = GitHubManager.getInstance(requireContext());
            ReadmeGenerator.ReadmeResult result = ReadmeGenerator.generate(requireContext(), projectTitle, projectId, rootPath, manager.getUserLogin(), manager.getUserAvatar(), selectedLicense, attachedFiles);
            generatedReadmeMarkdown = result.markdown;
            generatedBanners = result.banners;
            mainHandler.post(() -> {
                if (binding != null) {
                    binding.readmeAutoStatus.setText("README ready ✔ (" + generatedReadmeMarkdown.split("\n").length + " lines)");
                    binding.autoReadmePreview.setText(generatedReadmeMarkdown.substring(0, Math.min(200, generatedReadmeMarkdown.length())) + "...");
                }
            });
        });
    }

    private void setupAttachmentLogic() {
        binding.addAttachment.setOnClickListener(v -> {
            if (attachedFiles.size() >= MAX_ATTACHMENTS) return;
            pickMultipleMedia.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE).build());
        });
        updateAttachmentStrip();
    }

    private void handlePickedUris(List<Uri> uris) {
        int remaining = MAX_ATTACHMENTS - attachedFiles.size();
        List<Uri> toProcess = uris.size() > remaining ? uris.subList(0, remaining) : uris;
        for (Uri uri : toProcess) {
            try {
                String name = getFileName(uri);
                if (getFileSize(uri) > MAX_ATTACH_BYTES) { Toast.makeText(requireContext(), getString(R.string.upload_attachments_too_big, name), Toast.LENGTH_SHORT).show(); continue; }
                File cacheFile = new File(requireContext().getCacheDir(), "attach_" + System.currentTimeMillis() + "_" + name);
                try (InputStream is = requireContext().getContentResolver().openInputStream(uri); FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    if (is != null) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = is.read(buf)) != -1) fos.write(buf, 0, len);
                    }
                }
                attachedFiles.add(cacheFile);
                updateReadinessChips();
            } catch (Exception ignored) {}
        }
        updateAttachmentStrip();
        refreshUploadButtonState();
    }

    private void updateAttachmentStrip() {
        binding.attachmentsStrip.removeAllViews();
        for (File file : attachedFiles) {
            View view = LayoutInflater.from(requireContext()).inflate(R.layout.item_upload_attachment, binding.attachmentsStrip, false);
            ImageView thumb = view.findViewById(R.id.thumbnail);
            View remove = view.findViewById(R.id.remove_btn), videoIcon = view.findViewById(R.id.video_icon);
            boolean isVideo = file.getName().toLowerCase().endsWith(".mp4") || file.getName().toLowerCase().endsWith(".mov");
            if (isVideo) { videoIcon.setVisibility(View.VISIBLE); thumb.setBackgroundColor(Color.BLACK); }
            else Glide.with(this).load(file).centerCrop().into(thumb);
            remove.setOnClickListener(v -> { 
                attachedFiles.remove(file); 
                file.delete(); 
                updateAttachmentStrip(); 
                refreshUploadButtonState();
                updateReadinessChips();
            });
            binding.attachmentsStrip.addView(view);
        }
        if (attachedFiles.size() < MAX_ATTACHMENTS) binding.attachmentsStrip.addView(binding.addAttachment);
        binding.attachmentsCount.setText(getString(R.string.upload_attachments_count, attachedFiles.size(), MAX_ATTACHMENTS));
    }

    private void startUploadProcess() {
        String title = binding.editProjectTitle.getText().toString();
        if (title.isEmpty()) title = projectTitle;
        
        String message = binding.messageInput.getText().toString(), readmeContent = null;
        if (currentReadmeMode == ReadmeMode.CUSTOM) readmeContent = binding.readmeCustomInput.getText().toString();
        else if (currentReadmeMode == ReadmeMode.AUTO) readmeContent = generatedReadmeMarkdown;

        File iconFile = new File(wq.e() + File.separator + projectId, "icon.png");
        String readmeIconPath = iconFile.exists() ? iconFile.getAbsolutePath() : null;

        Intent intent;
        List<String> allPaths = new ArrayList<>();
        for (File f : attachedFiles) allPaths.add(f.getAbsolutePath());
        for (File f : generatedBanners) allPaths.add(f.getAbsolutePath());

        if (uploadMode == MODE_COMMIT_PUSH) {
            if (message.isEmpty()) message = "Update " + title;
            intent = new Intent(requireContext(), GitCommitService.class);
            intent.setAction(GitCommitService.ACTION_COMMIT);
            intent.putExtra(GitCommitService.EXTRA_RECORD_JSON, new com.google.gson.Gson().toJson(existingRecord));
            intent.putExtra(GitCommitService.EXTRA_MESSAGE, message);
            intent.putExtra(GitCommitService.EXTRA_README, readmeContent);
            intent.putExtra(GitCommitService.EXTRA_ATTACHED_PATHS, allPaths.toArray(new String[0]));
            Toast.makeText(requireContext(), R.string.upload_commit_started_toast, Toast.LENGTH_SHORT).show();
        } else {
            if (message.isEmpty()) message = "Upload project: " + title;
            intent = new Intent(requireContext(), GitHubUploadService.class);
            intent.setAction(GitHubUploadService.ACTION_START_UPLOAD);
            intent.putExtra(GitHubUploadService.EXTRA_PROJECT_TITLE, title);
            intent.putExtra(GitHubUploadService.EXTRA_PROJECT_ROOT, rootPath);
            intent.putExtra(GitHubUploadService.EXTRA_COMMIT_MESSAGE, message);
            intent.putExtra(GitHubUploadService.EXTRA_README_CONTENT, readmeContent);
            intent.putExtra(GitHubUploadService.EXTRA_README_ICON_PATH, readmeIconPath);
            intent.putExtra(GitHubUploadService.EXTRA_ATTACHED_PATHS, allPaths.toArray(new String[0]));
            intent.putExtra(GitHubUploadService.EXTRA_REPO_PRIVATE, isPrivate);
            intent.putExtra(GitHubUploadService.EXTRA_TARGET_REPO_FULL_NAME, targetRepoFullName);
            intent.putExtra(GitHubUploadService.EXTRA_USE_GITIGNORE, useGitignore);
            Toast.makeText(requireContext(), R.string.upload_started_toast, Toast.LENGTH_SHORT).show();
        }
        binding.uploadButton.setEnabled(false);
        binding.uploadProgress.setVisibility(View.VISIBLE);
        binding.uploadButton.setIcon(null);
        binding.uploadButton.setText(null);
        ContextCompat.startForegroundService(requireContext(), intent);
        dismissAllowingStateLoss();
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) result = cursor.getString(idx);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result;
    }

    private long getFileSize(Uri uri) {
        try (android.database.Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx != -1) return cursor.getLong(idx);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
