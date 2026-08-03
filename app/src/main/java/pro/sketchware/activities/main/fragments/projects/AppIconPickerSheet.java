package pro.sketchware.activities.main.fragments.projects;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import pro.sketchware.R;
import pro.sketchware.databinding.ItemInstalledAppBinding;
import pro.sketchware.databinding.SheetAppIconPickerBinding;

/**
 * WHAT: UI-only picker. List from InstalledAppsRepository, icons from AppIconLoader
 *      (prefetch-warmed), tap applies instantly from cache. Whole tile is tappable.
 * (عربي) واجهة فقط: القائمة من المستودع، الأيقونات مسخّنة مسبقاً، والضغط على أي
 *      مكان في البطاقة يطبّق الأيقونة فوراً من الكاش.
 */
public class AppIconPickerSheet extends BottomSheetDialogFragment {

    private SheetAppIconPickerBinding binding;
    private String scId;
    private String projectTitle;
    private AppsAdapter adapter;
    private volatile boolean isApplying = false;
    private final ExecutorService applyExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService filterExecutor = Executors.newSingleThreadExecutor();
    private int tilePx = 160;

    /**
     * WHAT: projectIconPreviewCache - Static cache for project icons.
     * WHY: Makes reopening the sheet instantaneous for the same project.
     * (عربي) كاش أيقونة المشروع: يضمن فتح النافذة فوراً لنفس المشروع دون إعادة المعالجة.
     */
    private static final android.util.LruCache<String, Bitmap> projectIconPreviewCache = 
            new android.util.LruCache<>(20);

    public static AppIconPickerSheet newInstance(String scId, String projectTitle) {
        AppIconPickerSheet f = new AppIconPickerSheet();
        Bundle args = new Bundle();
        args.putString("sc_id", scId);
        args.putString("title", projectTitle);
        f.setArguments(args);
        return f;
    }

