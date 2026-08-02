package pro.sketchware.marketplace.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import dev.aldi.sayuti.editor.manage.LocalLibrary;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityLibraryMarketplaceBinding;
import pro.sketchware.databinding.ViewItemMarketplaceHorizontalBinding;
import pro.sketchware.databinding.ViewItemMarketplaceVerticalBinding;
import pro.sketchware.marketplace.catalog.LibraryCatalog;
import pro.sketchware.marketplace.dialogs.CustomLibraryDialogFragment;
import pro.sketchware.marketplace.dialogs.LibraryDetailBottomSheet;
import pro.sketchware.marketplace.models.MarketplaceLibrary;
import pro.sketchware.marketplace.services.LibraryInstallService;
import pro.sketchware.utility.SketchwareUtil;

/**
 * النشاط الرئيسي لمتجر المكتبات - تم تحسين الأداء بنقل الـ I/O لخلفية وإصلاح ظهور الأيقونات.
 * Main Library Marketplace activity - optimized performance by moving I/O to background and fixed icon display.
 */
public class LibraryMarketplaceActivity extends BaseAppCompatActivity {

    private ActivityLibraryMarketplaceBinding binding;
    private List<MarketplaceLibrary> allLibraries;
    private List<String> installedLibraryNames;
    private MarketplaceAdapter allAdapter;
    private MostUsedAdapter mostUsedAdapter;
    private MarketplaceAdapter searchAdapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (LibraryInstallService.ACTION_STATUS_CHANGE.equals(action) || 
                LibraryInstallService.ACTION_LIBRARY_INSTALLED.equals(action)) {
                // G5-E: Double Verified Badge Update - تحديث فوري للشارة عند التثبيت.
                pro.sketchware.marketplace.utils.MarketplaceHelper.refreshCache();
                refreshUI();
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityLibraryMarketplaceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupUI();
        loadCatalogAsync();
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(LibraryInstallService.ACTION_STATUS_CHANGE);
        filter.addAction(LibraryInstallService.ACTION_INSTALL_STARTED);
        filter.addAction(LibraryInstallService.ACTION_INSTALL_FINISHED);
        filter.addAction(LibraryInstallService.ACTION_LIBRARY_INSTALLED);
        filter.addAction(LibraryInstallService.ACTION_LIBRARY_INSTALL_FAILED);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
    }

    private void loadCatalogAsync() {
        binding.pbLoading.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            // T2: Perform heavy I/O on executor to fix 30s delay
            allLibraries = LibraryCatalog.getCuratedLibraries();
            refreshInstalledStatus();
            
            runOnUiThread(() -> {
                if (binding != null) {
                    binding.pbLoading.setVisibility(View.GONE);
                    mostUsedAdapter.updateData(allLibraries.stream()
                            .filter(MarketplaceLibrary::isMostUsed)
                            .collect(Collectors.toList()));
                    allAdapter.updateData(allLibraries);
                }
            });
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(statusReceiver);
        executor.shutdownNow();
    }

    private void refreshInstalledStatus() {
        // نجمع أسماء مجلدات المكتبات المحلية (عادة تكون artifact-vVersion) للمطابقة الدقيقة.
        // We collect local library folder names (usually artifact-vVersion) for exact matching.
        installedLibraryNames = LocalLibrariesUtil.getAllLocalLibraries().stream()
                .map(LocalLibrary::getName)
                .collect(Collectors.toList());
        android.util.Log.d("LibMarket", "Syncing installed libraries: " + installedLibraryNames);
    }

    /**
     * يتحقق مما إذا كانت المكتبة مثبتة بمطابقة دقيقة للمفتاح المخزن باستخدام المساعد الموحد.
     * Checks if a library is installed using the unified helper for exact matching.
     */
    private boolean isInstalled(MarketplaceLibrary library) {
        return pro.sketchware.marketplace.utils.MarketplaceHelper.isInstalledSync(library);
    }

