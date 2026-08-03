package com.besome.sketch.adapters;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.export.ExportProjectActivity;
import com.besome.sketch.lib.ui.LoadingDialog;
import com.besome.sketch.projects.MyProjectSettingActivity;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import a.a.a.DB;
import a.a.a.lC;
import a.a.a.mB;
import a.a.a.wq;
import a.a.a.yB;
import mod.hey.studios.project.ProjectSettingsDialog;
import mod.hey.studios.project.backup.BackupRestoreManager;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.activities.main.fragments.projects.ProjectsFragment;
import pro.sketchware.activities.main.fragments.projects.InstalledAppsRepository;
import pro.sketchware.activities.main.fragments.projects.AppIconLoader;
import pro.sketchware.databinding.BottomSheetProjectOptionsBinding;
import pro.sketchware.databinding.MyprojectsItemBinding;
import pro.sketchware.github.GitHubManager;
import pro.sketchware.github.GitHubSignInSheet;
import pro.sketchware.github.GitHubUploadService;
import pro.sketchware.github.ProjectUploadBottomSheet;

public class ProjectsAdapter extends RecyclerView.Adapter<ProjectsAdapter.ProjectViewHolder> {
    private final ProjectsFragment projectsFragment;
    private final Activity activity;
    private final DB preference;
    private List<HashMap<String, Object>> shownProjects = new ArrayList<>();
    private List<HashMap<String, Object>> allProjects;

    /**
     * WHAT: Static LruCache for project icons.
     * WHY: Reading icons from disk (setImageURI) on every bind during scroll causes jank.
     * (عربي) كاش ثابت لأيقونات المشاريع لتجنب القراءة المتكررة من القرص أثناء التمرير.
     */
    private static final android.util.LruCache<String, android.graphics.Bitmap> ICON_CACHE = 
            new android.util.LruCache<>(30);

    /**
     * WHAT: Public API to invalidate the icon cache for a specific project.
     * WHY: Ensures UI reflects icon changes immediately without direct cache exposure.
     * (عربي) واجهة برمجية لإبطال كاش أيقونة مشروع معين عند تغييرها.
     */
    public static void invalidateIconCache(String scId) {
        if (scId != null) {
            ICON_CACHE.remove(scId);
        }
    }

    /**
     * WHAT: lightweightProjectIconRefresh - Targeted refresh for a single project icon.
     * WHY: Avoids reloading the entire project list from disk when only one icon changes.
     * (عربي) تحديث خفيف: تحديث أيقونة مشروع واحد فقط لتجنب إعادة تحميل القائمة بالكامل من القرص.
     */
    public void lightweightProjectIconRefresh(String scId) {
        invalidateIconCache(scId);
        for (int i = 0; i < shownProjects.size(); i++) {
            if (Objects.equals(yB.c(shownProjects.get(i), "sc_id"), scId)) {
                notifyItemChanged(i);
                return;
            }
        }
        // Fallback for safety (e.g. if filtered out)
        notifyDataSetChanged();
    }

    public ProjectsAdapter(ProjectsFragment projectsFragment, List<HashMap<String, Object>> allProjects) {
        this.projectsFragment = projectsFragment;
        activity = projectsFragment.requireActivity();
        this.allProjects = allProjects;
        preference = new DB(activity, "project");

    }

    public void setAllProjects(List<HashMap<String, Object>> projects) {
        allProjects = projects;
    }

    public void filterData(String query) {
        List<HashMap<String, Object>> newProjects = query.isEmpty() ? allProjects : new ArrayList<>();
        if (!query.isEmpty()) {
            for (HashMap<String, Object> project : allProjects) {
                if (matchesQuery(project, query)) {
                    newProjects.add(project);
                }
            }
        }

        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return shownProjects.size();
            }

