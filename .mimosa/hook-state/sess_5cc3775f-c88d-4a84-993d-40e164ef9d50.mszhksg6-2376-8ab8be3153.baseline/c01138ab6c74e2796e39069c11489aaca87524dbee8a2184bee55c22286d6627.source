package pro.sketchware.ai.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.R;
import pro.sketchware.ai.config.AIConfigStore;
import pro.sketchware.ai.core.AIProviderRegistry;
import pro.sketchware.ai.core.ModelItem;
import pro.sketchware.ai.core.ProviderProfile;
import pro.sketchware.ai.net.AIException;
import pro.sketchware.ai.net.ModelSyncService;
import pro.sketchware.databinding.BottomSheetModelPickerBinding;
import pro.sketchware.databinding.ItemModelBinding;

/**
 * M3 bottom sheet that picks a model for the active provider. Shows the cached
 * list instantly when younger than 24h; otherwise fetches it on a background
 * thread while a CircularProgressIndicator spins. Search filters live and the
 * All/Free/Paid chips are enabled only when the provider exposes pricing.
 */
public class ModelPickerBottomSheet extends BottomSheetDialogFragment {

    public interface OnModelSelectedListener {
        void onModelSelected(String modelId);
    }

    private static final String ARG_PROVIDER_ID = "provider_id";
    private static final int FILTER_ALL = 0;
    private static final int FILTER_FREE = 1;
    private static final int FILTER_PAID = 2;

    private BottomSheetModelPickerBinding binding;
    private OnModelSelectedListener listener;

    private String providerId;
    private ProviderProfile sourceProfile;
    private String sourceApiKey = "";
    private boolean pricingAvailable;
    private Context appContext;

