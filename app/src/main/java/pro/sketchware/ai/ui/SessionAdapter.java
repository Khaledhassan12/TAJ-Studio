package pro.sketchware.ai.ui;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.PopupMenu;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import pro.sketchware.R;
import pro.sketchware.ai.core.ChatStore;
public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.ViewHolder> {
 public static final int ACTION_RENAME=1,ACTION_BRANCH=2,ACTION_EXPORT=3,ACTION_DELETE=4;
 private final List<ChatStore.Session> sessions=new ArrayList<>(), allSessions=new ArrayList<>();
 private final SimpleDateFormat dateFormat=new SimpleDateFormat("dd MMM, HH:mm",Locale.getDefault());
 private String query="";
 private OnSessionClickListener listener; private OnSessionActionListener actionListener;
 public interface OnSessionClickListener { void onSessionClick(ChatStore.Session session); }
 public interface OnSessionActionListener { void onSessionAction(ChatStore.Session session,int action); }
 public void setSessions(List<ChatStore.Session> values){allSessions.clear();if(values!=null)allSessions.addAll(values);filter();}
 public void setQuery(String value){query=value==null?"":value.trim().toLowerCase(Locale.ROOT);filter();}
 private void filter(){sessions.clear();for(ChatStore.Session s:allSessions){boolean match=query.isEmpty()||(s.title!=null&&s.title.toLowerCase(Locale.ROOT).contains(query));if(!match&&s.messages!=null)for(ChatStore.Message m:s.messages)if(m!=null&&m.text!=null&&m.text.toLowerCase(Locale.ROOT).contains(query)){match=true;break;}if(match)sessions.add(s);}notifyDataSetChanged();}
 public void setOnSessionClickListener(OnSessionClickListener l){listener=l;}
 public void setOnSessionActionListener(OnSessionActionListener l){actionListener=l;}
 @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p,int t){return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_session_row,p,false));}
 @Override public void onBindViewHolder(@NonNull ViewHolder h,int position){
  ChatStore.Session s=sessions.get(position);h.title.setText(s.title==null||s.title.trim().isEmpty()?"New chat":s.title.trim());
  h.time.setText(dateFormat.format(new Date(s.updatedAt>0?s.updatedAt:s.createdAt)));
  String preview="";for(int i=s.messages.size()-1;i>=0;i--){ChatStore.Message m=s.messages.get(i);if(m!=null&&("assistant".equals(m.role)||"user".equals(m.role))&&m.text!=null&&!m.text.trim().isEmpty()){preview=m.text.trim().replaceAll("\\s+"," ");if(preview.length()>84)preview=preview.substring(0,81)+"...";break;}}
  h.count.setText(s.messages.size()+" messages"+(preview.isEmpty()?"":"  ·  "+preview));
  h.itemView.setOnClickListener(v->{if(listener!=null)listener.onSessionClick(s);});
  h.actions.setOnClickListener(v->{PopupMenu menu=new PopupMenu(v.getContext(),h.actions);menu.getMenu().add(0,ACTION_RENAME,0,"Rename");menu.getMenu().add(0,ACTION_BRANCH,1,"Open in new branch");menu.getMenu().add(0,ACTION_EXPORT,2,"Export session");menu.getMenu().add(0,ACTION_DELETE,3,"Delete session");menu.setOnMenuItemClickListener(item->{if(actionListener!=null)actionListener.onSessionAction(s,item.getItemId());return true;});menu.show();});
 }
 @Override public int getItemCount(){return sessions.size();}
 static class ViewHolder extends RecyclerView.ViewHolder {final TextView title,time,count,actions;ViewHolder(View v){super(v);title=v.findViewById(R.id.tv_session_title);time=v.findViewById(R.id.tv_session_time);count=v.findViewById(R.id.tv_session_count);actions=v.findViewById(R.id.tv_session_actions);}}
}