package pro.sketchware.ai.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.core.ModelItem;

public class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.ViewHolder> {

    private final List<ModelItem> models = new ArrayList<>();
    private final List<ModelItem> allModels = new ArrayList<>();
    private OnModelClickListener listener;

    public interface OnModelClickListener {
        void onModelClick(ModelItem model);
    }

    public void setModels(List<ModelItem> newModels) {
        allModels.clear();
        if (newModels != null) allModels.addAll(newModels);
        applyFilter("");
    }

    /** Filters the visible list without destroying the cached provider catalog. */
    public void filterModels(String query) {
        applyFilter(query);
    }

    private void applyFilter(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        models.clear();
        for (ModelItem model : allModels) {
            if (model == null || model.id == null) continue;
            if (needle.isEmpty() || model.id.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                models.add(model);
            }
        }
        notifyDataSetChanged();
    }

    public void setOnModelClickListener(OnModelClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_model_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ModelItem m = models.get(position);
        holder.name.setText(m.id);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onModelClick(m);
        });
    }

    @Override
    public int getItemCount() {
        return models.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        ViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.tv_model_name);
        }
    }
}
