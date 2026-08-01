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
import java.util.stream.Collectors;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import dev.aldi.sayuti.editor.manage.LocalLibrary;
import pro.sketchware.databinding.ActivityLibraryMarketplaceBinding;
import pro.sketchware.databinding.ViewItemMarketplaceHorizontalBinding;
import pro.sketchware.databinding.ViewItemMarketplaceVerticalBinding;
import pro.sketchware.marketplace.catalog.LibraryCatalog;
import pro.sketchware.marketplace.dialogs.CustomLibraryDialogFragment;
import pro.sketchware.marketplace.dialogs.LibraryDetailBottomSheet;
import pro.sketchware.marketplace.models.MarketplaceLibrary;
import pro.sketchware.marketplace.services.LibraryInstallService;

/**
 * النشاط الرئيسي لمتجر المكتبات - تم إصلاح شريط البحث وإضافة ميزة المكتبات المخصصة.
 * Main Library Marketplace activity - fixed search bar and added custom library feature.
 */
public class LibraryMarketplaceActivity extends BaseAppCompatActivity {

    private ActivityLibraryMarketplaceBinding binding;
    private List<MarketplaceLibrary> allLibraries;
    private List<String> installedLibraryNames;
    private MarketplaceAdapter allAdapter;
    private MostUsedAdapter mostUsedAdapter;
    private MarketplaceAdapter searchAdapter;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (LibraryInstallService.ACTION_STATUS_CHANGE.equals(action)) {
                refreshUI();
            } else if (LibraryInstallService.ACTION_INSTALL_STARTED.equals(action)) {
                binding.btnBackgroundTask.setVisibility(View.VISIBLE);
            } else if (LibraryInstallService.ACTION_INSTALL_FINISHED.equals(action)) {
                binding.btnBackgroundTask.setVisibility(View.GONE);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityLibraryMarketplaceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        allLibraries = LibraryCatalog.getCuratedLibraries();
        refreshInstalledStatus();

        setupUI();
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(LibraryInstallService.ACTION_STATUS_CHANGE);
        filter.addAction(LibraryInstallService.ACTION_INSTALL_STARTED);
        filter.addAction(LibraryInstallService.ACTION_INSTALL_FINISHED);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(statusReceiver);
    }

    private void refreshInstalledStatus() {
        installedLibraryNames = LocalLibrariesUtil.getAllLocalLibraries().stream()
                .map(LocalLibrary::getName)
                .collect(Collectors.toList());
    }

    private boolean isInstalled(MarketplaceLibrary library) {
        String expectedName = library.getId();
        for (String installed : installedLibraryNames) {
            if (installed.equalsIgnoreCase(expectedName) || installed.toLowerCase().contains(expectedName.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void setupUI() {
        binding.searchBar.setNavigationOnClickListener(v -> finish());
        
        // T6: Custom Library Feature
        binding.btnAddCustom.setOnClickListener(v -> {
            CustomLibraryDialogFragment dialog = new CustomLibraryDialogFragment();
            dialog.show(getSupportFragmentManager(), "CustomLibrary");
        });

        binding.btnBackgroundTask.setOnClickListener(v -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        });

        mostUsedAdapter = new MostUsedAdapter(allLibraries.stream().filter(MarketplaceLibrary::isMostUsed).collect(Collectors.toList()));
        binding.rvMostUsed.setAdapter(mostUsedAdapter);

        allAdapter = new MarketplaceAdapter(allLibraries);
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
        runOnUiThread(() -> {
            refreshInstalledStatus();
            allAdapter.notifyDataSetChanged();
            mostUsedAdapter.notifyDataSetChanged();
            searchAdapter.notifyDataSetChanged();
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
            
            if (lib.getIconRes() != 0) {
                holder.binding.ivIcon.setImageResource(lib.getIconRes());
                holder.binding.tvInitial.setVisibility(View.GONE);
            } else {
                holder.binding.tvInitial.setText(lib.getDisplayName().substring(0, 1).toUpperCase());
                holder.binding.tvInitial.setVisibility(View.VISIBLE);
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
            this.data = data;
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
            
            if (lib.getIconRes() != 0) {
                holder.binding.ivIcon.setImageResource(lib.getIconRes());
                holder.binding.tvInitial.setVisibility(View.GONE);
            } else {
                holder.binding.tvInitial.setText(lib.getDisplayName().substring(0, 1).toUpperCase());
                holder.binding.tvInitial.setVisibility(View.VISIBLE);
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
