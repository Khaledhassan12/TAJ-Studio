package pro.sketchware.ai.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import pro.sketchware.R;
import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.models.AiModel;

/**
 * [WHAT] Lists local AI models.
 * [WHY] Allows users to select active model or delete existing ones.
 */
public class ModelsFragment extends Fragment {

    private RecyclerView recycler;
    private View emptyState;
    private ModelAdapter adapter;
    private AiStorage storage;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_models, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        storage = AiStorage.get(requireContext());
        recycler = view.findViewById(R.id.recycler_models);
        emptyState = view.findViewById(R.id.empty_state);

        adapter = new ModelAdapter();
        recycler.setAdapter(adapter);

        view.findViewById(R.id.fab_search_hf).setOnClickListener(v -> {
            new HfSearchSheet().show(getChildFragmentManager(), "hf_search");
        });

        refreshList();
    }

    private void refreshList() {
        List<AiModel> models = new ArrayList<>();
        try (Cursor cursor = storage.listModels()) {
            while (cursor.moveToNext()) {
                AiModel m = new AiModel();
                m.id = cursor.getString(cursor.getColumnIndexOrThrow("id"));
                m.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                m.arch = "local"; // Placeholder
                m.filePath = cursor.getString(cursor.getColumnIndexOrThrow("filePath"));
                m.sizeBytes = new File(m.filePath).length();
                models.add(m);
            }
        }
        adapter.setModels(models);
        emptyState.setVisibility(models.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.ViewHolder> {
        private final List<AiModel> models = new ArrayList<>();

        public void setModels(List<AiModel> list) {
            models.clear();
            models.addAll(list);
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
            String meta = (m.arch != null ? m.arch : "unknown") + " / " + (m.sizeBytes / 1024 / 1024) + " MB";
            holder.meta.setText(meta);
            holder.delete.setOnClickListener(v -> {
                new File(m.filePath).delete();
                storage.deleteModel(m.id);
                refreshList();
            });
        }

        @Override
        public int getItemCount() {
            return models.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, meta;
            View delete;
            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.tv_name);
                meta = v.findViewById(R.id.tv_meta);
                delete = v.findViewById(R.id.btn_delete);
            }
        }
    }
}
