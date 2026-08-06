package pro.sketchware.upgrades;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.wq;
import pro.sketchware.R;
import pro.sketchware.databinding.MyprojectsItemBinding;

/**
 * Adapter for displaying projects with upgrade status.
 * Optimized for performance: Async thumbnail loading, DiffUtil updates, and caching.
 * (عربي) محول لعرض المشاريع مع حالة الترقية.
 * مصمم للأداء: تحميل الأيقونات في الخلفية، تحديثات DiffUtil، وتخزين مؤقت.
 */
public class UpgradableProjectAdapter extends RecyclerView.Adapter<UpgradableProjectAdapter.ViewHolder> {

    private final List<UpgradeReport> reports = new ArrayList<>();
    private final OnUpgradeClickListener upgradeClickListener;
    private final OnReportClickListener reportClickListener;
    private final Set<String> animatedIds = new HashSet<>();

    private static final android.util.LruCache<String, Bitmap> ICON_CACHE = 
            new android.util.LruCache<>(50);

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(1, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("UpgradesIconLoader");
        return t;
    });

    public interface OnUpgradeClickListener {
        void onUpgradeClick(UpgradeReport report);
    }

    public interface OnReportClickListener {
        void onReportClick(UpgradeReport report);
    }

    public UpgradableProjectAdapter(OnUpgradeClickListener upgradeClickListener, OnReportClickListener reportClickListener) {
        this.upgradeClickListener = upgradeClickListener;
        this.reportClickListener = reportClickListener;
        setHasStableIds(true);
    }

    /**
     * WHAT: setReports - Updates the data set using DiffUtil for smooth transitions.
     * WHY: Avoids notifyDataSetChanged() storm to prevent UI flicker and redundant binds.
     * (عربي) تعيين التقارير - تحديث البيانات باستخدام DiffUtil لانتقالات سلسة.
     * يتجنب عاصفة notifyDataSetChanged() لمنع وميض الواجهة والربط غير الضروري.
     */
    public void setReports(List<UpgradeReport> newReports) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return reports.size();
            }

            @Override
            public int getNewListSize() {
                return newReports.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return reports.get(oldItemPosition).scId.equals(newReports.get(newItemPosition).scId);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                UpgradeReport oldR = reports.get(oldItemPosition);
                UpgradeReport newR = newReports.get(newItemPosition);
                return oldR.isUpToDate() == newR.isUpToDate() &&
                        oldR.getUpgradableCount() == newR.getUpgradableCount() &&
                        oldR.age.equals(newR.age) &&
                        oldR.hasCustomIcon == newR.hasCustomIcon &&
                        oldR.appName.equals(newR.appName);
            }
        });
        reports.clear();
        reports.addAll(newReports);
        animatedIds.clear();
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public long getItemId(int position) {
        try {
            return Long.parseLong(reports.get(position).scId);
        } catch (Exception e) {
            return reports.get(position).scId.hashCode();
        }
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

        void bind(UpgradeReport report, int position) {
            binding.getRoot().animate().cancel();

            binding.projectName.setText(report.appName);
            
            String lastMod = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(new java.util.Date(report.lastModified));
            binding.appName.setText(binding.getRoot().getContext().getString(R.string.project_details_sub, 
                    report.scId, report.age, lastMod));
            
            boolean upToDate = report.isUpToDate();
            
            String androidX = report.androidxOn ? "AndroidX ON" : "AndroidX OFF";
            binding.packageName.setText(binding.getRoot().getContext().getString(R.string.project_tech_details,
                    report.packageName, report.storedVer, report.latestVer, androidX, report.minSdk, report.libsCount));
            
            int color = upToDate ? 
                    binding.getRoot().getContext().getColor(R.color.color_primary) : 
                    binding.getRoot().getContext().getColor(R.color.scolor_red_01);
            binding.packageName.setTextColor(color);
            
            binding.imgPin.setVisibility(View.GONE);

            // G1-G4 Fix: Async downsampled icon loading
            loadIcon(report);

            binding.tvPublished.setText(report.scId);

            binding.getRoot().setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).start();
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP || 
                           event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                }
                return false;
            });

            binding.getRoot().setOnClickListener(v -> {
                if (reportClickListener != null) {
                    reportClickListener.onReportClick(report);
                }
            });

            binding.expand.setImageResource(R.drawable.ic_tab_upgrades);
            binding.expand.setVisibility(upToDate ? View.GONE : View.VISIBLE);
            binding.expand.setOnClickListener(v -> {
                if (upgradeClickListener != null) {
                    upgradeClickListener.onUpgradeClick(report);
                }
            });

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

        private void loadIcon(UpgradeReport report) {
            if (report.hasCustomIcon) {
                Bitmap cached = ICON_CACHE.get(report.scId);
                if (cached != null) {
                    binding.imgIcon.setImageBitmap(cached);
                    binding.imgIcon.setAlpha(1.0f);
                } else {
                    // Placeholder + Async Load
                    binding.imgIcon.setImageResource(R.drawable.default_icon);
                    binding.imgIcon.setAlpha(0.4f);
                    binding.imgIcon.setTag(report.scId);
                    
                    String scId = report.scId;
                    EXECUTOR.execute(() -> {
                        String iconPath = wq.e() + File.separator + scId + File.separator + "icon.png";
                        File iconFile = new File(iconPath);
                        if (iconFile.exists()) {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(iconPath, options);
                            
                            options.inSampleSize = calculateInSampleSize(options, 160, 160);
                            options.inJustDecodeBounds = false;
                            
                            Bitmap bitmap = BitmapFactory.decodeFile(iconPath, options);
                            if (bitmap != null) {
                                ICON_CACHE.put(scId, bitmap);
                                binding.imgIcon.post(() -> {
                                    if (scId.equals(binding.imgIcon.getTag())) {
                                        binding.imgIcon.setImageBitmap(bitmap);
                                        binding.imgIcon.animate().alpha(1.0f).setDuration(200).start();
                                    }
                                });
                            }
                        }
                    });
                }
            } else {
                binding.imgIcon.setImageResource(R.drawable.default_icon);
                binding.imgIcon.setAlpha(1.0f);
                binding.imgIcon.setTag(null);
            }
        }

        private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
            final int height = options.outHeight;
            final int width = options.outWidth;
            int inSampleSize = 1;
            if (height > reqHeight || width > reqWidth) {
                final int halfHeight = height / 2;
                final int halfWidth = width / 2;
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }
            return inSampleSize;
        }
    }
}