            @Override
            public int getNewListSize() {
                return newProjects.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                String oldScId = yB.c(shownProjects.get(oldItemPosition), "sc_id");
                String newScId = yB.c(newProjects.get(newItemPosition), "sc_id");
                return oldScId.equalsIgnoreCase(newScId);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                HashMap<String, Object> oldMap = shownProjects.get(oldItemPosition);
                HashMap<String, Object> newMap = newProjects.get(newItemPosition);
                for (String key : Arrays.asList("my_app_name", "my_ws_name", "sc_ver_name", "sc_ver_code", "my_sc_pkg_name")) {
                    if (!yB.c(oldMap, key).equals(yB.c(newMap, key))) {
                        return false;
                    }
                }
                boolean oldCustomIcon = yB.a(oldMap, "custom_icon");
                boolean newCustomIcon = yB.a(newMap, "custom_icon");
                return oldCustomIcon == newCustomIcon;
            }
        }, true);
        shownProjects = newProjects;
        result.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemCount() {
        return shownProjects.size();
    }

    private boolean matchesQuery(HashMap<String, Object> projectMap, String searchQuery) {
        searchQuery = searchQuery.toLowerCase();
        for (String key : Arrays.asList("sc_id", "my_ws_name", "my_app_name", "my_sc_pkg_name")) {
            if (yB.c(projectMap, key).toLowerCase().contains(searchQuery)) {
                return true;
            }
        }
        return false;
    }

    @DrawableRes
    public static <T> int getShapedBackgroundForList(List<T> list, int position) {
        if (list.size() == 1) {
            return R.drawable.project_item_shape_alone;
        } else if (position == 0) {
            return R.drawable.project_item_shape_top;
        } else if (position == list.size() - 1) {
            return R.drawable.project_item_shape_bottom;
        } else {
            return R.drawable.project_item_shape_middle;
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
        holder.itemView.setBackgroundResource(getShapedBackgroundForList(shownProjects, position));
        HashMap<String, Object> projectMap = shownProjects.get(position);
        String scId = yB.c(projectMap, "sc_id");

        // WHAT: Memory-only data normalization to avoid DB writes during scroll.
        // WHY: Fixing data (lC.b) in onBind is extremely heavy. We fix it in memory for the UI.
        // (عربي) إصلاح البيانات في الذاكرة فقط لتجنب الكتابة الثقيلة في قاعدة البيانات أثناء التمرير.
        if (yB.c(projectMap, "sc_ver_code").isEmpty()) {
            projectMap.put("sc_ver_code", "1");
            projectMap.put("sc_ver_name", "1.0");
        }
        if (yB.b(projectMap, "sketchware_ver") <= 0) {
            projectMap.put("sketchware_ver", 61);
        }

        // WHAT: Use cached Bitmap if available, otherwise read synchronously once.
        // WHY: setImageURI is a blocking I/O call. Pre-loading into LruCache respects G1 while being fast.
        // (عربي) استخدام أيقونة مخزنة في الذاكرة (Cache) لتجنب تقطيع الواجهة الناتج عن قراءة القرص.
        if (yB.a(projectMap, "custom_icon")) {
            android.graphics.Bitmap cached = ICON_CACHE.get(scId);
            if (cached != null) {
                holder.binding.imgIcon.setImageBitmap(cached);
            } else {
                String iconPath = wq.e() + File.separator + scId + File.separator + "icon.png";
                File iconFile = new File(iconPath);
                if (iconFile.exists()) {
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(iconPath);
                    if (bitmap != null) {
                        ICON_CACHE.put(scId, bitmap);
                        holder.binding.imgIcon.setImageBitmap(bitmap);
                    } else {
                        holder.binding.imgIcon.setImageResource(R.drawable.default_icon);
                    }
                } else {
                    holder.binding.imgIcon.setImageResource(R.drawable.default_icon);
                }
            }
        } else {
            holder.binding.imgIcon.setImageResource(R.drawable.default_icon);
        }

        if (isPinned(projectMap)) {
            holder.binding.imgPin.setVisibility(View.VISIBLE);
        } else {
            holder.binding.imgPin.setVisibility(View.INVISIBLE);
        }

        // WHAT: Using a pre-built string instead of repeated concatenation.
        // WHY: Reduces garbage collection pressure in hot scroll paths.
        // (عربي) بناء نص الإصدار مرة واحدة لتقليل استهلاك الذاكرة أثناء التمرير.
        String wsName = yB.c(projectMap, "my_ws_name");
        String verName = yB.c(projectMap, "sc_ver_name");
        String verCode = yB.c(projectMap, "sc_ver_code");
        
        StringBuilder title = new StringBuilder(wsName);
        if (!verName.isEmpty() || !verCode.isEmpty()) {
            title.append(" - ").append(verName).append(" (").append(verCode).append(")");
        }
        
        holder.binding.appName.setText(title.toString());
        holder.binding.projectName.setText(yB.c(projectMap, "my_app_name"));
        holder.binding.packageName.setText(yB.c(projectMap, "my_sc_pkg_name"));
        holder.binding.tvPublished.setVisibility(View.VISIBLE);
        holder.binding.tvPublished.setText(scId);
        holder.itemView.setTag("custom");

        // WHAT: Click listeners are handled by the holder to avoid repeated lambda allocations.
        // (عربي) إدارة مستمعي النقر داخل ViewHolder لتقليل إنشاء الكائنات المتكرر.
        holder.bindListeners(projectMap);
    }

    @NonNull
    @Override
    public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        MyprojectsItemBinding binding = MyprojectsItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ProjectViewHolder(binding);
    }

    private void deleteProject(HashMap<String, Object> projectMap, int position) {
        LoadingDialog progressDialog = new LoadingDialog(activity);
        progressDialog.show();

        String scId = yB.c(projectMap, "sc_id");
        new Thread(() -> {
            lC.a(activity, scId);
            activity.runOnUiThread(() -> {
                progressDialog.dismiss();
                shownProjects.remove(position);
                notifyDataSetChanged();
                allProjects.remove(projectMap);
            });
        }).start();
    }

    private void toProjectSettingOrRequestPermission(HashMap<String, Object> project, int index) {
        Intent intent = new Intent(activity, MyProjectSettingActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", yB.c(project, "sc_id"));
        intent.putExtra("is_update", true);
        intent.putExtra("index", index);
        projectsFragment.openProjectSettings.launch(intent);
    }

    private void showProjectSettingDialog(HashMap<String, Object> project) {
        new ProjectSettingsDialog(activity, yB.c(project, "sc_id")).show();
    }

    private void backupProject(HashMap<String, Object> project) {
        String scId = yB.c(project, "sc_id");
        String appName = yB.c(project, "my_ws_name");
        new BackupRestoreManager(activity).backup(scId, appName);
    }

    private void toExportProjectActivity(HashMap<String, Object> project) {
        Intent intent = new Intent(activity, ExportProjectActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", yB.c(project, "sc_id"));
        activity.startActivity(intent);
    }

    private void changePinState(HashMap<String, Object> projectMap) {
        if (isPinned(projectMap)) {
            preference.a("pinnedProject", "-1", true);
        } else {
            preference.a("pinnedProject", yB.c(projectMap, "sc_id"), true);
        }
        projectsFragment.refreshProjectsList();
    }

    private boolean isPinned(HashMap<String, Object> projectMap) {
        return Objects.equals(yB.c(projectMap, "sc_id"), preference.a("pinnedProject", "-1"));
    }

    private void showProjectOptionsBottomSheet(HashMap<String, Object> projectMap, int position) {
        // WHAT: optionsSheetPrewarm - Background loading of apps list and icons.
        // WHY: Pre-fills caches so that if the user clicks "Change Icon", the UI is instant.
        // (عربي) تسخين مسبق: جلب قائمة التطبيقات والأيقونات في الخلفية عند فتح الخيارات.
        InstalledAppsRepository.load(activity, apps -> {
            List<String> pkgs = new ArrayList<>();
            for (InstalledAppsRepository.App a : apps) pkgs.add(a.packageName);
            AppIconLoader.get().prefetch(activity, pkgs, (int) (96 * activity.getResources().getDisplayMetrics().density));
        });

        BottomSheetDialog projectOptionsBSD = new BottomSheetDialog(activity);
        BottomSheetProjectOptionsBinding binding = BottomSheetProjectOptionsBinding.inflate(LayoutInflater.from(activity));
        projectOptionsBSD.setContentView(binding.getRoot());

        String projectTitle = yB.c(projectMap, "my_ws_name");
        binding.title.setText(projectTitle);
        binding.tvProjectId.setText(yB.c(projectMap, "sc_id"));

        GitHubManager ghManager = GitHubManager.getInstance(activity);
        if (ghManager.isSignedIn()) {
            binding.githubMainText.setText(ghManager.getUserLogin());
            binding.githubSubText.setText(activity.getString(R.string.github_upload_project_title, projectTitle));
            if (ghManager.getUserAvatar() != null) {
                Glide.with(activity)
                        .load(ghManager.getUserAvatar())
                        .circleCrop()
                        .placeholder(R.drawable.ic_github_brand)
                        .into(binding.githubIcon);
            }
        } else {
            binding.githubMainText.setText(activity.getString(R.string.github_upload_project_title, projectTitle));
            binding.githubSubText.setText(R.string.github_upload_sign_in_hint);
            binding.githubIcon.setImageResource(R.drawable.ic_github_brand);
        }

        binding.githubAuthCard.setOnClickListener(v -> {
            if (!ghManager.isSignedIn()) {
                projectOptionsBSD.dismiss();
                if (activity instanceof FragmentActivity) {
                    GitHubSignInSheet.newInstance(projectTitle)
                            .show(((FragmentActivity) activity).getSupportFragmentManager(), "GitHubSignIn");
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS) 
                            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(activity, 
                                new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
                    }
                }

                String scId = yB.c(projectMap, "sc_id");
                File projectRoot = new File(wq.d(scId));

                projectOptionsBSD.dismiss();
                ProjectUploadBottomSheet.newInstance(scId, projectTitle, projectRoot.getAbsolutePath())
                        .show(((FragmentActivity) activity).getSupportFragmentManager(), "UploadStudio");
            }
        });

        binding.projectSettings.setOnClickListener(v -> {
            toProjectSettingOrRequestPermission(projectMap, position);
            projectOptionsBSD.dismiss();
        });

        binding.changeAppIcon.setOnClickListener(v -> {
            projectOptionsBSD.dismiss();
            if (activity instanceof FragmentActivity) {
                String scId = yB.c(projectMap, "sc_id");
                pro.sketchware.activities.main.fragments.projects.AppIconPickerSheet.newInstance(scId, projectTitle)
                        .show(((FragmentActivity) activity).getSupportFragmentManager(), "AppIconPicker");
            }
        });

        binding.projectBackup.setOnClickListener(v -> {
            backupProject(projectMap);
            projectOptionsBSD.dismiss();
        });

        binding.pinProject.setOnClickListener(v -> {
            changePinState(projectMap);
            projectOptionsBSD.dismiss();
        });

        binding.exportSign.setOnClickListener(v -> {
            toExportProjectActivity(projectMap);
            projectOptionsBSD.dismiss();
        });

        binding.projectConfig.setOnClickListener(v -> {
            showProjectSettingDialog(projectMap);
            projectOptionsBSD.dismiss();
        });

        binding.projectDelete.setOnClickListener(v -> {
            projectOptionsBSD.dismiss();
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(activity);
            dialog.setIcon(R.drawable.icon_delete);
            dialog.setTitle(Helper.getResString(R.string.delete_project_dialog_title));
            dialog.setMessage(Helper.getResString(R.string.delete_project_dialog_message).replace("%1$s", yB.c(projectMap, "my_app_name")));
            dialog.setPositiveButton(Helper.getResString(R.string.common_word_delete), (v1, which) -> {
                deleteProject(projectMap, position);
                v1.dismiss();
            });
            dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
            dialog.show();
        });

        if (isPinned(projectMap)) {
            binding.pinIcon.setImageResource(R.drawable.ic_mtrl_unpin);
            binding.pinText.setText("Unpin project");
        } else {
            binding.pinIcon.setImageResource(R.drawable.ic_mtrl_pin);
            binding.pinText.setText("Pin project");
        }

        projectOptionsBSD.show();
    }

    public class ProjectViewHolder extends RecyclerView.ViewHolder {
        final MyprojectsItemBinding binding;

        ProjectViewHolder(MyprojectsItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bindListeners(HashMap<String, Object> projectMap) {
            String scId = yB.c(projectMap, "sc_id");
            
            binding.getRoot().setOnClickListener(v -> {
                if (!mB.a()) {
                    projectsFragment.toDesignActivity(scId);
                }
            });

            binding.expand.setOnClickListener(v -> {
                mB.a(v);
                int currentPosition = getAbsoluteAdapterPosition();
                if (currentPosition != RecyclerView.NO_POSITION) {
                    showProjectOptionsBottomSheet(projectMap, currentPosition);
                }
            });

            binding.imgIcon.setOnClickListener(v -> 
                toProjectSettingOrRequestPermission(projectMap, getAbsoluteAdapterPosition())
            );

            binding.getRoot().setOnLongClickListener(v -> {
                showProjectOptionsBottomSheet(projectMap, getAbsoluteAdapterPosition());
                return true;
            });
        }
    }
}
