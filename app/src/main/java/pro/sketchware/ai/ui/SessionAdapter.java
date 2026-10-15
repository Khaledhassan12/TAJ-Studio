package pro.sketchware.ai.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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

    private final List<ChatStore.Session> sessions = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());
    private OnSessionClickListener listener;

    public interface OnSessionClickListener {
        void onSessionClick(ChatStore.Session session);
    }

    public void setSessions(List<ChatStore.Session> newSessions) {
        sessions.clear();
        sessions.addAll(newSessions);
        notifyDataSetChanged();
    }

    public void setOnSessionClickListener(OnSessionClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatStore.Session s = sessions.get(position);
        String title = s.title == null || s.title.trim().isEmpty() ? "New chat" : s.title;
        holder.title.setText(title);
        holder.time.setText(dateFormat.format(new Date(s.updatedAt > 0 ? s.updatedAt : s.createdAt)));
        String preview = "";
        for (int i = s.messages.size() - 1; i >= 0; i--) {
            ChatStore.Message message = s.messages.get(i);
            if (message != null && ("assistant".equals(message.role) || "user".equals(message.role))
                    && message.text != null && !message.text.trim().isEmpty()) {
                preview = message.text.trim().replaceAll("\\s+", " ");
                if (preview.length() > 48) preview = preview.substring(0, 45) + "...";
                break;
            }
        }
        holder.count.setText(s.messages.size() + " messages" + (preview.isEmpty() ? "" : " · " + preview));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSessionClick(s);
        });
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, time, count;
        ViewHolder(View v) {
            super(v);
            title = v.findViewById(R.id.tv_session_title);
            time = v.findViewById(R.id.tv_session_time);
            count = v.findViewById(R.id.tv_session_count);
        }
    }
}
