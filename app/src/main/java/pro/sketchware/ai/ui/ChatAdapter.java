package pro.sketchware.ai.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
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
    private static final int TYPE_ERROR = 5;
    private static final int TYPE_THINKING = 6;

    private final List<ChatStore.Message> messages = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private OnSuggestionListener suggestionListener;
    private OnErrorActionListener onErrorActionListener;
    private OnMessageActionListener onMessageActionListener;
    private String currentSpeakingText;

    public void setCurrentSpeakingText(String text) {
        this.currentSpeakingText = text;
        notifyDataSetChanged();
    }

    public interface OnMessageActionListener {
        void onCopy(ChatStore.Message message);
        void onEdit(ChatStore.Message message, int position);
        void onBranch(ChatStore.Message message, int position);
        void onShare(ChatStore.Message message);
        void onRegenerate(ChatStore.Message message, int position);
        void onVoice(ChatStore.Message message, View btnVoice);
    }

    public void setOnMessageActionListener(OnMessageActionListener listener) {
        this.onMessageActionListener = listener;
    }

    public interface OnErrorActionListener {
        void onAction(ChatStore.Message message);
        void onLongClick(ChatStore.Message message);
    }

    public void setOnErrorActionListener(OnErrorActionListener listener) {
        this.onErrorActionListener = listener;
    }
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

    public void insertMessage(int pos, ChatStore.Message message) {
        if (pos >= 0 && pos <= messages.size()) {
            messages.add(pos, message);
            notifyItemInserted(pos);
        }
    }

    public void setTyping(boolean typing) {
        if (isTyping == typing) return;
        isTyping = typing;
        if (typing) notifyItemInserted(messages.size());
        else notifyItemRemoved(messages.size());
    }

    public void appendToken(int pos, String token) {
        if (pos >= 0 && pos < messages.size()) {
            messages.get(pos).text += token;
            notifyItemChanged(pos, "token");
        }
    }

    public void appendReasoning(int pos, String token) {
        if (pos >= 0 && pos < messages.size()) {
            ChatStore.Message m = messages.get(pos);
            m.reasoning = (m.reasoning == null ? "" : m.reasoning) + token;
            notifyItemChanged(pos, "reasoning");
        }
    }

    public void completeThinking(int pos, long seconds) {
        if (pos >= 0 && pos < messages.size()) {
            ChatStore.Message m = messages.get(pos);
            m.role = "thought";
            m.reasoningSeconds = (int) Math.max(1, seconds);
            notifyItemChanged(pos, "think_done");
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == messages.size() && isTyping) return TYPE_TYPING;
        ChatStore.Message m = messages.get(position);
        if ("user".equals(m.role)) return TYPE_USER;
        if ("tool".equals(m.role)) return TYPE_TOOL;
        if ("suggestion".equals(m.role)) return TYPE_SUGGESTION;
        if ("error".equals(m.role)) return TYPE_ERROR;
        if ("thinking".equals(m.role) || "thought".equals(m.role)) return TYPE_THINKING;
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
        } else if (viewType == TYPE_ERROR) {
            return new ErrorViewHolder(inflater.inflate(R.layout.item_chat_error, parent, false));
        } else if (viewType == TYPE_THINKING) {
            return new ThinkingViewHolder(inflater.inflate(R.layout.item_chat_thinking, parent, false));
        } else {
            return new AssistantViewHolder(inflater.inflate(R.layout.item_chat_assistant, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        onBindViewHolder(holder, position, new ArrayList<>());
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (position >= messages.size()) return;
        ChatStore.Message m = messages.get(position);
        
        if (!payloads.isEmpty()) {
            if (holder instanceof AssistantViewHolder) {
                AssistantViewHolder vh = (AssistantViewHolder) holder;
                if (payloads.contains("token")) {
                    if (m.text.length() > vh.boundLen) {
                        vh.message.append(m.text.substring(vh.boundLen));
                        vh.boundLen = m.text.length();
                    }
                }
            } else if (holder instanceof ThinkingViewHolder) {
                ThinkingViewHolder vh = (ThinkingViewHolder) holder;
                if (payloads.contains("reasoning")) {
                    vh.tvFull.setText(m.reasoning);
                    vh.updatePreview(m.reasoning);
                    if (vh.isExpanded) {
                        vh.scrollThink.post(() -> vh.scrollThink.fullScroll(View.FOCUS_DOWN));
                    }
                }
                if (payloads.contains("think_done")) {
                    vh.bind(m); // Full bind is safer for completion state change
                }
            }
            return;
        }

        String time = timeFormat.format(new Date(m.time));

        if (holder instanceof UserViewHolder) {
            UserViewHolder vh = (UserViewHolder) holder;
            vh.label.setText("You • " + time);
            vh.message.setText(m.text);
            setupActions(vh.actionsRow, vh.btnCopy, vh.btnEdit, vh.btnBranch, vh.btnShare, null, null, m, position);
        } else if (holder instanceof AssistantViewHolder) {
            AssistantViewHolder vh = (AssistantViewHolder) holder;
            vh.label.setText("Assistant • " + time);
            vh.message.setText(m.text);
            vh.boundLen = m.text.length();
            if (vh.btnVoice instanceof android.widget.ImageButton) {
                ((android.widget.ImageButton) vh.btnVoice).setImageResource(
                        m.text.equals(currentSpeakingText) ? R.drawable.ic_msg_voice_stop : R.drawable.ic_msg_voice);
            }
            setupActions(vh.actionsRow, vh.btnCopy, null, vh.btnBranch, vh.btnShare, vh.btnRegenerate, vh.btnVoice, m, position);
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
        } else if (holder instanceof ErrorViewHolder) {
            ErrorViewHolder vh = (ErrorViewHolder) holder;
            vh.label.setText("Error • " + time);
            vh.message.setText(m.text);
            vh.btnAction.setText(m.action != null ? m.action : "Open settings");
            vh.btnAction.setOnClickListener(v -> {
                if (onErrorActionListener != null) onErrorActionListener.onAction(m);
            });
            vh.itemView.setOnLongClickListener(v -> {
                if (onErrorActionListener != null) onErrorActionListener.onLongClick(m);
                return true;
            });
            vh.btnCopy.setOnClickListener(v -> {
                if (onMessageActionListener != null) onMessageActionListener.onCopy(m);
            });
        } else if (holder instanceof ThinkingViewHolder) {
            ((ThinkingViewHolder) holder).bind(m);
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

    private void setupActions(View row, View copy, View edit, View branch, View share, View regen, View voice, ChatStore.Message m, int pos) {
        row.setAlpha(0f);
        row.animate().alpha(1f).setDuration(300).setStartDelay(100).start();
        if (copy != null) copy.setOnClickListener(v -> { if (onMessageActionListener != null) onMessageActionListener.onCopy(m); });
        if (edit != null) edit.setOnClickListener(v -> { if (onMessageActionListener != null) onMessageActionListener.onEdit(m, pos); });
        if (branch != null) branch.setOnClickListener(v -> { if (onMessageActionListener != null) onMessageActionListener.onBranch(m, pos); });
        if (share != null) share.setOnClickListener(v -> { if (onMessageActionListener != null) onMessageActionListener.onShare(m); });
        if (regen != null) regen.setOnClickListener(v -> { if (onMessageActionListener != null) onMessageActionListener.onRegenerate(m, pos); });
        if (voice != null) voice.setOnClickListener(v -> { if (onMessageActionListener != null) onMessageActionListener.onVoice(m, voice); });
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof ThinkingViewHolder) {
            ((ThinkingViewHolder) holder).stopTimer();
        }
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return messages.size() + (isTyping ? 1 : 0);
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView label, message;
        View actionsRow, btnCopy, btnEdit, btnBranch, btnShare;
        UserViewHolder(View v) {
            super(v);
            label = v.findViewById(R.id.tv_label);
            message = v.findViewById(R.id.tv_message);
            actionsRow = v.findViewById(R.id.actions_row);
            btnCopy = v.findViewById(R.id.btn_copy);
            btnEdit = v.findViewById(R.id.btn_edit);
            btnBranch = v.findViewById(R.id.btn_branch);
            btnShare = v.findViewById(R.id.btn_share);
        }
    }

    static class AssistantViewHolder extends RecyclerView.ViewHolder {
        TextView label, message;
        View actionsRow, btnCopy, btnRegenerate, btnVoice, btnBranch, btnShare;
        int boundLen = 0;

        AssistantViewHolder(View v) {
            super(v);
            label = v.findViewById(R.id.tv_label);
            message = v.findViewById(R.id.tv_message);
            actionsRow = v.findViewById(R.id.actions_row);
            btnCopy = v.findViewById(R.id.btn_copy);
            btnRegenerate = v.findViewById(R.id.btn_regenerate);
            btnVoice = v.findViewById(R.id.btn_voice);
            btnBranch = v.findViewById(R.id.btn_branch);
            btnShare = v.findViewById(R.id.btn_share);
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

    static class ErrorViewHolder extends RecyclerView.ViewHolder {
        TextView label, message;
        com.google.android.material.button.MaterialButton btnAction;
        View btnCopy;
        ErrorViewHolder(View v) {
            super(v);
            label = v.findViewById(R.id.tv_label);
            message = v.findViewById(R.id.tv_message);
            btnAction = v.findViewById(R.id.btn_action);
            btnCopy = v.findViewById(R.id.btn_copy);
        }
    }

    static class ThinkingViewHolder extends RecyclerView.ViewHolder {
        com.google.android.material.progressindicator.CircularProgressIndicator progress;
        android.widget.ImageView ivSparkle;
        TextView tvTitle, tvSeconds, tvPreview, tvFull;
        android.widget.ImageButton btnExpand;
        android.widget.ScrollView scrollThink;
        View card;
        
        private final Handler timerHandler = new Handler(Looper.getMainLooper());
        private Runnable timerRunnable;
        private int seconds = 0;
        private boolean isExpanded = false;
        private ChatStore.Message currentMessage;

        ThinkingViewHolder(View v) {
            super(v);
            card = v.findViewById(R.id.card_thinking);
            progress = v.findViewById(R.id.progress_think);
            ivSparkle = v.findViewById(R.id.iv_sparkle);
            tvTitle = v.findViewById(R.id.tv_think_title);
            tvSeconds = v.findViewById(R.id.tv_think_seconds);
            tvPreview = v.findViewById(R.id.tv_think_preview);
            tvFull = v.findViewById(R.id.tv_think_full);
            btnExpand = v.findViewById(R.id.btn_think_expand);
            scrollThink = v.findViewById(R.id.scroll_think);

            btnExpand.setOnClickListener(view -> toggleExpand());
            card.setOnClickListener(view -> toggleExpand());
        }

        void bind(ChatStore.Message m) {
            currentMessage = m;
            boolean isStreaming = "thinking".equals(m.role) && (m.text == null || m.text.isEmpty());
            // If it's a history item, role is assistant but reasoning is present
            // Actually AssistantFragment will handle inserting the thinking role message.
            // For historical items, we'll see if they have reasoning.
            
            boolean completed = m.reasoningSeconds > 0 || (m.reasoning != null && !m.reasoning.isEmpty() && !"thinking".equals(m.role));
            
            tvFull.setText(m.reasoning);
            updatePreview(m.reasoning);
            
            if (completed) {
                stopTimer();
                seconds = m.reasoningSeconds;
                tvSeconds.setText(seconds + "s");
                tvTitle.setText("Thought for " + seconds + "s");
                
                if (progress.getVisibility() == View.VISIBLE) {
                    progress.animate().alpha(0f).setDuration(200).withEndAction(() -> progress.setVisibility(View.GONE)).start();
                    ivSparkle.setAlpha(0f);
                    ivSparkle.setVisibility(View.VISIBLE);
                    ivSparkle.animate().alpha(1f).setDuration(200).start();
                } else {
                    progress.setVisibility(View.GONE);
                    ivSparkle.setVisibility(View.VISIBLE);
                    ivSparkle.setAlpha(1f);
                }
                btnExpand.setRotation(isExpanded ? 90 : 0);
            } else {
                ivSparkle.setVisibility(View.GONE);
                progress.setVisibility(View.VISIBLE);
                progress.setAlpha(1f);
                tvTitle.setText("Thinking…");
                startTimer();
                btnExpand.setRotation(isExpanded ? 90 : 0);
            }
            
            scrollThink.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            tvPreview.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
            
            if (isExpanded && !completed) {
                scrollThink.post(() -> scrollThink.fullScroll(View.FOCUS_DOWN));
            }
        }

        private void updatePreview(String reasoning) {
            if (reasoning == null || reasoning.isEmpty()) {
                tvPreview.setText("");
                return;
            }
            int len = reasoning.length();
            String preview = len > 140 ? reasoning.substring(len - 140) : reasoning;
            tvPreview.setText(preview);
        }

        private void startTimer() {
            if (timerRunnable != null) return;
            seconds = 0;
            timerRunnable = new Runnable() {
                @Override
                public void run() {
                    seconds++;
                    tvSeconds.setText(seconds + "s");
                    timerHandler.postDelayed(this, 1000);
                }
            };
            timerHandler.postDelayed(timerRunnable, 1000);
        }

        private void stopTimer() {
            if (timerRunnable != null) {
                timerHandler.removeCallbacks(timerRunnable);
                timerRunnable = null;
            }
        }

        private void toggleExpand() {
            isExpanded = !isExpanded;
            
            btnExpand.animate().rotation(isExpanded ? 90 : 0).setDuration(150).start();
            
            boolean isStreaming = "thinking".equals(currentMessage.role);
            
            if (isExpanded) {
                scrollThink.setVisibility(View.VISIBLE);
                tvPreview.setVisibility(View.GONE);
                if (isStreaming) {
                    scrollThink.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    scrollThink.requestLayout();
                } else {
                    animateHeight(true);
                }
            } else {
                if (isStreaming) {
                    scrollThink.setVisibility(View.GONE);
                    tvPreview.setVisibility(View.VISIBLE);
                } else {
                    animateHeight(false);
                }
            }
        }

        private void animateHeight(boolean expanding) {
            scrollThink.measure(
                View.MeasureSpec.makeMeasureSpec(card.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            int targetHeight = Math.min(scrollThink.getMeasuredHeight(), (int)(240 * card.getContext().getResources().getDisplayMetrics().density));
            
            ValueAnimator anim = ValueAnimator.ofInt(expanding ? 0 : targetHeight, expanding ? targetHeight : 0);
            anim.addUpdateListener(animation -> {
                ViewGroup.LayoutParams lp = scrollThink.getLayoutParams();
                lp.height = (int) animation.getAnimatedValue();
                scrollThink.setLayoutParams(lp);
            });
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (!expanding) {
                        scrollThink.setVisibility(View.GONE);
                        tvPreview.setVisibility(View.VISIBLE);
                    }
                    ViewGroup.LayoutParams lp = scrollThink.getLayoutParams();
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    scrollThink.setLayoutParams(lp);
                }
            });
            anim.setDuration(200);
            anim.setInterpolator(new FastOutSlowInInterpolator());
            anim.start();
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
