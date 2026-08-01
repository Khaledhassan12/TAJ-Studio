package pro.sketchware.activities.main.fragments.projects;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ProjectBean;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;
import pro.sketchware.R;
import pro.sketchware.databinding.ItemInstalledAppBinding;
import pro.sketchware.databinding.SheetAppIconPickerBinding;

/**
 * A "Premium" BottomSheet for picking a project icon from installed apps.
 * It follows the minty fresh theme and provides a live preview.
 * 
 * بوطم شيت "بريميوم" لاختيار أيقونة المشروع من التطبيقات المثبتة.
 * يتبع الثيم النعناعي ويوفر معاينة حية للأيقونة قبل التطبيق.
 */
public class AppIconPickerSheet extends BottomSheetDialogFragment {

    private SheetAppIconPickerBinding binding;
    private String scId;
    private String projectTitle;
    private Bitmap selectedBitmap;
    private AppsAdapter adapter;
    private final ExecutorService loaderExecutor = Executors.newSingleThreadExecutor();
    private List<AppInfo> allApps = new ArrayList<>();

    public static AppIconPickerSheet newInstance(String scId, String projectTitle) {
        AppIconPickerSheet fragment = new AppIconPickerSheet();
        Bundle args = new Bundle();
        args.putString("sc_id", scId);
        args.putString("title", projectTitle);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            scId = getArguments().getString("sc_id");
            projectTitle = getArguments().getString("title");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialogInterface;
            FrameLayout bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        
        Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetAppIconPickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.projectNamePreview.setText(projectTitle);
        loadCurrentIcon();

        setupGrid();
        setupListeners();
    }

    private void loadCurrentIcon() {
        // We load the current icon from the project's resource folder to show the user what they have now.
        // نقوم بتحميل الأيقونة الحالية من مجلد موارد المشروع لنعرض للمستخدم ما لديه الآن.
        String iconPath = wq.e() + File.separator + scId + File.separator + "icon.png";
        File file = new File(iconPath);
        if (file.exists()) {
            binding.iconPreview.setImageURI(Uri.fromFile(file));
        } else {
            binding.iconPreview.setImageResource(R.drawable.default_icon);
        }
    }

    private void setupGrid() {
        adapter = new AppsAdapter(appInfo -> {
            selectedBitmap = appInfo.icon;
            updatePreview(selectedBitmap);
            binding.btnApply.setEnabled(true);
        });
        binding.installedAppsGrid.setLayoutManager(new GridLayoutManager(getContext(), 4));
        binding.installedAppsGrid.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.btnPickImage.setOnClickListener(v -> loadInstalledApps());
        
        binding.btnReset.setOnClickListener(v -> {
            selectedBitmap = null;
            binding.iconPreview.setImageResource(R.drawable.default_icon);
            binding.btnApply.setEnabled(true);
        });

        binding.btnCancel.setOnClickListener(v -> dismiss());

        binding.btnApply.setOnClickListener(v -> applyPickedIconToProject());

        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void updatePreview(Bitmap bitmap) {
        // Animation for live preview: a small pop to make it feel responsive.
        // أنيميشن للمعاينة الحية: "هزة" خفيفة لتجعل الواجهة تبدو متفاعلة.
        binding.iconPreviewFrame.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction(() -> {
            binding.iconPreview.setImageBitmap(bitmap);
            binding.iconPreviewFrame.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
        }).start();
    }

    private void loadInstalledApps() {
        // We load icons off the main thread to keep the UI smooth, as PackageManager can be slow.
        // نحمل الأيقونات بعيداً عن الخيط الرئيسي للحفاظ على سلاسة الواجهة، لأن PackageManager قد يكون بطيئاً.
        binding.loadingState.setVisibility(View.VISIBLE);
        binding.emptyState.setVisibility(View.GONE);
        binding.btnPickImage.setEnabled(false);

        loaderExecutor.execute(() -> {
            PackageManager pm = requireContext().getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<AppInfo> apps = new ArrayList<>();

            for (ApplicationInfo app : packages) {
                if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                    try {
                        Drawable icon = pm.getApplicationIcon(app);
                        Bitmap bitmap = drawableToBitmap(icon);
                        apps.add(new AppInfo(pm.getApplicationLabel(app).toString(), bitmap, app.packageName));
                    } catch (Exception ignored) {}
                }
            }

            apps.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
            
            requireActivity().runOnUiThread(() -> {
                allApps = apps;
                adapter.setApps(apps);
                binding.loadingState.setVisibility(View.GONE);
                binding.btnPickImage.setEnabled(true);
            });
        });
    }

    private void filterApps(String query) {
        List<AppInfo> filtered = new ArrayList<>();
        for (AppInfo app : allApps) {
            if (app.label.toLowerCase().contains(query.toLowerCase())) {
                filtered.add(app);
            }
        }
        adapter.setApps(filtered);
    }

    private void applyPickedIconToProject() {
        // The core logic: writing the Bitmap to icon.png and updating the project metadata.
        // المنطق الأساسي: كتابة الـ Bitmap في ملف icon.png وتحديث بيانات المشروع الوصفية.
        String iconDir = wq.e() + File.separator + scId;
        File dir = new File(iconDir);
        if (!dir.exists()) dir.mkdirs();

        File iconFile = new File(dir, "icon.png");

        try {
            if (selectedBitmap != null) {
                try (FileOutputStream out = new FileOutputStream(iconFile)) {
                    selectedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
            } else {
                if (iconFile.exists()) iconFile.delete();
            }

            // Update metadata
            HashMap<String, Object> projectMap = lC.b(scId);
            if (projectMap != null) {
                projectMap.put("custom_icon", selectedBitmap != null);
                lC.b(scId, projectMap);
            }

            Toast.makeText(getContext(), R.string.app_icon_changed, Toast.LENGTH_SHORT).show();
            dismiss();
            
            // We assume the parent listener or ProjectsFragment will handle the refresh.
            // نفترض أن المستمع الأب أو ProjectsFragment سيتولى تحديث القائمة.
            if (getActivity() instanceof pro.sketchware.activities.main.activities.MainActivity) {
                ((pro.sketchware.activities.main.activities.MainActivity) getActivity()).n();
            }

        } catch (Exception e) {
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        loaderExecutor.shutdownNow();
        binding = null;
    }

    private static class AppInfo {
        String label;
        Bitmap icon;
        String packageName;

        AppInfo(String label, Bitmap icon, String packageName) {
            this.label = label;
            this.icon = icon;
            this.packageName = packageName;
        }
    }

    private static class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.ViewHolder> {
        private List<AppInfo> apps = new ArrayList<>();
        private final OnAppSelectedListener listener;

        AppsAdapter(OnAppSelectedListener listener) {
            this.listener = listener;
        }

        void setApps(List<AppInfo> apps) {
            this.apps = apps;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemInstalledAppBinding binding = ItemInstalledAppBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppInfo app = apps.get(position);
            holder.binding.label.setText(app.label);
            holder.binding.icon.setImageBitmap(app.icon);
            holder.itemView.setOnClickListener(v -> listener.onAppSelected(app));
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ItemInstalledAppBinding binding;

            ViewHolder(ItemInstalledAppBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }

        interface OnAppSelectedListener {
            void onAppSelected(AppInfo appInfo);
        }
    }
}