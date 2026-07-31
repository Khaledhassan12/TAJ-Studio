package pro.sketchware.activities.main.fragments.git;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;
import pro.sketchware.R;
import pro.sketchware.databinding.ItemGitUploadBinding;
import pro.sketchware.github.GitHubManager;
import pro.sketchware.github.GitCommitBottomSheet;

/**
 * محول مخصص لعرض قائمة سجلات الرفع إلى GitHub مع دعم وضع الحذف المتعدد.
 * نتحكم هنا في مظهر الصفوف (عادي أم تحديد) ونطلق الأنيميشن المناسب.
 * Custom adapter to display GitHub upload records with multi-select delete support.
 * We control row appearance (normal vs selection) and trigger the appropriate animations.
 */
public class GitUploadsAdapter extends RecyclerView.Adapter<GitUploadsAdapter.RecordViewHolder> {

    private final List<GitHubManager.GitUploadRecord> records;
    private final Context context;
    private final OnSelectionChangeListener selectionChangeListener;

    private boolean selectionMode = false;
    private boolean longPressConsumed = false;
    private final Set<String> selectedRepoUrls = new LinkedHashSet<>();

    /**
     * مُبلّغ موحّد لتزامن حالة التحديد بين المحول والواجهة الرئيسية.
     * Unified listener to synchronize selection state between adapter and fragment.
     */
    public interface OnSelectionChangeListener {
        void onSelectionChanged(boolean inMode, int selectedCount);
    }

    public GitUploadsAdapter(Context context, List<GitHubManager.GitUploadRecord> records, OnSelectionChangeListener listener) {
        this.context = context;
        this.records = records;
        this.selectionChangeListener = listener;
    }

