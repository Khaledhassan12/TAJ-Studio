package pro.sketchware.ai.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import pro.sketchware.R;
import pro.sketchware.ai.bus.AiEventHub;
import pro.sketchware.ai.models.AiModel;
import pro.sketchware.ai.models.ModelManager;

public class ModelsFragment extends Fragment implements AiEventHub.Listener {

    private ModelAdapter adapter;
    private View emptyState;
    private ModelManager manager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_models, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        manager = ModelManager.get(requireContext());
        emptyState = view.findViewById(R.id.empty_state);
        RecyclerView recycler = view.findViewById(R.id.recycler_models);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ModelAdapter();
        recycler.setAdapter(adapter);

        view.findViewById(R.id.btn_search_hf).setOnClickListener(v -> {
            new HfSearchSheet().show(getChildFragmentManager(), "hf_search");
        });

        refreshList();
        AiEventHub.get().addListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        AiEventHub.get().removeListener(this);
    }

    private void refreshList() {
        List<AiModel> models = manager.listLocal();
        adapter.setModels(models);
        emptyState.setVisibility(models.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onAiEvent(AiEventHub.Entry entry) {
        if (entry.event == AiEventHub.Event.MODELS_CHANGED) {
            refreshList();
        }
    }

    private class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.ViewHolder> {
        private final List<AiModel> models = new ArrayList<>();

        public void setModels(List<AiModel> newModels) {
            models.clear();
            models.addAll(newModels);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ai_model, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AiModel m = models.get(position);
            holder.name.setText(m.name);
            holder.meta.setText(m.arch + " / " + m.quant + " / " + (m.sizeBytes / 1024 / 1024) + " MB");
            holder.status.setVisibility(m.isActive ? View.VISIBLE : View.GONE);
            holder.delete.setOnClickListener(v -> manager.delete(m.id));
            holder.itemView.setOnClickListener(v -> manager.setActive(m.id));
        }

        @Override
        public int getItemCount() {
            return models.size();
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, meta;
            View status, delete;
            public ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.tv_name);
                meta = v.findViewById(R.id.tv_meta);
                status = v.findViewById(R.id.img_status);
                delete = v.findViewById(R.id.btn_delete);
            }
        }
    }
}
