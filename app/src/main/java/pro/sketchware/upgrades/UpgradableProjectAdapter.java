package pro.sketchware.upgrades;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import a.a.a.wq;
import pro.sketchware.R;
import pro.sketchware.databinding.MyprojectsItemBinding;

/**
 * Adapter for displaying projects with upgrade status.
 * It transforms standard project data into "rich cards" with status indicators and action buttons.
 * (عربي) محول لعرض المشاريع مع حالة الترقية.
 * يحول بيانات المشاريع العادية إلى "بطاقات غنية" مع مؤشرات الحالة وأزرار الإجراءات.
 */
public class UpgradableProjectAdapter extends RecyclerView.Adapter<UpgradableProjectAdapter.ViewHolder> {

    private final List<UpgradeReport> reports = new ArrayList<>();
    private final OnUpgradeClickListener upgradeClickListener;
    private final OnReportClickListener reportClickListener;
    private final Set<String> animatedIds = new HashSet<>();

    private static final android.util.LruCache<String, android.graphics.Bitmap> ICON_CACHE = 
            new android.util.LruCache<>(30);

    public interface OnUpgradeClickListener {
        void onUpgradeClick(UpgradeReport report);
    }

    public interface OnReportClickListener {
        void onReportClick(UpgradeReport report);
    }

    public OnUpgradeClickListener getUpgradeClickListener() {
        return upgradeClickListener;
    }

    /**
     * Smart Upgrade Center Adapter - Displays projects with rich metadata and upgrade status.
     * (عربي) محول مركز الترقيات الذكي - يعرض المشاريع مع بيانات وصفية غنية وحالة الترقية.
     */
    public UpgradableProjectAdapter(OnUpgradeClickListener upgradeClickListener, OnReportClickListener reportClickListener) {
        this.upgradeClickListener = upgradeClickListener;
        this.reportClickListener = reportClickListener;
    }

    public void setReports(List<UpgradeReport> newReports) {
        reports.clear();
        reports.addAll(newReports);
        animatedIds.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        MyprojectsItemBinding binding = MyprojectsItemBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UpgradeReport report = reports.get(position);
        holder.bind(report, position);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        // Reset state for recycled views to prevent invisible items during scroll
        holder.itemView.animate().cancel();
        holder.itemView.setAlpha(1f);
        holder.itemView.setTranslationY(0f);
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final MyprojectsItemBinding binding;

        ViewHolder(MyprojectsItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }


        /**
         * WHAT: bind - Populates the project card with data and sets up listeners.
         * WHY: Entrance animations are tracked via animatedIds to ensure they only run once.
         * (عربي) ربط البيانات - ملء بطاقة المشروع بالبيانات وإعداد مستمعي النقرات.
         * يتم تتبع أنيميشن الدخول لضمان تشغيله مرة واحدة فقط لكل عنصر.
         */
        void bind(UpgradeReport report, int position) {
            binding.getRoot().animate().cancel();

            binding.projectName.setText(report.appName);
            
            // G7: appName row details
            String lastMod = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(new java.util.Date(report.lastModified));
            binding.appName.setText(binding.getRoot().getContext().getString(R.string.project_details_sub, 
                    report.scId, report.age, lastMod));
            
            boolean upToDate = report.isUpToDate();
            
            // G7: packageName row technical details
            String androidX = report.androidxOn ? "AndroidX ON" : "AndroidX OFF";
            binding.packageName.setText(binding.getRoot().getContext().getString(R.string.project_tech_details,
                    report.packageName, report.storedVer, report.latestVer, androidX, report.minSdk, report.libsCount));
            
            // G7: packageName color logic
            int color = upToDate ? 
                    binding.getRoot().getContext().getColor(R.color.color_primary) : 
                    binding.getRoot().getContext().getColor(R.color.scolor_red_01);
            binding.packageName.setTextColor(color);
            
            // G5: Hide imgPin
            binding.imgPin.setVisibility(View.GONE);

            // Icon Literal logic from ProjectsAdapter (B2 Fix)
            if (report.hasCustomIcon) {
                android.graphics.Bitmap cached = ICON_CACHE.get(report.scId);
                if (cached != null) {
                    binding.imgIcon.setImageBitmap(cached);
                } else {
                    String iconPath = wq.e() + File.separator + report.scId + File.separator + "icon.png";
                    File iconFile = new File(iconPath);
                    if (iconFile.exists()) {
                        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(iconPath);
                        if (bitmap != null) {
                            ICON_CACHE.put(report.scId, bitmap);
                            binding.imgIcon.setImageBitmap(bitmap);
                        } else {
                            binding.imgIcon.setImageResource(R.drawable.default_icon);
                        }
                    } else {
                        binding.imgIcon.setImageResource(R.drawable.default_icon);
                    }
                }
            } else {
                binding.imgIcon.setImageResource(R.drawable.default_icon);
            }
            binding.tvPublished.setText(report.scId);

            // G7: Animation - scale on press
            binding.getRoot().setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).start();
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP || 
                           event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                }
                return false;
            });

            // Rich Card Listeners
            binding.getRoot().setOnClickListener(v -> {
                if (reportClickListener != null) {
                    reportClickListener.onReportClick(report);
                }
            });

            // Button "Upgrade Resources"
            binding.expand.setImageResource(R.drawable.ic_tab_upgrades);
            binding.expand.setVisibility(upToDate ? View.GONE : View.VISIBLE);
            binding.expand.setOnClickListener(v -> {
                if (upgradeClickListener != null) {
                    upgradeClickListener.onUpgradeClick(report);
                }
            });

            // G7: Entrance Animation - scroll-safe logic
            if (animatedIds.contains(report.scId)) {
                binding.getRoot().setAlpha(1f);
                binding.getRoot().setTranslationY(0f);
            } else {
                animatedIds.add(report.scId);
                binding.getRoot().setAlpha(0f);
                binding.getRoot().setTranslationY(100f);
                binding.getRoot().animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(220)
                        .setStartDelay(Math.min(position * 40L, 400L))
                        .start();
            }
        }
    }
}