    @NonNull
    @Override
    public RecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemGitUploadBinding binding = ItemGitUploadBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new RecordViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordViewHolder holder, int position) {
        GitHubManager.GitUploadRecord record = records.get(position);
        holder.bind(record);
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void exitSelectionMode() {
        if (!selectionMode) return;
        selectionMode = false;
        longPressConsumed = false;
        selectedRepoUrls.clear();
        notifyDataSetChanged();
        notifyListener();
    }

    public List<GitHubManager.GitUploadRecord> getSelectedRecords() {
        List<GitHubManager.GitUploadRecord> selected = new ArrayList<>();
        for (GitHubManager.GitUploadRecord r : records) {
            if (selectedRepoUrls.contains(r.repoHtmlUrl)) {
                selected.add(r);
            }
        }
        return selected;
    }

    private void notifyListener() {
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectionMode, selectedRepoUrls.size());
        }
    }

    class RecordViewHolder extends RecyclerView.ViewHolder {
        private final ItemGitUploadBinding binding;

        RecordViewHolder(ItemGitUploadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(GitHubManager.GitUploadRecord record) {
            binding.projectTitle.setText(record.projectTitle);
            String repoPath = record.repoHtmlUrl.replace("https://github.com/", "");
            binding.repoPath.setText(repoPath);

            CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                    record.uploadedAtMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            binding.uploadTime.setText(relativeTime);
            binding.fileCount.setText(context.getString(R.string.git_files_count, record.fileCount));

            // أيقونة المشروع المحلية
            loadLocalProjectIcon(record);

            // صورة البروفايل المصغرة
            if (record.avatarUrl != null) {
                Glide.with(context).load(record.avatarUrl).circleCrop()
                        .placeholder(R.drawable.ic_github_brand).into(binding.miniAvatar);
            } else {
                binding.miniAvatar.setImageResource(R.drawable.ic_github_brand);
            }

            updateUiState(record);
        }

        private void loadLocalProjectIcon(GitHubManager.GitUploadRecord record) {
            boolean iconSet = false;
            if (record.projectId != null) {
                HashMap<String, Object> projectMap = lC.b(record.projectId);
                if (projectMap != null && yB.a(projectMap, "custom_icon")) {
                    File iconFile = new File(wq.e() + File.separator + record.projectId, "icon.png");
                    if (iconFile.exists()) {
                        String providerPath = context.getPackageName() + ".provider";
                        Uri uri = FileProvider.getUriForFile(context, providerPath, iconFile);
                        binding.projectIcon.setImageURI(uri);
                        iconSet = true;
                    }
                }
            }
            if (!iconSet) {
                binding.projectIcon.setImageResource(R.drawable.default_icon);
            }
        }

        private void updateUiState(GitHubManager.GitUploadRecord record) {
            boolean isSelected = selectedRepoUrls.contains(record.repoHtmlUrl);
            
            if (selectionMode) {
                // وضع الحذف: تبديل الأيقونة إلى سلة (حمراء للمحدد، رمادية للغير)
                // Delete mode: switch icon to trash (red for selected, grey otherwise)
                binding.commitButton.setImageResource(R.drawable.icon_delete);
                binding.commitButton.setBackgroundResource(isSelected ? 
                        R.drawable.circle_bg_error_alpha : R.drawable.circle_bg_surface);
                binding.commitButton.setImageTintList(ColorStateList.valueOf(isSelected ? 
                        context.getColor(R.color.scolor_red_01) : context.getColor(R.color.grey)));
                
                // تمييز الصف المحدّد بحدود أو لون مختلف
                binding.getRoot().setBackgroundResource(isSelected ? 
                        R.drawable.bg_round_border_red : R.drawable.project_item_shape_alone);
                
                // نستهلك النقرة المتسرّبة حتى في وضع التحديد لمنع إلغاء التحديد فور بدئه (سبب E1)
                // Even in selection mode, we consume the ghost click to prevent instant deselection (fixing E1).
                View.OnClickListener toggleClick = v -> {
                    if (longPressConsumed) {
                        longPressConsumed = false;
                        return;
                    }
                    toggleSelection(record);
                };

                binding.getRoot().setOnClickListener(toggleClick);
                binding.commitButton.setOnClickListener(toggleClick);
                binding.getRoot().setOnLongClickListener(null);
            } else {
                // الوضع العادي: أيقونة commit/push الزرقاء
                // Normal mode: blue commit/push icon
                binding.commitButton.setImageResource(R.drawable.ic_mtrl_update);
                binding.commitButton.setBackgroundResource(R.drawable.circle_bg_primary_alpha);
                binding.commitButton.setImageTintList(ColorStateList.valueOf(context.getColor(R.color.color_primary)));
                binding.getRoot().setBackgroundResource(R.drawable.project_item_shape_alone);

                binding.getRoot().setOnClickListener(v -> {
                    // نستهلك النقرة المتسرّبة التي قد تأتي فوراً بعد الضغط المطوّل (سبب E1)
                    // Consume the ghost click that often follows a long-press (fixing E1).
                    if (longPressConsumed) {
                        longPressConsumed = false;
                        return;
                    }
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(record.repoHtmlUrl));
                    context.startActivity(intent);
                });

                binding.commitButton.setOnClickListener(v -> {
                    if (context instanceof androidx.fragment.app.FragmentActivity) {
                        GitCommitBottomSheet.newInstance(record)
                                .show(((androidx.fragment.app.FragmentActivity) context).getSupportFragmentManager(), "GitCommit");
                    }
                });

                binding.getRoot().setOnLongClickListener(v -> {
                    longPressConsumed = true;
                    enterSelectionMode(record);
                    return true;
                });
            }
        }

        private void enterSelectionMode(GitHubManager.GitUploadRecord origin) {
            selectionMode = true;
            selectedRepoUrls.clear();
            selectedRepoUrls.add(origin.repoHtmlUrl);
            notifyDataSetChanged();
            notifyListener();
        }

        private void toggleSelection(GitHubManager.GitUploadRecord record) {
            if (selectedRepoUrls.contains(record.repoHtmlUrl)) {
                selectedRepoUrls.remove(record.repoHtmlUrl);
            } else {
                selectedRepoUrls.add(record.repoHtmlUrl);
            }
            
            // إغلاق الوضع تلقائياً عند فراغ التحديد، لمنع حالة "0 selected" الغريبة
            // Auto-exit if selection becomes empty, preventing the weird "0 selected" state.
            if (selectedRepoUrls.isEmpty()) {
                exitSelectionMode();
                return;
            }

            // أنيميشن POP للأيقونة عند التحديد
            binding.commitButton.animate().scaleX(1.3f).scaleY(1.3f).setDuration(120).withEndAction(() -> 
                binding.commitButton.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            ).start();
            
            notifyItemChanged(getBindingAdapterPosition());
            notifyListener();
        }
    }
}
