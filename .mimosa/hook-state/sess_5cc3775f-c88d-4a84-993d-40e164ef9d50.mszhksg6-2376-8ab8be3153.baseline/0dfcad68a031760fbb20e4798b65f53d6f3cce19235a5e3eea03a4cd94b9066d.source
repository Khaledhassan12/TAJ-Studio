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
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;
import pro.sketchware.R;
import pro.sketchware.databinding.ItemGitUploadBinding;
import pro.sketchware.github.GitHubManager;
import pro.sketchware.github.GitCommitBottomSheet;

/**
 * [R5-R8 State Maintenance] GitUploadsAdapter - SSOT derived binding.
 * (عربي) محول سجلات الرفع - ربط البيانات مشتق من مصدر الحقيقة؛ يضمن استقرار وضع التحديد.
 */
public class GitUploadsAdapter extends RecyclerView.Adapter<GitUploadsAdapter.RecordViewHolder> {

    private final List<GitHubManager.GitUploadRecord> records = new ArrayList<>();
    private final Context context;
    private final GitUploadsFragment fragment;

    private boolean selectionMode = false;
    private Set<String> selectedRepoUrls = new HashSet<>();

    public GitUploadsAdapter(Context context, GitUploadsFragment fragment) {
        this.context = context;
        this.fragment = fragment;
        setHasStableIds(true);
    }

    public void setRecords(List<GitHubManager.GitUploadRecord> newRecords) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return records.size(); }
            @Override public int getNewListSize() { return newRecords.size(); }
            @Override public boolean areItemsTheSame(int oldP, int newP) {
                return records.get(oldP).repoHtmlUrl.equals(newRecords.get(newP).repoHtmlUrl);
            }
            @Override public boolean areContentsTheSame(int oldP, int newP) {
                GitHubManager.GitUploadRecord o = records.get(oldP);
                GitHubManager.GitUploadRecord n = newRecords.get(newP);
                return o.fileCount == n.fileCount && Objects.equals(o.projectTitle, n.projectTitle);
            }
        });
        records.clear();
        records.addAll(newRecords);
        result.dispatchUpdatesTo(this);
    }

    public void setSelectionState(boolean mode, Set<String> selected) {
        boolean modeChanged = (this.selectionMode != mode);
        this.selectionMode = mode;
        this.selectedRepoUrls = selected;
        if (modeChanged) {
            notifyDataSetChanged(); // Major mode shift
        } else {
            // Partial update is handled by fragment calling notifyItemChanged or similar
            // For simplicity here, we can re-notify all if needed or fragment can handle it.
            notifyDataSetChanged();
        }
    }

    @Override
    public long getItemId(int position) {
        return records.get(position).repoHtmlUrl.hashCode();
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
        holder.bind(records.get(position));
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    class RecordViewHolder extends RecyclerView.ViewHolder {
        private final ItemGitUploadBinding binding;

        RecordViewHolder(ItemGitUploadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(GitHubManager.GitUploadRecord record) {
            binding.projectTitle.setText(record.projectTitle);
            binding.repoPath.setText(record.repoHtmlUrl.replace("https://github.com/", ""));

            CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                    record.uploadedAtMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            binding.uploadTime.setText(relativeTime);
            binding.fileCount.setText(context.getString(R.string.git_files_count, record.fileCount));

            loadLocalProjectIcon(record);

            if (record.avatarUrl != null) {
                Glide.with(context).load(record.avatarUrl).circleCrop()
                        .placeholder(R.drawable.ic_github_brand).into(binding.miniAvatar);
            } else {
                binding.miniAvatar.setImageResource(R.drawable.ic_github_brand);
            }

            // R5-5: Binding derived from SSOT
            boolean isSelected = selectedRepoUrls.contains(record.repoHtmlUrl);
            
            if (selectionMode) {
                binding.commitButton.setImageResource(R.drawable.icon_delete);
                binding.commitButton.setBackgroundResource(isSelected ? 
                        R.drawable.circle_bg_error_alpha : R.drawable.circle_bg_surface);
                binding.commitButton.setImageTintList(ColorStateList.valueOf(isSelected ? 
                        context.getColor(R.color.scolor_red_01) : context.getColor(R.color.grey)));
                
                binding.getRoot().setBackgroundResource(isSelected ? 
                        R.drawable.bg_round_border_red : R.drawable.project_item_shape_alone);
                
                View.OnClickListener l = v -> fragment.toggleSelection(record.repoHtmlUrl);
                binding.getRoot().setOnClickListener(l);
                binding.commitButton.setOnClickListener(l);
                binding.getRoot().setOnLongClickListener(null);
            } else {
                binding.commitButton.setImageResource(R.drawable.ic_mtrl_update);
                binding.commitButton.setBackgroundResource(R.drawable.circle_bg_primary_alpha);
                binding.commitButton.setImageTintList(ColorStateList.valueOf(context.getColor(R.color.color_primary)));
                binding.getRoot().setBackgroundResource(R.drawable.project_item_shape_alone);

                binding.getRoot().setOnClickListener(v -> {
                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(record.repoHtmlUrl)));
                });

                binding.commitButton.setOnClickListener(v -> {
                    if (context instanceof androidx.fragment.app.FragmentActivity) {
                        GitCommitBottomSheet.newInstance(record)
                                .show(((androidx.fragment.app.FragmentActivity) context).getSupportFragmentManager(), "GitCommit");
                    }
                });

                binding.getRoot().setOnLongClickListener(v -> {
                    fragment.enterSelectionMode(record.repoHtmlUrl);
                    return true;
                });
            }
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
            if (!iconSet) binding.projectIcon.setImageResource(R.drawable.default_icon);
        }
    }
}
