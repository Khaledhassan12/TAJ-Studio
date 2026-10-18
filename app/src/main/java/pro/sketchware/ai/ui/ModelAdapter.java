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
 private final List<ModelItem> models=new ArrayList<>(), allModels=new ArrayList<>();
 private OnModelClickListener listener; private TextView emptyView,countView; private String query="";
 public interface OnModelClickListener { void onModelClick(ModelItem model); }
 public void setEmptyView(TextView v){emptyView=v;updateStateViews();}
 public void setCountView(TextView v){countView=v;updateStateViews();}
 public void setModels(List<ModelItem> values){allModels.clear();if(values!=null)allModels.addAll(values);applyFilter(query);}
 public void filterModels(String value){query=value==null?"":value;applyFilter(query);}
 private void applyFilter(String value){
  String needle=value==null?"":value.trim().toLowerCase(java.util.Locale.ROOT);models.clear();
  for(ModelItem model:allModels)if(model!=null&&model.id!=null&&(needle.isEmpty()||model.id.toLowerCase(java.util.Locale.ROOT).contains(needle)))models.add(model);
  notifyDataSetChanged();updateStateViews();
 }
 private void updateStateViews(){
  if(emptyView!=null){emptyView.setVisibility(models.isEmpty()?View.VISIBLE:View.GONE);emptyView.setText(allModels.isEmpty()?"No cached models. Open Manage to configure a provider and sync its model list.":"No models match this search.");}
  if(countView!=null)countView.setText(query.trim().isEmpty()?allModels.size()+" cached models":models.size()+" of "+allModels.size()+" models shown");
 }
 public void setOnModelClickListener(OnModelClickListener l){listener=l;}
 @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p,int t){return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_model_row,p,false));}
 @Override public void onBindViewHolder(@NonNull ViewHolder h,int pos){ModelItem m=models.get(pos);h.name.setText(m.id);h.itemView.setOnClickListener(v->{if(listener!=null)listener.onModelClick(m);});}
 @Override public int getItemCount(){return models.size();}
 static class ViewHolder extends RecyclerView.ViewHolder {final TextView name;ViewHolder(View v){super(v);name=v.findViewById(R.id.tv_model_name);}}
}
