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
 * [R5-R8 State Maintenance] AppIconPickerSheet - Centralized state management.
 * (عربي) منتقي أيقونة التطبيق - إعادة هندسة الحالة: كتّاب مركزيون، مصدر وحيد للحقيقة، وحماية النقر المزدوج.
 */
public class AppIconPickerSheet extends BottomSheetDialogFragment {

    private SheetAppIconPickerBinding binding;
    private String scId;
    private String projectTitle;
    private AppsAdapter adapter;
    private int tilePx = 160;

    // R5: Single Source of Truth (SSOT) Model
    private enum ContentState { LOADING, LIST, EMPTY, NO_RESULTS }
    private String selectedPkg = null;
    private volatile boolean isApplying = false;
    
    private final ExecutorService applyExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService filterExecutor = Executors.newSingleThreadExecutor();

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
        tilePx = (int) (96 * getResources().getDisplayMetrics().density);
        binding.projectNamePreview.setText(projectTitle);
        
        setupGrid();
        setupListeners();
        
        loadCurrentIcon();
        loadApps();
    }

    // --- Monopoly Writers (Single Source of Truth) ---

    /**
     * WHAT: applyContentState - The ONLY writer for grid/loading/empty visibility.
     * (عربي) الكاتب الوحيد لحالة المحتوى: يدير ظهور الشبكة والتحميل والفراغ بدقة.
     */
    private void applyContentState(ContentState state) {
        if (binding == null) return;
        binding.loadingState.setVisibility(state == ContentState.LOADING ? View.VISIBLE : View.GONE);
        binding.emptyState.setVisibility(state == ContentState.EMPTY || state == ContentState.NO_RESULTS ? View.VISIBLE : View.GONE);
        binding.installedAppsGrid.setVisibility(state == ContentState.LIST ? View.VISIBLE : View.GONE);
    }

    /**
     * WHAT: applySelection - Updates the SSOT and notifies adapter of changes.
     * (عربي) الكاتب الوحيد للتحديد: يحدّث مصدر الحقيقة ويخطر المحول بالمواضع المتغيرة فقط.
     */
    private void applySelection(String pkg) {
        String old = selectedPkg;
        selectedPkg = pkg;
        if (adapter != null) {
            adapter.notifyItemChangedForPackage(old);
            adapter.notifyItemChangedForPackage(selectedPkg);
        }
    }

    /**
     * WHAT: applyPreview - Centralized renderer for the project icon preview.
     * (عربي) الكاتب الوحيد للمعاينة: المسؤول الوحيد عن تحديث أيقونة المعاينة مع الأنيميشن.
     */
    private void applyPreview(@Nullable Bitmap b) {
        if (binding == null) return;
        if (b == null) {
            binding.iconPreview.setImageResource(R.drawable.default_icon);
            return;
        }
        binding.iconPreviewFrame.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100)
                .withEndAction(() -> {
                    if (binding != null) {
                        binding.iconPreview.setImageBitmap(b);
                        binding.iconPreviewFrame.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    }
                }).start();
    }

    /**
     * WHAT: setApplying - Centralized blocker for concurrent apply tasks.
     * (عربي) حارس التنفيذ: يمنع العمليات المتوازية ويحمي الواجهة أثناء الحفظ.
     */
    private void setApplying(boolean applying) {
        isApplying = applying;
        if (binding == null) return;
        binding.getRoot().setAlpha(applying ? 0.7f : 1.0f);
    }

    // --- Business Logic ---

    private void loadApps() {
        applyContentState(ContentState.LOADING);
        InstalledAppsRepository.load(requireContext(), apps -> {
            if (binding == null || !isAdded()) return;
            applyContentState(apps.isEmpty() ? ContentState.EMPTY : ContentState.LIST);
            adapter.setApps(apps);
            List<String> pkgs = new ArrayList<>();
            for (InstalledAppsRepository.App a : apps) pkgs.add(a.packageName);
            AppIconLoader.get().prefetch(requireContext(), pkgs, tilePx);
        });
    }

    private void loadCurrentIcon() {
        Bitmap hit = projectIconPreviewCache.get(scId);
        if (hit != null) {
            applyPreview(hit);
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
                act.runOnUiThread(() -> { if (binding != null) applyPreview(b); });
            });
        } else {
            applyPreview(null);
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
                        applyContentState(out.isEmpty() && !q.isEmpty() ? ContentState.NO_RESULTS : (out.isEmpty() ? ContentState.EMPTY : ContentState.LIST));
                        adapter.setApps(out);
                    }
                });
            }
        });
    }

    private void applyPickedIconToProject(@Nullable InstalledAppsRepository.App app) {
        if (isApplying) return;
        setApplying(true);
        final Activity act = getActivity();
        if (act == null || !isAdded()) { setApplying(false); return; }
        
        final String pkg = (app != null) ? app.packageName : null;
        applySelection(pkg);

        if (pkg != null) {
            Bitmap preview = AppIconLoader.get().cached(pkg);
            if (preview != null) applyPreview(preview);
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
                
                HashMap<String, Object> map = lC.b(scId);
                if (map != null) { map.put("custom_icon", high != null); lC.b(scId, map); }

                final Bitmap finalBmp = high;
                act.runOnUiThread(() -> {
                    if (!isAdded() || binding == null) { setApplying(false); return; }
                    
                    if (finalBmp != null) {
                        applyPreview(finalBmp);
                        projectIconPreviewCache.put(scId, finalBmp);
                    } else {
                        applyPreview(null);
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
                        setApplying(false);
                    }, 180);
                });
            } catch (Exception e) {
                act.runOnUiThread(() -> {
                    setApplying(false);
                    if (isAdded()) Toast.makeText(act, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
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

        AppsAdapter(OnAppSelected l) { listener = l; }

        void setApps(List<InstalledAppsRepository.App> list) {
            apps = list; notifyDataSetChanged();
        }

        void notifyItemChangedForPackage(String pkg) {
            if (pkg == null) return;
            for (int i = 0; i < apps.size(); i++) {
                if (pkg.equals(apps.get(i).packageName)) {
                    notifyItemChanged(i);
                    return;
                }
            }
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

            boolean sel = (selectedPkg != null && selectedPkg.equals(app.packageName));
            holder.binding.tileContainer.setActivated(sel);
            holder.binding.selectionBadge.setVisibility(sel ? View.VISIBLE : View.GONE);

            View.OnClickListener cl = v -> {
                if (isApplying) return;
                
                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()).start();
                
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