    private void setupUI() {
        binding.searchBar.setNavigationOnClickListener(v -> finish());
        
        // T6: Custom Library Feature
        binding.btnAddCustom.setOnClickListener(v -> {
            CustomLibraryDialogFragment dialog = new CustomLibraryDialogFragment();
            dialog.show(getSupportFragmentManager(), "CustomLibrary");
        });

        mostUsedAdapter = new MostUsedAdapter(new ArrayList<>());
        binding.rvMostUsed.setAdapter(mostUsedAdapter);

        allAdapter = new MarketplaceAdapter(new ArrayList<>());
        binding.rvAllLibraries.setAdapter(allAdapter);

        searchAdapter = new MarketplaceAdapter(new ArrayList<>());
        binding.rvSearchResults.setAdapter(searchAdapter);

        binding.searchView.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filter(String query) {
        if (allLibraries == null) return;
        List<MarketplaceLibrary> filtered = allLibraries.stream()
                .filter(lib -> lib.getDisplayName().toLowerCase().contains(query.toLowerCase()) ||
                        lib.getCoordinate().toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
        searchAdapter.updateData(filtered);
    }

    private void showDetails(MarketplaceLibrary library) {
        // T5: Open sheet immediately for better UX
        LibraryDetailBottomSheet bottomSheet = LibraryDetailBottomSheet.newInstance(library);
        bottomSheet.show(getSupportFragmentManager(), "LibraryDetail");
        bottomSheet.setOnDismissListener(this::refreshUI);
    }

    public void refreshUI() {
        if (allLibraries == null) return;
        executor.execute(() -> {
            refreshInstalledStatus();
            runOnUiThread(() -> {
                if (binding != null) {
                    allAdapter.notifyDataSetChanged();
                    mostUsedAdapter.notifyDataSetChanged();
                    searchAdapter.notifyDataSetChanged();
                }
            });
        });
    }

    // Adapters

    private class MarketplaceAdapter extends RecyclerView.Adapter<MarketplaceAdapter.ViewHolder> {
        private final List<MarketplaceLibrary> data;

        MarketplaceAdapter(List<MarketplaceLibrary> data) {
            this.data = new ArrayList<>(data);
        }

        void updateData(List<MarketplaceLibrary> newData) {
            data.clear();
            data.addAll(newData);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ViewItemMarketplaceVerticalBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MarketplaceLibrary lib = data.get(position);
            holder.binding.tvName.setText(lib.getDisplayName());
            holder.binding.tvSummary.setText(lib.getSummary());
            holder.binding.tvCoordinate.setText(lib.getCoordinate());
            
            // P1 & P5: Fix white icons and use GitHub fallback
            if (lib.getIconRes() != 0 && lib.getIconRes() != R.drawable.ic_lib_generic) {
                holder.binding.ivIcon.setImageResource(lib.getIconRes());
                holder.binding.ivIcon.setVisibility(View.VISIBLE);
                holder.binding.tvInitial.setVisibility(View.GONE);
            } else {
                // Fallback to GitHub icon instead of generic/initial
                holder.binding.ivIcon.setImageResource(R.drawable.ic_lib_github);
                holder.binding.ivIcon.setVisibility(View.VISIBLE);
                holder.binding.tvInitial.setVisibility(View.GONE);
            }

            if (isInstalled(lib)) {
                holder.binding.tvStatusBadge.setVisibility(View.VISIBLE);
            } else {
                holder.binding.tvStatusBadge.setVisibility(View.GONE);
            }

            holder.binding.getRoot().setOnClickListener(v -> showDetails(lib));
        }

        @Override
        public int getItemCount() { return data.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ViewItemMarketplaceVerticalBinding binding;
            ViewHolder(ViewItemMarketplaceVerticalBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private class MostUsedAdapter extends RecyclerView.Adapter<MostUsedAdapter.ViewHolder> {
        private final List<MarketplaceLibrary> data;

        MostUsedAdapter(List<MarketplaceLibrary> data) {
            this.data = new ArrayList<>(data);
        }

        void updateData(List<MarketplaceLibrary> newData) {
            data.clear();
            data.addAll(newData);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ViewItemMarketplaceHorizontalBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MarketplaceLibrary lib = data.get(position);
            holder.binding.tvName.setText(lib.getDisplayName());
            
            // P1 & P5: Fix white icons
            if (lib.getIconRes() != 0 && lib.getIconRes() != R.drawable.ic_lib_generic) {
                holder.binding.ivIcon.setImageResource(lib.getIconRes());
                holder.binding.ivIcon.setVisibility(View.VISIBLE);
                holder.binding.tvInitial.setVisibility(View.GONE);
            } else {
                // Fallback to GitHub icon
                holder.binding.ivIcon.setImageResource(R.drawable.ic_lib_github);
                holder.binding.ivIcon.setVisibility(View.VISIBLE);
                holder.binding.tvInitial.setVisibility(View.GONE);
            }

            if (isInstalled(lib)) {
                holder.binding.tvStatus.setVisibility(View.VISIBLE);
            } else {
                holder.binding.tvStatus.setVisibility(View.GONE);
            }

            holder.binding.getRoot().setOnClickListener(v -> showDetails(lib));
        }

        @Override
        public int getItemCount() { return data.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ViewItemMarketplaceHorizontalBinding binding;
            ViewHolder(ViewItemMarketplaceHorizontalBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
