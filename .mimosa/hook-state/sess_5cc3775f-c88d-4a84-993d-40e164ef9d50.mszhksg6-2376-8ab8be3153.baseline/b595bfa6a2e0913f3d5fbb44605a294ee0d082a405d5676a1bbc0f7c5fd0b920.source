package pro.sketchware.ai.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.agent.Tool;

public class ToolAdapter extends RecyclerView.Adapter<ToolAdapter.ViewHolder> {

    private final List<Tool> tools = new ArrayList<>();

    public void setTools(List<Tool> newTools) {
        tools.clear();
        tools.addAll(newTools);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tool_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Tool t = tools.get(position);
        holder.name.setText(t.spec().name);
        holder.desc.setText(t.spec().description);
    }

    @Override
    public int getItemCount() {
        return tools.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, desc;
        MaterialSwitch toggle;
        ViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.tv_tool_name);
            desc = v.findViewById(R.id.tv_tool_desc);
            toggle = v.findViewById(R.id.switch_tool);
        }
    }
}
