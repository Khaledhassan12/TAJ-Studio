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
    private OnModelClickListener listener;

    public interface OnModelClickListener {
        void onModelClick(ModelItem model);
    }

    public void setModels(List<ModelItem> newModels) {
        models.clear();
        models.addAll(newModels);
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