    @Override public void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        if (getArguments() != null) {
            scId = getArguments().getString("sc_id");
            projectTitle = getArguments().getString("title");
        }
    }

    @NonNull @Override
    public Dialog onCreateDialog(@Nullable Bundle s) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(s);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            FrameLayout sheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<FrameLayout> b = BottomSheetBehavior.from(sheet);
                b.setState(BottomSheetBehavior.STATE_EXPANDED);
                b.setSkipCollapsed(true);
            }
        });
        Window w = dialog.getWindow();
        if (w != null) w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        return dialog;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle s) {
        binding = SheetAppIconPickerBinding.inflate(i, c, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        // WHAT: loadingStateGuard - Ensure states are reset before loading.
        // (عربي) حارس الحالة: تصفير حالات الفراغ قبل البدء بالتحميل لضمان عدم التداخل.
        binding.emptyState.setVisibility(View.GONE);
        
        tilePx = (int) (96 * getResources().getDisplayMetrics().density);
        binding.projectNamePreview.setText(projectTitle);
        loadCurrentIcon();
        setupGrid();
        setupListeners();
        loadApps();
    }

    private void loadApps() {
        binding.loadingState.setVisibility(View.VISIBLE);
        binding.emptyState.setVisibility(View.GONE);
        binding.installedAppsGrid.setVisibility(View.GONE);

        InstalledAppsRepository.load(requireContext(), apps -> {
            if (binding == null || !isAdded()) return;
            binding.loadingState.setVisibility(View.GONE);
            binding.emptyState.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
            binding.installedAppsGrid.setVisibility(apps.isEmpty() ? View.GONE : View.VISIBLE);
            adapter.setApps(apps);
            // WHAT: prefetch warm-up - warm the cache for the whole list off-main.
            List<String> pkgs = new ArrayList<>();
            for (InstalledAppsRepository.App a : apps) pkgs.add(a.packageName);
            AppIconLoader.get().prefetch(requireContext(), pkgs, tilePx);
        });
    }

    private void loadCurrentIcon() {
        // WHAT: projectIconPreviewCache hit check.
        // (عربي) فحص الكاش: استخدام النسخة المخزنة لأيقونة المشروع إن وجدت.
        Bitmap hit = projectIconPreviewCache.get(scId);
        if (hit != null) {
            binding.iconPreview.setImageBitmap(hit);
            return;
        }

        String path = wq.e() + File.separator + scId + File.separator + "icon.png";
        File file = new File(path);
        if (file.exists()) {
            applyExecutor.execute(() -> {
                Bitmap b = decodeFile(path, 256);
                Activity act = getActivity();
                if (act == null || !isAdded() || b == null) return;
                
                projectIconPreviewCache.put(scId, b);
                act.runOnUiThread(() -> { if (binding != null) binding.iconPreview.setImageBitmap(b); });
            });
        } else {
            binding.iconPreview.setImageResource(R.drawable.default_icon);
        }
    }

    private void setupGrid() {
        adapter = new AppsAdapter(app -> applyPickedIconToProject(app));
        binding.installedAppsGrid.setLayoutManager(new GridLayoutManager(getContext(), 4));
        binding.installedAppsGrid.setHasFixedSize(true);
        binding.installedAppsGrid.setItemViewCacheSize(24);
        binding.installedAppsGrid.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.btnPickImage.setOnClickListener(v -> loadApps());
        binding.btnReset.setOnClickListener(v -> applyPickedIconToProject(null));
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { filterApps(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void filterApps(String q) {
        if (binding == null || !isAdded()) return;
        // WHAT: Background Filtering - Offload string search to executor.
        // WHY: Smooth typing experience even with 500+ apps.
        // (عربي) بحث خلفي: تنفيذ البحث في خيط مستقل لضمان سلاسة الكتابة.
        filterExecutor.execute(() -> {
            List<InstalledAppsRepository.App> all = InstalledAppsRepository.cached();
            if (all == null) return;
            List<InstalledAppsRepository.App> out = new ArrayList<>();
            String lq = q.toLowerCase();
            for (InstalledAppsRepository.App a : all) if (a.label.toLowerCase().contains(lq)) out.add(a);
            
            Activity act = getActivity();
            if (act != null) {
                act.runOnUiThread(() -> {
                    if (binding != null && isAdded()) {
                        binding.emptyState.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
                        binding.installedAppsGrid.setVisibility(out.isEmpty() ? View.GONE : View.VISIBLE);
                        adapter.setApps(out);
                    }
                });
            }
        });
    }

    /**
     * WHAT: instantTapApply - preview + file from cache; high-res decode off-main.
     * (عربي) تطبيق فوري: المعاينة والملف من الكاش، والفك عالي الدقة في الخلفية.
     */
    private void applyPickedIconToProject(@Nullable InstalledAppsRepository.App app) {
        if (isApplying) return;
        isApplying = true;
        final Activity act = getActivity();
        if (act == null || !isAdded()) { isApplying = false; return; }
        final String pkg = (app != null) ? app.packageName : null;

        if (pkg != null) {
            Bitmap preview = AppIconLoader.get().cached(pkg);
            if (preview != null) updatePreview(preview);
        }

        applyExecutor.execute(() -> {
            try {
                Bitmap high = null;
                if (pkg != null) high = AppIconLoader.get().fetchHighRes(act, pkg, 512);

                File dir = new File(wq.e() + File.separator + scId);
                if (!dir.exists()) dir.mkdirs();
                File iconFile = new File(dir, "icon.png");
                if (high != null) {
                    try (FileOutputStream out = new FileOutputStream(iconFile)) {
                        high.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                } else if (iconFile.exists()) {
                    iconFile.delete();
                }
                
                // WHAT: Heavy Data Write - update project metadata off-main.
                HashMap<String, Object> map = lC.b(scId);
                if (map != null) { map.put("custom_icon", high != null); lC.b(scId, map); }

                final Bitmap preview2 = high;
                act.runOnUiThread(() -> {
                    if (!isAdded() || binding == null) { isApplying = false; return; }
                    if (preview2 != null) {
                        updatePreview(preview2);
                        projectIconPreviewCache.put(scId, preview2);
                    } else {
                        binding.iconPreview.setImageResource(R.drawable.default_icon);
                        projectIconPreviewCache.remove(scId);
                    }
                    
                    com.besome.sketch.adapters.ProjectsAdapter.invalidateIconCache(scId);
                    Toast.makeText(act, R.string.app_icon_changed, Toast.LENGTH_SHORT).show();
                    
                    binding.getRoot().postDelayed(() -> {
                        if (!isAdded()) return;
                        dismissAllowingStateLoss();
                        Activity a = getActivity();
                        if (a instanceof pro.sketchware.activities.main.activities.MainActivity) {
                            ((pro.sketchware.activities.main.activities.MainActivity) a).lightweightProjectIconRefresh(scId);
                        }
                        isApplying = false;
                    }, 180);
                });
            } catch (Exception e) {
                act.runOnUiThread(() -> {
                    isApplying = false;
                    if (isAdded()) Toast.makeText(act, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updatePreview(Bitmap b) {
        if (binding == null) return;
        binding.iconPreviewFrame.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100)
                .withEndAction(() -> {
                    if (binding != null) {
                        binding.iconPreview.setImageBitmap(b);
                        binding.iconPreviewFrame.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    }
                }).start();
    }

    private Bitmap decodeFile(String path, int req) {
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(path, o);
        int s = 1;
        if (o.outHeight > req || o.outWidth > req) {
            int hh = o.outHeight / 2, hw = o.outWidth / 2;
            while ((hh / s) >= req && (hw / s) >= req) s *= 2;
        }
        o.inSampleSize = s;
        o.inJustDecodeBounds = false;
        return android.graphics.BitmapFactory.decodeFile(path, o);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        applyExecutor.shutdownNow();
        filterExecutor.shutdownNow();
        binding = null;
    }

    private interface OnAppSelected { void onAppSelected(InstalledAppsRepository.App app); }

    private class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.VH> {
        private List<InstalledAppsRepository.App> apps = new ArrayList<>();
        private final OnAppSelected listener;
        private int selectedPosition = -1;

        AppsAdapter(OnAppSelected l) { listener = l; }

        void setApps(List<InstalledAppsRepository.App> list) {
            apps = list; selectedPosition = -1; notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new VH(ItemInstalledAppBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            InstalledAppsRepository.App app = apps.get(position);
            holder.binding.label.setText(app.label);
            holder.binding.icon.setTag(app.packageName);

            Bitmap b = AppIconLoader.get().cached(app.packageName);
            if (b != null) {
                holder.binding.icon.setImageBitmap(b);
                holder.binding.icon.setAlpha(1f);
            } else {
                holder.binding.icon.setImageResource(R.drawable.default_icon);
                holder.binding.icon.setAlpha(0.35f);
                AppIconLoader.get().load(requireContext(), app.packageName, tilePx, bitmap -> {
                    if (bitmap != null && app.packageName.equals(holder.binding.icon.getTag())) {
                        holder.binding.icon.setImageBitmap(bitmap);
                        holder.binding.icon.setAlpha(1f);
                    }
                });
            }

            boolean sel = (selectedPosition == position);
            holder.binding.tileContainer.setActivated(sel);
            holder.binding.selectionBadge.setVisibility(sel ? View.VISIBLE : View.GONE);

            // WHAT: Tap-to-apply guard.
            // (عربي) حارس النقر: تجنب إعادة الربط غير الضرورية.
            View.OnClickListener cl = v -> {
                if (isApplying) return;
                int pos = holder.getBindingAdapterPosition();
                if (selectedPosition == pos) {
                    listener.onAppSelected(app);
                    return;
                }

                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()).start();
                
                int old = selectedPosition;
                selectedPosition = pos;
                if (old >= 0) notifyItemChanged(old);
                notifyItemChanged(selectedPosition);
                listener.onAppSelected(app);
            };
            holder.binding.tileContainer.setOnClickListener(cl);
            holder.binding.getRoot().setOnClickListener(cl);
        }

        @Override public int getItemCount() { return apps.size(); }

        class VH extends RecyclerView.ViewHolder {
            final ItemInstalledAppBinding binding;
            VH(ItemInstalledAppBinding b) { super(b.getRoot()); binding = b; }
        }
    }
}
