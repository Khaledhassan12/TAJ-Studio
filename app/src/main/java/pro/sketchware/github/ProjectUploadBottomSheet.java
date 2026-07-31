package pro.sketchware.github;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
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
import androidx.core.content.FileProvider;
import androidx.core.widget.NestedScrollView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;

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

/**
 * بوتم شيت استوديو الرفع (Upload Studio) — يوفر واجهة متقدمة لرفع المشاريع إلى GitHub
 * مع دعم الإرفاقات (صور/فيديو) وتوليد تلقائي لملف README.
 * Upload Studio Bottom Sheet — provides an advanced interface for uploading projects to GitHub
 * with support for attachments (images/video) and automated README generation.
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

    // حالة حسم الملفات: -1 يعني جارٍ الفحص، 0 يعني فارغ، >0 يعني وُجدت ملفات
    // File resolution state: -1 means checking, 0 means empty, >0 means files found.
    private int resolvedFileCount = -1;

    private enum ReadmeMode { NONE, CUSTOM, AUTO }
    private ReadmeMode currentReadmeMode = ReadmeMode.NONE;

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
        
        binding.uploadButton.setOnClickListener(v -> startUploadProcess());

        // نُبعد المحتوى عن منطقة الزر السفلي الشفاف لضمان عدم التداخل البصري
        // Keep content clear of the transparent sticky footer area.
        binding.getRoot().post(() -> {
            if (binding != null) {
                View footer = binding.getRoot().findViewById(R.id.upload_button_container);
                View scroll = binding.getRoot().findViewById(R.id.content_scroll_view);
                if (footer != null && scroll != null) {
                    scroll.setPadding(scroll.getPaddingLeft(), scroll.getPaddingTop(), 
                            scroll.getPaddingRight(), footer.getHeight() + (int)(16 * getResources().getDisplayMetrics().density));
                }
            }
        });
        
        // نربط مراقبي التغيير لتحديث حالة الزر ديناميكياً
        // Attach change listeners to update the button state dynamically.
        binding.messageInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshUploadButtonState(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        binding.readmeCustomInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshUploadButtonState(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        refreshUploadButtonState();

        // تطبيق حركة دخول خفيفة
        // Simple entrance animation
        view.setAlpha(0f);
        view.setTranslationY(40f);
        view.animate().alpha(1f).translationY(0f).setDuration(300).start();
    }

    @Override
    public void onStart() {
        super.onStart();
        // نُجبر البوتم شيت على الحالة الممدودة (Expanded) لمنع الانكماش وكشف ما خلفه
        // Force the BottomSheet to stay expanded to prevent shrinking and exposing background.
        View sheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior = 
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet);
            behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            behavior.setFitToContents(false);
            
            // نجعل الارتفاع يغطي الشاشة تقريباً لضمان عدم ظهور التابات
            // Set height to cover screen to ensure tabs aren't visible.
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

        // 1. تحديث الهيدر
        String sub = isCommit ? 
                getString(R.string.upload_mode_commit_sub, login, "main") :
                getString(R.string.upload_mode_first_sub, login, "main");
        binding.uploadTarget.setText(sub);

        // 2. تحديث الرسالة والاقتراحات
        // نستخدم TextInputLayout كمصدر وحيد للـ hint لضمان عدم التداخل، ونمسح hint الـ EditText
        // Use TextInputLayout as the single source for hints to prevent overlap, clearing EditText hint.
        binding.messageInput.setHint(null);
        com.google.android.material.textfield.TextInputLayout messageLayout = binding.getRoot().findViewById(R.id.message_input_layout);
        if (messageLayout != null) {
            messageLayout.setHint(isCommit ? "Describe what changed…" : getString(R.string.upload_message_hint));
        }
        
        binding.suggestionChips.removeAllViews();
        String[] suggestions = isCommit ? 
                new String[]{"Update project", "Fix bug", "Add feature", "Refactor code", "Improve UI"} :
                new String[]{getString(R.string.upload_suggestion_initial), getString(R.string.upload_suggestion_release), 
                             getString(R.string.upload_suggestion_import), getString(R.string.upload_suggestion_backup)};
        
        for (String s : suggestions) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.view_chip_suggestion, binding.suggestionChips, false);
            chip.setText(s);
            chip.setOnClickListener(v -> {
                binding.messageInput.setText(s);
                binding.messageInput.setSelection(s.length());
            });
            binding.suggestionChips.addView(chip);
        }

        // 3. تحديث تلميح الـ README وضع None
        binding.readmeNoneHint.setText(isCommit ? "No release notes for this commit." : getString(R.string.upload_readme_none_hint));

        // 4. تحديث بطاقة السياق
        View contextCard = binding.getRoot().findViewById(R.id.context_info_card);
        ImageView contextIcon = binding.getRoot().findViewById(R.id.context_icon);
        TextView contextTitle = binding.getRoot().findViewById(R.id.context_title);
        TextView contextSubtitle = binding.getRoot().findViewById(R.id.context_subtitle);
        TextView contextHint = binding.getRoot().findViewById(R.id.context_hint);

        if (isCommit) {
            if (contextIcon != null) contextIcon.setImageResource(R.drawable.ic_mtrl_history);
            if (contextTitle != null) contextTitle.setText(R.string.upload_context_already);
            String relativeTime = DateUtils.getRelativeTimeSpanString(existingRecord.uploadedAtMillis).toString();
            if (contextSubtitle != null) contextSubtitle.setText(getString(R.string.upload_context_last_sync, relativeTime));
            if (contextHint != null) contextHint.setText(R.string.upload_context_already_sub);
        } else {
            if (contextIcon != null) contextIcon.setImageResource(R.drawable.ic_github_brand);
            if (contextTitle != null) contextTitle.setText(R.string.upload_context_first);
            if (contextSubtitle != null) contextSubtitle.setText(getString(R.string.upload_context_first_sub, projectTitle));
            if (contextHint != null) contextHint.setText(R.string.upload_context_first_hint);
        }
        
        // حركة دخول للبطاقة
        if (contextCard != null) {
            contextCard.setAlpha(0f);
            contextCard.setTranslationY(20f);
            contextCard.animate().alpha(1f).translationY(0f).setDuration(400).setStartDelay(100).start();
        }
    }

    private void setupHeader() {
        binding.projectTitle.setText(projectTitle);
        GitHubManager manager = GitHubManager.getInstance(requireContext());
        String login = manager.getUserLogin();
        binding.uploadTarget.setText(getString(R.string.upload_studio_pushing_to, login, "main"));

        // جلب أيقونة المشروع أو استخدام الـ fallback بماركة GitHub الملوّنة
        // Fetch project icon or use the colored GitHub branded fallback.
        File iconFile = new File(wq.e() + File.separator + projectId, "icon.png");
        if (iconFile.exists()) {
            binding.projectIcon.setVisibility(View.VISIBLE);
            binding.projectIconFallbackContainer.setVisibility(View.GONE);
            binding.projectIcon.setImageURI(Uri.fromFile(iconFile));
        } else {
            binding.projectIcon.setVisibility(View.GONE);
            binding.projectIconFallbackContainer.setVisibility(View.VISIBLE);
            binding.projectIconFallbackText.setVisibility(View.GONE);
            
            // رسم دائرة خافتة بماركة GitHub ملوّنة لتعريف المشروع
            // Draw a faint circle with colored GitHub brand to identify the project.
            int primary = ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(primary);
            gd.setAlpha(30); // ~12% opacity
            binding.projectIconFallbackBg.setBackground(gd);
            
            // إضافة أيقونة الماركة برمجياً لضمان الدقة واللون
            // Programmatically add the brand icon for precision and color.
            binding.projectIconFallbackContainer.removeAllViews();
            binding.projectIconFallbackContainer.addView(binding.projectIconFallbackBg);
            
            ImageView fallbackIcon = new ImageView(requireContext());
            int iconSize = (int)(24 * getResources().getDisplayMetrics().density);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(iconSize, iconSize);
            lp.gravity = Gravity.CENTER;
            fallbackIcon.setLayoutParams(lp);
            fallbackIcon.setImageResource(R.drawable.ic_github_brand);
            fallbackIcon.setImageTintList(android.content.res.ColorStateList.valueOf(primary));
            binding.projectIconFallbackContainer.addView(fallbackIcon);
        }
    }

    private void refreshChangesOffThread() {
        View loading = binding.getRoot().findViewById(R.id.changes_loading_state);
        View summary = binding.getRoot().findViewById(R.id.changes_summary);
        TextView badge = binding.getRoot().findViewById(R.id.files_badge);
        
        if (loading != null) loading.setVisibility(View.VISIBLE);
        if (summary != null) summary.setVisibility(View.GONE);
        if (badge != null) badge.setText("…");
        
        executor.execute(() -> {
            GitHubManager manager = GitHubManager.getInstance(requireContext());
            File root = new File(rootPath);
            if (!root.exists()) {
                mainHandler.post(() -> {
                    if (binding == null) return;
                    if (loading != null) loading.setVisibility(View.GONE);
                    if (summary != null) {
                        summary.setVisibility(View.VISIBLE);
                        ((TextView)summary).setText(R.string.upload_changes_missing);
                    }
                    if (badge != null) badge.setText("0");
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
            
            int finalJava = java, finalRes = res, finalAssets = assets, finalOther = other;
            int total = files.size();
            
            mainHandler.post(() -> {
                if (binding == null) return;
                if (loading != null) loading.setVisibility(View.GONE);
                if (summary != null) {
                    summary.setVisibility(View.VISIBLE);
                    ((TextView)summary).setText(getString(R.string.upload_changes_summary, finalJava, finalRes, finalAssets, finalOther));
                    if (total == 0) ((TextView)summary).setText(R.string.upload_changes_none);
                }
                
                if (badge != null) badge.setText(getString(R.string.upload_changes_files, total));
                
                resolvedFileCount = total;
                refreshUploadButtonState();
            });
        });
    }

    /**
     * تُحدِّث حالة زر الرفع (النص، الأيقونة، التلميح) بناءً على ما أدخله المستخدم وحالة الملفات.
     * Updates the upload button state (text, icon, hint) based on user input and file status.
     */
    private void refreshUploadButtonState() {
        if (binding == null) return;

        boolean hasFiles = resolvedFileCount > 0;
        boolean hasMessage = !binding.messageInput.getText().toString().trim().isEmpty();
        boolean hasAttachments = !attachedFiles.isEmpty();
        boolean hasReadme = (currentReadmeMode == ReadmeMode.CUSTOM && 
                !binding.readmeCustomInput.getText().toString().trim().isEmpty()) 
                || currentReadmeMode == ReadmeMode.AUTO;
        
        boolean isCommit = uploadMode == MODE_COMMIT_PUSH;
        
        // في وضع COMMIT، الزر متاح دائماً. في وضع FIRST_PUSH، يتطلب canUpload.
        // In COMMIT mode, button is always enabled. In FIRST_PUSH, it requires canUpload.
        boolean canUpload = hasFiles || hasAttachments || hasReadme;
        boolean canAct = isCommit || canUpload;

        if (resolvedFileCount == -1) {
            // حالة الفحص الجاري
            binding.uploadButton.setEnabled(false);
            binding.uploadButton.setText(R.string.upload_button_preparing);
            binding.uploadButton.setAlpha(0.6f);
            View buttonHint = binding.getRoot().findViewById(R.id.upload_button_hint);
            if (buttonHint != null) buttonHint.setVisibility(View.GONE);
            return;
        }

        if (!canAct) {
            // حالة لا يوجد شيء للرفع (UPLOAD فقط)
            binding.uploadButton.setEnabled(false);
            binding.uploadButton.setText(R.string.upload_button_nothing);
            binding.uploadButton.setAlpha(0.5f);
            
            TextView buttonHint = binding.getRoot().findViewById(R.id.upload_button_hint);
            if (buttonHint != null) {
                buttonHint.setVisibility(View.VISIBLE);
                buttonHint.setText(R.string.upload_button_needs_input);
                buttonHint.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_error));
            }
            return;
        }

        // حالة الرفع المتاحة
        binding.uploadButton.setEnabled(true);
        binding.uploadButton.setAlpha(1.0f);
        TextView buttonHintView = binding.getRoot().findViewById(R.id.upload_button_hint);
        if (buttonHintView != null) {
            buttonHintView.setVisibility(View.VISIBLE);
            buttonHintView.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_on_surface));
        }

        if (isCommit) {
            binding.uploadButton.setText(R.string.upload_commit_button);
            binding.uploadButton.setIconResource(R.drawable.ic_mtrl_history);
            
            TextView buttonHint = binding.getRoot().findViewById(R.id.upload_button_hint);
            if (buttonHint != null) {
                if (!canUpload && !hasMessage) {
                    buttonHint.setText(R.string.upload_empty_commit_warn);
                    buttonHint.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_error));
                    buttonHint.setAlpha(0.6f);
                } else if (!hasMessage) {
                    buttonHint.setText(R.string.upload_no_message_commit);
                } else {
                    buildCustomizationHint(hasAttachments, hasReadme, true);
                }
            }
        } else {
            boolean isCustomized = hasMessage || hasAttachments || hasReadme;
            if (!isCustomized) {
                // حالة "الرفع كما هو" (التخطّي بالافتراضي)
                binding.uploadButton.setText(R.string.upload_button_as_is);
                binding.uploadButton.setIconResource(R.drawable.ic_stars); // أيقونة ⚡
                
                TextView buttonHint = binding.getRoot().findViewById(R.id.upload_button_hint);
                if (buttonHint != null) buttonHint.setText(R.string.upload_hint_defaults);
            } else {
                // حالة "الرفع المخصص"
                binding.uploadButton.setText(R.string.upload_button_custom);
                binding.uploadButton.setIconResource(R.drawable.ic_github_brand);
                buildCustomizationHint(hasAttachments, hasReadme, hasMessage);
            }
        }
    }

    private void buildCustomizationHint(boolean hasAttachments, boolean hasReadme, boolean hasMessage) {
        List<String> details = new ArrayList<>();
        if (hasAttachments) details.add(attachedFiles.size() + " attachments");
        if (hasReadme) details.add(currentReadmeMode == ReadmeMode.AUTO ? "Auto README" : "Custom README");
        if (hasMessage) details.add("Custom message");
        
        String hint = String.join(" · ", details);
        TextView buttonHint = binding.getRoot().findViewById(R.id.upload_button_hint);
        if (buttonHint != null) buttonHint.setText(getString(R.string.upload_hint_summary, hint));
    }

    private void setupSuggestions() {
        for (int i = 0; i < binding.suggestionChips.getChildCount(); i++) {
            View child = binding.suggestionChips.getChildAt(i);
            if (child instanceof Chip) {
                child.setOnClickListener(v -> {
                    binding.messageInput.setText(((Chip) v).getText());
                    binding.messageInput.setSelection(binding.messageInput.getText().length());
                });
            }
        }
    }

    private void setupReadmeControls() {
        segmentedTransition(ReadmeMode.NONE);
        
        binding.readmeNone.setOnClickListener(v -> segmentedTransition(ReadmeMode.NONE));
        binding.readmeCustom.setOnClickListener(v -> segmentedTransition(ReadmeMode.CUSTOM));
        binding.readmeAuto.setOnClickListener(v -> segmentedTransition(ReadmeMode.AUTO));
    }

    /**
     * تحديث وضع الـ README وتغيير شكل أزرار الاختيار (Segmented Control).
     * Update README mode and toggle segmented control button styles.
     */
    private void segmentedTransition(ReadmeMode mode) {
        currentReadmeMode = mode;
        
        resetReadmeButton(binding.readmeNone);
        resetReadmeButton(binding.readmeCustom);
        resetReadmeButton(binding.readmeAuto);
        
        // إخفاء كافة المناطق مع حركة تلاشٍ خفيفة
        // Hide all areas with a subtle fade animation.
        binding.readmeNoneHint.animate().alpha(0f).setDuration(150).withEndAction(() -> binding.readmeNoneHint.setVisibility(View.GONE)).start();
        binding.readmeCustomInput.animate().alpha(0f).setDuration(150).withEndAction(() -> binding.readmeCustomInput.setVisibility(View.GONE)).start();
        binding.readmeAutoContainer.animate().alpha(0f).setDuration(150).withEndAction(() -> binding.readmeAutoContainer.setVisibility(View.GONE)).start();

        TextView selected = null;
        View toShow = null;
        switch (mode) {
            case NONE:
                selected = binding.readmeNone;
                toShow = binding.readmeNoneHint;
                break;
            case CUSTOM:
                selected = binding.readmeCustom;
                toShow = binding.readmeCustomInput;
                break;
            case AUTO:
                selected = binding.readmeAuto;
                toShow = binding.readmeAutoContainer;
                buildAutoReadmePreview();
                break;
        }

        if (toShow != null) {
            toShow.setAlpha(0f);
            toShow.setVisibility(View.VISIBLE);
            toShow.animate().alpha(1f).setDuration(200).start();
        }

        if (selected != null) {
            int primary = ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary);
            int onPrimary = ContextCompat.getColor(requireContext(), R.color.md_theme_light_on_primary);
            
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(primary);
            gd.setCornerRadius(8 * getResources().getDisplayMetrics().density);
            selected.setBackground(gd);
            selected.setTextColor(onPrimary);
        }
        
        refreshUploadButtonState();
    }

    private void resetReadmeButton(TextView view) {
        view.setBackground(null);
        view.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_on_surface_variant));
    }

    private void buildAutoReadmePreview() {
        executor.execute(() -> {
            GitHubManager manager = GitHubManager.getInstance(requireContext());
            List<GitHubManager.UploadFile> files = manager.collectUploadFiles(new File(rootPath));
            
            StringBuilder sb = new StringBuilder();
            sb.append("• Project Title: ").append(projectTitle).append("\n");
            sb.append("• Files to include: ").append(files.size()).append("\n");
            
            File iconFile = new File(wq.e() + File.separator + projectId, "icon.png");
            if (iconFile.exists()) {
                sb.append("• App icon will be included (docs/app-icon.png)");
            }

            String preview = sb.toString();
            mainHandler.post(() -> {
                if (binding != null) binding.autoReadmePreview.setText(preview);
            });
        });
    }

    private void setupAttachmentLogic() {
        binding.addAttachment.setOnClickListener(v -> {
            if (attachedFiles.size() >= MAX_ATTACHMENTS) return;
            pickMultipleMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                    .build());
        });
        updateAttachmentStrip();
    }

    private void handlePickedUris(List<Uri> uris) {
        int remaining = MAX_ATTACHMENTS - attachedFiles.size();
        List<Uri> toProcess = uris.size() > remaining ? uris.subList(0, remaining) : uris;

        for (Uri uri : toProcess) {
            try {
                String name = getFileName(uri);
                long size = getFileSize(uri);
                
                if (size > MAX_ATTACH_BYTES) {
                    Toast.makeText(requireContext(), getString(R.string.upload_attachments_too_big, name), Toast.LENGTH_SHORT).show();
                    continue;
                }

                File cacheFile = new File(requireContext().getCacheDir(), "attach_" + System.currentTimeMillis() + "_" + name);
                try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) != -1) fos.write(buf, 0, len);
                }
                attachedFiles.add(cacheFile);
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
            View remove = view.findViewById(R.id.remove_btn);
            View videoIcon = view.findViewById(R.id.video_icon);
            
            boolean isVideo = file.getName().toLowerCase().endsWith(".mp4") || file.getName().toLowerCase().endsWith(".mov");
            
            if (isVideo) {
                videoIcon.setVisibility(View.VISIBLE);
                thumb.setBackgroundColor(Color.BLACK);
            } else {
                videoIcon.setVisibility(View.GONE);
                Glide.with(this).load(file).centerCrop().into(thumb);
            }

            remove.setOnClickListener(v -> {
                attachedFiles.remove(file);
                file.delete();
                updateAttachmentStrip();
                refreshUploadButtonState();
            });

            binding.attachmentsStrip.addView(view);
        }

        if (attachedFiles.size() < MAX_ATTACHMENTS) {
            binding.attachmentsStrip.addView(binding.addAttachment);
        }
        
        binding.attachmentsCount.setText(getString(R.string.upload_attachments_count, attachedFiles.size(), MAX_ATTACHMENTS));
    }

    private void startUploadProcess() {
        String message = binding.messageInput.getText().toString();
        
        String readmeContent = null;
        if (currentReadmeMode == ReadmeMode.CUSTOM) {
            readmeContent = binding.readmeCustomInput.getText().toString();
        } else if (currentReadmeMode == ReadmeMode.AUTO) {
            readmeContent = generateAutoReadme();
        }

        File iconFile = new File(wq.e() + File.separator + projectId, "icon.png");
        String readmeIconPath = iconFile.exists() ? iconFile.getAbsolutePath() : null;

        String[] attachedPaths = new String[attachedFiles.size()];
        for (int i = 0; i < attachedFiles.size(); i++) {
            attachedPaths[i] = attachedFiles.get(i).getAbsolutePath();
        }

        Intent intent;
        if (uploadMode == MODE_COMMIT_PUSH) {
            if (message.isEmpty()) message = "Update " + projectTitle;
            intent = new Intent(requireContext(), GitCommitService.class);
            intent.setAction(GitCommitService.ACTION_COMMIT);
            intent.putExtra(GitCommitService.EXTRA_RECORD_JSON, new com.google.gson.Gson().toJson(existingRecord));
            intent.putExtra(GitCommitService.EXTRA_MESSAGE, message);
            intent.putExtra(GitCommitService.EXTRA_README, readmeContent);
            intent.putExtra(GitCommitService.EXTRA_ATTACHED_PATHS, attachedPaths);
            Toast.makeText(requireContext(), R.string.upload_commit_started_toast, Toast.LENGTH_SHORT).show();
        } else {
            if (message.isEmpty()) message = "Upload project: " + projectTitle;
            intent = new Intent(requireContext(), GitHubUploadService.class);
            intent.setAction(GitHubUploadService.ACTION_START_UPLOAD);
            intent.putExtra(GitHubUploadService.EXTRA_PROJECT_TITLE, projectTitle);
            intent.putExtra(GitHubUploadService.EXTRA_PROJECT_ROOT, rootPath);
            intent.putExtra(GitHubUploadService.EXTRA_COMMIT_MESSAGE, message);
            intent.putExtra(GitHubUploadService.EXTRA_README_CONTENT, readmeContent);
            intent.putExtra(GitHubUploadService.EXTRA_README_ICON_PATH, readmeIconPath);
            intent.putExtra(GitHubUploadService.EXTRA_ATTACHED_PATHS, attachedPaths);
            Toast.makeText(requireContext(), R.string.upload_started_toast, Toast.LENGTH_SHORT).show();
        }
        
        ContextCompat.startForegroundService(requireContext(), intent);
        dismissAllowingStateLoss();
    }

    /**
     * مولّد README التلقائي — يأخذ ما يجد ويترك ما لا يجد عمداً لضمان عدم الفشل.
     * Auto-README Generator — takes what it finds and leaves what it doesn't to ensure no failure.
     */
    private String generateAutoReadme() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(projectTitle).append("\n\n");
        
        File iconFile = new File(wq.e() + File.separator + projectId, "icon.png");
        if (iconFile.exists()) {
            sb.append("![](").append("docs/app-icon.png").append(")\n\n");
        }

        sb.append("## Project Details\n");
        sb.append("- **Project ID**: ").append(projectId).append("\n");
        
        try {
            GitHubManager manager = GitHubManager.getInstance(requireContext());
            List<GitHubManager.UploadFile> files = manager.collectUploadFiles(new File(rootPath));
            sb.append("- **Total Files**: ").append(files.size()).append("\n");
        } catch (Exception ignored) {}

        if (!attachedFiles.isEmpty()) {
            sb.append("\n## Attachments\n");
            for (File f : attachedFiles) {
                sb.append("- ").append(f.getName()).append(" (attachments/").append(f.getName()).append(")\n");
            }
        }

        sb.append("\n---\n*Uploaded from **TAJ Studio** (Sketchware Pro based) on ")
          .append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()))
          .append("*");
          
        return sb.toString();
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
