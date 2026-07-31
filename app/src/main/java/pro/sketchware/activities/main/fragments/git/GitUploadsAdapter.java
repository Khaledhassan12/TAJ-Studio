package pro.sketchware.activities.main.fragments.git;

import android.content.Context;
import android.content.Intent;
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
import java.util.HashMap;
import java.util.List;

import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;
import pro.sketchware.R;
import pro.sketchware.databinding.ItemGitUploadBinding;
import pro.sketchware.github.GitHubManager;
import pro.sketchware.github.GitCommitBottomSheet;

/**
 * محول مخصص لعرض قائمة سجلات الرفع إلى GitHub.
 * نربط كل سجل ببيانات المشروع المحلية (إن وجدت) لعرض الأيقونة الصحيحة.
 * Custom adapter to display GitHub upload records.
 * We link each record to local project data (if available) to show the correct icon.
 */
public class GitUploadsAdapter extends RecyclerView.Adapter<GitUploadsAdapter.RecordViewHolder> {

    private final List<GitHubManager.GitUploadRecord> records;
    private final Context context;

    public GitUploadsAdapter(Context context, List<GitHubManager.GitUploadRecord> records) {
        this.context = context;
        this.records = records;
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

    class RecordViewHolder extends RecyclerView.ViewHolder {
        private final ItemGitUploadBinding binding;

        RecordViewHolder(ItemGitUploadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(GitHubManager.GitUploadRecord record) {
            binding.projectTitle.setText(record.projectTitle);
            
            // استخراج المسار القصير للمستودع (user/repo) للعرض الجمالي
            // Extract short repo path (user/repo) for a cleaner UI.
            String repoPath = record.repoHtmlUrl.replace("https://github.com/", "");
            binding.repoPath.setText(repoPath);

            // عرض الوقت النسبي (مثلاً: منذ ساعتين) بلغة الجهاز تلقائياً
            // Display relative time (e.g., 2h ago) automatically in system language.
            CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                    record.uploadedAtMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            binding.uploadTime.setText(relativeTime);

            binding.fileCount.setText(context.getString(R.string.git_files_count, record.fileCount));

            // نحاول استعادة أيقونة المشروع من المجلد المحلي باستخدام المعرف المخزن
            // Attempt to restore project icon from local folder using the stored ID.
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

            // تحميل صورة البروفايل المصغرة كشارة فوق الأيقونة
            // Load mini profile avatar as a badge over the icon.
            if (record.avatarUrl != null) {
                Glide.with(context)
                        .load(record.avatarUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_github_brand)
                        .into(binding.miniAvatar);
            } else {
                binding.miniAvatar.setImageResource(R.drawable.ic_github_brand);
            }

            // عند النقر، نفتح المستودع مباشرة في المتصفح
            // On click, open the repository directly in the browser.
            binding.getRoot().setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(record.repoHtmlUrl));
                context.startActivity(intent);
            });

            // ربط زر الـ Commit بفتح البوتم شيت المخصص مع منع وصول الحدث للأب (Row)
            // Link the Commit button to open the custom BottomSheet while preventing event bubbling.
            binding.commitButton.setOnClickListener(v -> {
                if (context instanceof androidx.fragment.app.FragmentActivity) {
                    GitCommitBottomSheet.newInstance(record)
                            .show(((androidx.fragment.app.FragmentActivity) context).getSupportFragmentManager(), 
                                    "GitCommit");
                }
            });
        }
    }
}
