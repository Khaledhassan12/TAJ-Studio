package pro.sketchware.ai.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
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

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_ASSISTANT = 1;
    private static final int TYPE_TOOL = 2;
    private static final int TYPE_SUGGESTION = 3;
    private static final int TYPE_TYPING = 4;

    private final List<ChatStore.Message> messages = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private OnSuggestionListener suggestionListener;
    private boolean isTyping = false;
    private int lastAnimatedPosition = -1;

    public interface OnSuggestionListener {
        void onSwitchToAgent(int position);
        void onStayInChat(int position);
    }

    public void setOnSuggestionListener(OnSuggestionListener listener) {
        this.suggestionListener = listener;
    }

    public void setMessages(List<ChatStore.Message> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    public void addMessage(ChatStore.Message message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void setTyping(boolean typing) {
        if (isTyping == typing) return;
        isTyping = typing;
        if (typing) notifyItemInserted(messages.size());
        else notifyItemRemoved(messages.size());
    }

    @Override
    public int getItemViewType(int position) {
        if (position == messages.size() && isTyping) return TYPE_TYPING;
        ChatStore.Message m = messages.get(position);
        if ("user".equals(m.role)) return TYPE_USER;
        if ("tool".equals(m.role)) return TYPE_TOOL;
        if ("suggestion".equals(m.role)) return TYPE_SUGGESTION;
        return TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            return new UserViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false));
        } else if (viewType == TYPE_TOOL) {
            return new ToolViewHolder(inflater.inflate(R.layout.item_chat_tool, parent, false));
        } else if (viewType == TYPE_SUGGESTION) {
            return new SuggestionViewHolder(inflater.inflate(R.layout.item_chat_suggestion, parent, false));
        } else if (viewType == TYPE_TYPING) {
            return new TypingViewHolder(inflater.inflate(R.layout.item_chat_typing, parent, false));
        } else {
            return new AssistantViewHolder(inflater.inflate(R.layout.item_chat_assistant, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (position >= messages.size()) return;
        ChatStore.Message m = messages.get(position);
        String time = timeFormat.format(new Date(m.time));

        if (holder instanceof UserViewHolder) {
            UserViewHolder vh = (UserViewHolder) holder;
            vh.label.setText("You • " + time);
            vh.message.setText(m.text);
        } else if (holder instanceof AssistantViewHolder) {
            AssistantViewHolder vh = (AssistantViewHolder) holder;
            vh.label.setText("Assistant • " + time);
            vh.message.setText(m.text);
        } else if (holder instanceof ToolViewHolder) {
            ToolViewHolder vh = (ToolViewHolder) holder;
            vh.chip.setText(m.text);
        } else if (holder instanceof SuggestionViewHolder) {
            SuggestionViewHolder vh = (SuggestionViewHolder) holder;
            vh.btnSwitch.setOnClickListener(v -> {
                if (suggestionListener != null) suggestionListener.onSwitchToAgent(holder.getAdapterPosition());
            });
            vh.btnStay.setOnClickListener(v -> {
                if (suggestionListener != null) suggestionListener.onStayInChat(holder.getAdapterPosition());
            });
        }

        if (position > lastAnimatedPosition) {
            holder.itemView.setAlpha(0f);
            holder.itemView.setTranslationY(12f);
            holder.itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            lastAnimatedPosition = position;
        }
    }

    @Override
    public int getItemCount() {
        return messages.size() + (isTyping ? 1 : 0);
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView label, message;
        UserViewHolder(View v) {
            super(v);
            label = v.findViewById(R.id.tv_label);
            message = v.findViewById(R.id.tv_message);
        }
    }

    static class AssistantViewHolder extends RecyclerView.ViewHolder {
        TextView label, message;
        AssistantViewHolder(View v) {
            super(v);
            label = v.findViewById(R.id.tv_label);
            message = v.findViewById(R.id.tv_message);
        }
    }

    static class ToolViewHolder extends RecyclerView.ViewHolder {
        com.google.android.material.chip.Chip chip;
        ToolViewHolder(View v) {
            super(v);
            chip = v.findViewById(R.id.chip_tool);
        }
    }

    static class SuggestionViewHolder extends RecyclerView.ViewHolder {
        View btnSwitch, btnStay;
        SuggestionViewHolder(View v) {
            super(v);
            btnSwitch = v.findViewById(R.id.btn_switch);
            btnStay = v.findViewById(R.id.btn_stay);
        }
    }

    static class TypingViewHolder extends RecyclerView.ViewHolder {
        View dot1, dot2, dot3;
        TypingViewHolder(View v) {
            super(v);
            dot1 = v.findViewById(R.id.dot1);
            dot2 = v.findViewById(R.id.dot2);
            dot3 = v.findViewById(R.id.dot3);
            animate(dot1, 0);
            animate(dot2, 120);
            animate(dot3, 240);
        }
        private void animate(View v, int delay) {
            ObjectAnimator anim = ObjectAnimator.ofFloat(v, "translationY", 0, -12, 0);
            anim.setDuration(600);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setStartDelay(delay);
            anim.start();
        }
    }
}
