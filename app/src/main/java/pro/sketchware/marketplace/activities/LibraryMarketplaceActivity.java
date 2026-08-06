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
import pro.sketchware.marketplace.services.InstallStateHub;
import pro.sketchware.marketplace.services.LibraryInstallService;
import pro.sketchware.utility.SketchwareUtil;

/**
 * النشاط الرئيسي لمتجر المكتبات - تم تحسين الأداء بنقل الـ I/O لخلفية وإصلاح ظهور الأيقونات.
 * Main Library Marketplace activity - optimized performance by moving I/O to background and fixed icon display.
 */
public class LibraryMarketplaceActivity extends BaseAppCompatActivity {

    private ActivityLibraryMarketplaceBinding binding;
    private List<MarketplaceLibrary> allLibraries;
    private MarketplaceAdapter allAdapter;
    private MostUsedAdapter mostUsedAdapter;
    private MarketplaceAdapter searchAdapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final InstallStateHub.Listener hubListener = (coordinate, entry) -> refreshBadges();

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
        
        // R5: Registry Hub Listener
        InstallStateHub.getInstance().addListener(hubListener);

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
            // WHAT: Load catalog once (cached in LibraryCatalog).
            // WHY: Keeps UI thread free during initial object mapping.
            allLibraries = LibraryCatalog.getCuratedLibraries();
            
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
        InstallStateHub.getInstance().removeListener(hubListener);
        unregisterReceiver(statusReceiver);
        executor.shutdownNow();
    }

    /**
     * يتحقق مما إذا كانت المكتبة مثبتة باستخدام المساعد الموحد الذي يعتمد على الكاش.
     * Checks if a library is installed using the unified, cached helper.
     */
    private boolean isInstalled(MarketplaceLibrary library) {
        return pro.sketchware.marketplace.utils.MarketplaceHelper.isInstalledSync(library);
    }

    /**
     * تبديل مركزي بين واجهة المتجر الرئيسية ونتائج البحث.
     * Centralized toggle between main marketplace UI and search results.
     *
     * @param active true if search results should be visible (query not empty).
     */
    private void setSearchMode(boolean active) {
        if (binding == null) return;
        // WHAT: Marketplace Content Switcher - Toggle between main catalog and search results.
        // WHY: Case A/B fix - Ensures the main content doesn't show through the search results list.
        // (عربي) تبديل المحتوى: إخفاء القائمة الرئيسية وإظهار نتائج البحث لضمان عدم التداخل.
        binding.marketplaceMainContent.setVisibility(active ? View.GONE : View.VISIBLE);
        binding.rvSearchResults.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    private void setupUI() {
        binding.searchBar.setNavigationOnClickListener(v -> finish());
        
        // T6: Custom Library Feature
        binding.btnAddCustom.setOnClickListener(v -> {
            CustomLibraryDialogFragment dialog = new CustomLibraryDialogFragment();
            dialog.show(getSupportFragmentManager(), "CustomLibrary");
        });

        mostUsedAdapter = new MostUsedAdapter(new ArrayList<>());
        binding.rvMostUsed.setHasFixedSize(true);
        binding.rvMostUsed.setAdapter(mostUsedAdapter);

        allAdapter = new MarketplaceAdapter(new ArrayList<>());
        binding.rvAllLibraries.setHasFixedSize(true);
        binding.rvAllLibraries.setAdapter(allAdapter);

        searchAdapter = new MarketplaceAdapter(new ArrayList<>());
        binding.rvSearchResults.setHasFixedSize(true);
        binding.rvSearchResults.setAdapter(searchAdapter);

        binding.searchView.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // WHAT: searchOverlayToggleV3 - Toggle based on text presence.
                // WHY: Resolves UI conflict by ensuring main content is hidden only when query is active.
                // (عربي) تبديل واجهة البحث: إخفاء المحتوى الرئيسي فقط عند وجود نص بحث لتجنب التداخل.
                setSearchMode(s.length() != 0);
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // WHAT: searchTransitionListener - Handle search exit paths.
        // WHY: Case C fix - Ensures main UI returns when search is closed even if text wasn't cleared.
        binding.searchView.addTransitionListener((searchView, previousState, newState) -> {
            if (newState == com.google.android.material.search.SearchView.TransitionState.HIDDEN 
                    || newState == com.google.android.material.search.SearchView.TransitionState.HIDING) {
                setSearchMode(false);
            }
        });
    }

    @Override
    public void onBackPressed() {
        // Handle search back navigation correctly
        if (binding.searchView.isShowing()) {
            binding.searchView.hide();
        } else {
            super.onBackPressed();
        }
    }

    private void filter(String query) {
        if (allLibraries == null) return;
        // WHAT: Hoist lowercase conversion out of the stream.
        // WHY: Avoids redundant toLowerCase() calls for every item in the catalog.
        String q = query.toLowerCase();
        List<MarketplaceLibrary> filtered = allLibraries.stream()
                .filter(lib -> lib.getDisplayName().toLowerCase().contains(q) ||
                        lib.getCoordinate().toLowerCase().contains(q))
                .collect(Collectors.toList());
        searchAdapter.updateData(filtered);
    }

    private void showDetails(MarketplaceLibrary library) {
        // T5: Open sheet immediately for better UX
        LibraryDetailBottomSheet bottomSheet = LibraryDetailBottomSheet.newInstance(library);
        bottomSheet.show(getSupportFragmentManager(), "LibraryDetail");
        bottomSheet.setOnDismissListener(this::refreshUI);
    }

    /**
     * تحديث شارات الحالة فقط لضمان سلاسة الواجهة.
     */
    public void refreshBadges() {
        refreshUI();
    }

    /**
     * تحديث الواجهة لإعادة رسم حالة التثبيت (Badges).
     * Refreshes the UI to redraw installation status badges.
     *
     * WHAT: Direct notifyDataSetChanged() on UI thread.
     * WHY: Status checks now use a fast cache, no need for background executor overhead here.
     */
    public void refreshUI() {
        if (allLibraries == null) return;
        if (allAdapter != null) allAdapter.notifyDataSetChanged();
        if (mostUsedAdapter != null) mostUsedAdapter.notifyDataSetChanged();
        if (searchAdapter != null) searchAdapter.notifyDataSetChanged();
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

            // R5: Honest Live Badges (Hub priority)
            InstallStateHub.Entry entry = InstallStateHub.getInstance().get(lib.getCoordinate());
            if (entry != null) {
                if (entry.state == InstallStateHub.State.SUCCESS) {
                    holder.binding.tvStatusBadge.setVisibility(View.VISIBLE);
                    holder.binding.tvStatusBadge.setText(R.string.lib_installed);
                    holder.binding.tvStatusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF00C853));
                } else if (entry.state == InstallStateHub.State.FAILED) {
                    holder.binding.tvStatusBadge.setVisibility(View.GONE);
                } else if (entry.state != InstallStateHub.State.IDLE) {
                    holder.binding.tvStatusBadge.setVisibility(View.VISIBLE);
                    holder.binding.tvStatusBadge.setText("Installing…");
                    holder.binding.tvStatusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFA000));
                } else {
                    holder.binding.tvStatusBadge.setVisibility(View.GONE);
                }
            } else if (isInstalled(lib)) {
                holder.binding.tvStatusBadge.setVisibility(View.VISIBLE);
                holder.binding.tvStatusBadge.setText(R.string.lib_installed);
                holder.binding.tvStatusBadge.setBackgroundTintList(null); // Default theme
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

            // R5: Honest Live Badges
            InstallStateHub.Entry entry = InstallStateHub.getInstance().get(lib.getCoordinate());
            if (entry != null) {
                if (entry.state == InstallStateHub.State.SUCCESS) {
                    holder.binding.tvStatus.setVisibility(View.VISIBLE);
                    holder.binding.tvStatus.setText(R.string.lib_installed);
                } else if (entry.state == InstallStateHub.State.FAILED) {
                    holder.binding.tvStatus.setVisibility(View.GONE);
                } else if (entry.state != InstallStateHub.State.IDLE) {
                    holder.binding.tvStatus.setVisibility(View.VISIBLE);
                    holder.binding.tvStatus.setText("Installing…");
                } else {
                    holder.binding.tvStatus.setVisibility(View.GONE);
                }
            } else if (isInstalled(lib)) {
                holder.binding.tvStatus.setVisibility(View.VISIBLE);
                holder.binding.tvStatus.setText(R.string.lib_installed);
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
