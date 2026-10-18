package pro.sketchware.ai.ui;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import java.util.ArrayList;
import java.util.List;
import pro.sketchware.R;
import pro.sketchware.ai.agent.Tool;

public class ToolAdapter extends RecyclerView.Adapter<ToolAdapter.ViewHolder> {
 private final List<Tool> tools=new ArrayList<>(),allTools=new ArrayList<>();
 private String query=""; private TextView emptyView,countView;
 public void setEmptyView(TextView v){emptyView=v;updateStateViews();}
 public void setCountView(TextView v){countView=v;updateStateViews();}
 public void setTools(List<Tool> values){allTools.clear();if(values!=null)allTools.addAll(values);applyFilter(query);}
 public void setQuery(String value){query=value==null?"":value;applyFilter(query);}
 private void applyFilter(String value){
  String needle=value==null?"":value.trim().toLowerCase(java.util.Locale.ROOT);tools.clear();
  for(Tool t:allTools){if(t==null||t.spec()==null)continue;String name=t.spec().name==null?"":t.spec().name;String desc=t.spec().description==null?"":t.spec().description;String domain=t.domain()==null?"":t.domain().name();
   if(needle.isEmpty()||name.toLowerCase(java.util.Locale.ROOT).contains(needle)||desc.toLowerCase(java.util.Locale.ROOT).contains(needle)||domain.toLowerCase(java.util.Locale.ROOT).contains(needle))tools.add(t);}
  notifyDataSetChanged();updateStateViews();
 }
 private void updateStateViews(){
  if(emptyView!=null){emptyView.setVisibility(tools.isEmpty()?View.VISIBLE:View.GONE);emptyView.setText(allTools.isEmpty()?"No tools are registered in ToolRegistry.":"No registered tools match this search.");}
  if(countView!=null)countView.setText(query.trim().isEmpty()?allTools.size()+" registered tools":tools.size()+" of "+allTools.size()+" tools shown");
 }
 @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p,int t){return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_tool_row,p,false));}
 @Override public void onBindViewHolder(@NonNull ViewHolder h,int pos){
  Tool tool=tools.get(pos);h.name.setText(tool.spec().name);h.desc.setText(tool.spec().description);Tool.Domain d=tool.domain();h.chip.setText(d.name());
  int bg=com.google.android.material.R.attr.colorPrimaryContainer,fg=com.google.android.material.R.attr.colorOnPrimaryContainer;
  switch(d){case JAVA->{bg=com.google.android.material.R.attr.colorPrimaryContainer;fg=com.google.android.material.R.attr.colorOnPrimaryContainer;}case RES->{bg=com.google.android.material.R.attr.colorTertiaryContainer;fg=com.google.android.material.R.attr.colorOnTertiaryContainer;}case ASSET->{bg=com.google.android.material.R.attr.colorSecondaryContainer;fg=com.google.android.material.R.attr.colorOnSecondaryContainer;}case BLOCK->{bg=com.google.android.material.R.attr.colorErrorContainer;fg=com.google.android.material.R.attr.colorOnErrorContainer;}case MANIFEST->{bg=com.google.android.material.R.attr.colorPrimaryContainer;fg=com.google.android.material.R.attr.colorOnPrimaryContainer;}case PROJECT->{bg=com.google.android.material.R.attr.colorSurfaceVariant;fg=com.google.android.material.R.attr.colorOnSurfaceVariant;}}
  h.chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(MaterialColors.getColor(h.chip,bg)));h.chip.setTextColor(MaterialColors.getColor(h.chip,fg));
 }
 @Override public int getItemCount(){return tools.size();}
 static class ViewHolder extends RecyclerView.ViewHolder{final TextView name,desc;final Chip chip;ViewHolder(View v){super(v);name=v.findViewById(R.id.tv_tool_name);desc=v.findViewById(R.id.tv_tool_desc);chip=v.findViewById(R.id.chip_domain);View toggle=v.findViewById(R.id.switch_tool);if(toggle!=null)toggle.setVisibility(View.GONE);}}
}
