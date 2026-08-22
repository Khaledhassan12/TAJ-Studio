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

import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
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
        
        Tool.Domain domain = t.domain();
        holder.chip.setText(domain.name());
        
        int colorAttr = com.google.android.material.R.attr.colorPrimaryContainer;
        int textColorAttr = com.google.android.material.R.attr.colorOnPrimaryContainer;

        switch (domain) {
            case JAVA -> {
                colorAttr = com.google.android.material.R.attr.colorPrimaryContainer;
                textColorAttr = com.google.android.material.R.attr.colorOnPrimaryContainer;
            }
            case RES -> {
                colorAttr = com.google.android.material.R.attr.colorTertiaryContainer;
                textColorAttr = com.google.android.material.R.attr.colorOnTertiaryContainer;
            }
            case ASSET -> {
                colorAttr = com.google.android.material.R.attr.colorSecondaryContainer;
                textColorAttr = com.google.android.material.R.attr.colorOnSecondaryContainer;
            }
            case BLOCK -> {
                colorAttr = com.google.android.material.R.attr.colorErrorContainer;
                textColorAttr = com.google.android.material.R.attr.colorOnErrorContainer;
            }
            case MANIFEST -> {
                colorAttr = com.google.android.material.R.attr.colorPrimaryContainer;
                textColorAttr = com.google.android.material.R.attr.colorOnPrimaryContainer;
            }
            case PROJECT -> {
                colorAttr = com.google.android.material.R.attr.colorSurfaceVariant;
                textColorAttr = com.google.android.material.R.attr.colorOnSurfaceVariant;
            }
        }
        
        int color = MaterialColors.getColor(holder.chip, colorAttr);
        int textColor = MaterialColors.getColor(holder.chip, textColorAttr);
        holder.chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(color));
        holder.chip.setTextColor(textColor);
    }

    @Override
    public int getItemCount() {
        return tools.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, desc;
        Chip chip;
        MaterialSwitch toggle;
        ViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.tv_tool_name);
            desc = v.findViewById(R.id.tv_tool_desc);
            chip = v.findViewById(R.id.chip_domain);
            toggle = v.findViewById(R.id.switch_tool);
        }
    }
}