    private final List<ModelItem> allModels = new ArrayList<>();
    private final List<ModelItem> visibleModels = new ArrayList<>();
    private ModelAdapter adapter;
    private int filter = FILTER_ALL;
    private String query = "";
    private boolean started = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static ModelPickerBottomSheet newInstance(String providerId, ProviderProfile profile,
                                                     String apiKey, boolean pricingAvailable) {
        ModelPickerBottomSheet sheet = new ModelPickerBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_PROVIDER_ID, providerId);
        sheet.setArguments(args);
        sheet.sourceProfile = profile;
        sheet.sourceApiKey = AIConfigStore.sanitizeKey(apiKey);
        sheet.pricingAvailable = pricingAvailable;
        return sheet;
    }

    public void setListener(OnModelSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        providerId = args == null ? "" : args.getString(ARG_PROVIDER_ID, "");
        appContext = requireContext().getApplicationContext();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetModelPickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.rvModels.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ModelAdapter(visibleModels, this::onModelSelected);
        binding.rvModels.setAdapter(adapter);

        binding.editModelSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = (s == null ? "" : s.toString()).trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });

        binding.chipGroupPickerFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            if (checkedIds.get(0) == R.id.chip_picker_free) {
                filter = FILTER_FREE;
            } else if (checkedIds.get(0) == R.id.chip_picker_paid) {
                filter = FILTER_PAID;
            } else {
                filter = FILTER_ALL;
            }
            applyFilter();
        });

        binding.chipPickerFree.setEnabled(pricingAvailable);
        binding.chipPickerPaid.setEnabled(pricingAvailable);

        if (started) {
            applyFilter();
            return;
        }
        started = true;
        loadCacheOrFetch();
    }
    private void loadCacheOrFetch() {
        AIConfigStore store = AIConfigStore.getInstance(appContext);
        List<ModelItem> cached = store.loadModelsCache(providerId);
        if (!cached.isEmpty() && store.isModelsCacheFresh(providerId)) {
            showLoaded(cached);
            return;
        }
        showLoading();
        executor.execute(this::fetchModels);
    }

    private void fetchModels() {
        Context ctx = appContext;
        if (ctx == null) {
            return;
        }
        if (sourceProfile == null) {
            sourceProfile = AIProviderRegistry.getInstance(ctx).get(providerId);
            sourceApiKey = AIConfigStore.getInstance(ctx).getApiKey(providerId);
        }
        if (sourceProfile == null) {
            postToUi(() -> showEmpty(getString(R.string.ai_no_model_list)));
            return;
        }
        try {
            ModelSyncService.Result result = ModelSyncService.fetch(sourceProfile, sourceApiKey);
            AIConfigStore.getInstance(ctx).saveModelsCache(providerId, result.models);
            postToUi(() -> {
                if (result.modelListUnavailable) {
                    showEmpty(getString(R.string.ai_no_model_list));
                } else {
                    showLoaded(result.models);
                }
            });
        } catch (AIException e) {
            final String friendly = e.friendlyMessage();
            final String raw = e.rawBody;
            postToUi(() -> showEmpty(raw.isEmpty() ? friendly : friendly + "\n\n" + raw));
        }
    }

    private void postToUi(Runnable runnable) {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
            if (binding != null && isAdded()) {
                runnable.run();
            }
        });
        }
    }

    private void showLoading() {
        binding.progressModels.setVisibility(View.VISIBLE);
        binding.rvModels.setVisibility(View.GONE);
        binding.tvPickerEmpty.setVisibility(View.GONE);
    }

    private void showLoaded(List<ModelItem> items) {
        binding.progressModels.setVisibility(View.GONE);
        allModels.clear();
        allModels.addAll(items);
        binding.rvModels.setVisibility(View.VISIBLE);
        applyFilter();
    }

    private void showEmpty(String message) {
        binding.progressModels.setVisibility(View.GONE);
        allModels.clear();
        adapter.notifyDataSetChanged();
        binding.rvModels.setVisibility(View.GONE);
        binding.tvPickerEmpty.setVisibility(View.VISIBLE);
        binding.tvPickerEmpty.setText(message);
    }

    private void applyFilter() {
        visibleModels.clear();
        for (ModelItem item : allModels) {
            if (!matchesQuery(item)) {
                continue;
            }
            if (filter == FILTER_FREE && !item.isFree()) {
                continue;
            }
            if (filter == FILTER_PAID && (item.free == null || item.isFree())) {
                continue;
            }
            visibleModels.add(item);
        }
        adapter.notifyDataSetChanged();
        if (visibleModels.isEmpty() && !allModels.isEmpty()) {
            binding.tvPickerEmpty.setVisibility(View.VISIBLE);
            binding.tvPickerEmpty.setText(R.string.ai_model_picker_empty);
            binding.rvModels.setVisibility(View.GONE);
        } else if (!allModels.isEmpty()) {
            binding.tvPickerEmpty.setVisibility(View.GONE);
            binding.rvModels.setVisibility(View.VISIBLE);
        }
    }

    private boolean matchesQuery(ModelItem item) {
        return query.isEmpty() || item.id.toLowerCase(Locale.ROOT).contains(query);
    }

    private void onModelSelected(String modelId) {
        if (listener != null) {
            listener.onModelSelected(modelId);
        }
        dismissAllowingStateLoss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private static final class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.VH> {
        private final List<ModelItem> items;
        private final OnModelSelectedListener listener;

        ModelAdapter(List<ModelItem> items, OnModelSelectedListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemModelBinding b = ItemModelBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(b);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ModelItem item = items.get(position);
            holder.binding.tvModelId.setText(item.id);
            Chip badge = holder.binding.chipModelBadge;
            if (item.free == null) {
                badge.setVisibility(View.GONE);
            } else {
                badge.setVisibility(View.VISIBLE);
                boolean free = item.free;
                badge.setText(free ? R.string.ai_model_free : R.string.ai_model_paid);
                badge.setChipBackgroundColor(ColorStateList.valueOf(free ? 0xFFA5D6A7 : 0xFFB0BEC5));
                badge.setTextColor(free ? Color.parseColor("#1B5E20") : Color.parseColor("#37474F"));
            }
            holder.binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onModelSelected(item.id);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static final class VH extends RecyclerView.ViewHolder {
            final ItemModelBinding binding;

            VH(ItemModelBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
