package pro.sketchware.ai.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
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
    private static final int TYPE_TOOLS_CARD = 7;
    private static final int TYPE_RATE_LIMIT = 8;

    private final List<ChatStore.Message> messages = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private OnSuggestionListener suggestionListener;
    private OnErrorActionListener onErrorActionListener;
    private OnMessageActionListener onMessageActionListener;
    private OnRateLimitActionListener onRateLimitActionListener;
    private String currentSpeakingText;

    public void setCurrentSpeakingText(String text) {
        this.currentSpeakingText = text;
        notifyDataSetChanged();
    }

    public interface OnRateLimitActionListener {
        void onRetryNow();
        void onCancel();
        void onRetryExhausted();
    }

    public void setOnRateLimitActionListener(OnRateLimitActionListener listener) {
        this.onRateLimitActionListener = listener;
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
        if (token == null || token.isEmpty()) return;
        if (pos >= 0 && pos < messages.size()) {
            ChatStore.Message m = messages.get(pos);
            m.text = (m.text == null ? "" : m.text) + token;
            notifyItemChanged(pos, "token");
        }
    }

    public void appendReasoning(int pos, String token) {
        if (token == null || token.isEmpty()) return;
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
        if ("tools_card".equals(m.role)) return TYPE_TOOLS_CARD;
        if ("rate_limit".equals(m.role)) return TYPE_RATE_LIMIT;
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
        } else if (viewType == TYPE_TOOLS_CARD) {
            return new ToolsViewHolder(inflater.inflate(R.layout.item_chat_tools, parent, false));
        } else if (viewType == TYPE_RATE_LIMIT) {
            return new RateLimitViewHolder(inflater.inflate(R.layout.item_chat_ratelimit, parent, false));
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
            vh.message.setText(m.text == null ? "" : m.text);
            setupActions(vh.actionsRow, vh.btnCopy, vh.btnEdit, vh.btnBranch, vh.btnShare, null, null, m, position);
        } else if (holder instanceof AssistantViewHolder) {
            AssistantViewHolder vh = (AssistantViewHolder) holder;
            vh.label.setText("Assistant • " + time);
            vh.message.setText(m.text == null ? "" : m.text);
            vh.boundLen = (m.text == null) ? 0 : m.text.length();
            if (vh.btnVoice instanceof android.widget.ImageButton) {
                String safeText = m.text == null ? "" : m.text;
                ((android.widget.ImageButton) vh.btnVoice).setImageResource(
                        safeText.equals(currentSpeakingText) ? R.drawable.ic_msg_voice_stop : R.drawable.ic_msg_voice);
            }
            setupActions(vh.actionsRow, vh.btnCopy, null, vh.btnBranch, vh.btnShare, vh.btnRegenerate, vh.btnVoice, m, position);
        } else if (holder instanceof ToolViewHolder) {
            ToolViewHolder vh = (ToolViewHolder) holder;
            vh.chip.setText(m.text == null ? "" : m.text);
        } else if (holder instanceof SuggestionViewHolder) {
            SuggestionViewHolder vh = (SuggestionViewHolder) holder;
            if ("locked".equals(m.text)) {
                vh.title.setText(R.string.ai_agent_locked_card);
                vh.btnSwitch.setText(R.string.ai_enable_and_run);
                vh.btnStay.setText(R.string.ai_keep_chat);
                vh.icon.setImageResource(R.drawable.ic_mtrl_wrench);
            } else {
                vh.title.setText(R.string.ai_agent_suggestion_title);
                vh.btnSwitch.setText(R.string.ai_agent_switch);
                vh.btnStay.setText(R.string.ai_agent_stay);
                vh.icon.setImageResource(R.drawable.ic_mtrl_sparkle);
            }
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
        } else if (holder instanceof ToolsViewHolder) {
            ((ToolsViewHolder) holder).bind(m, payloads.contains("tool_events"));
        } else if (holder instanceof RateLimitViewHolder) {
            ((RateLimitViewHolder) holder).bind(m, payloads, onRateLimitActionListener);
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

    static class ToolsViewHolder extends RecyclerView.ViewHolder {
        TextView tvCount, tvSummary;
        View header;
        ImageView ivChevron;
        ViewGroup rowsContainer;
        boolean isExpanded = true;
        ChatStore.Message currentMsg;

        ToolsViewHolder(View v) {
            super(v);
            tvCount = v.findViewById(R.id.ai_tools_count);
            tvSummary = v.findViewById(R.id.ai_tools_summary);
            header = v.findViewById(R.id.ai_tools_header);
            ivChevron = v.findViewById(R.id.ai_tools_chevron);
            rowsContainer = v.findViewById(R.id.ai_tools_rows_container);
            
            header.setOnClickListener(view -> toggleExpand());
        }

        void bind(ChatStore.Message m, boolean isPartial) {
            currentMsg = m;
            List<ChatStore.ToolEvent> events = m.toolEvents;
            if (events == null) events = new ArrayList<>();
            
            tvCount.setText("(" + events.size() + ")");
            
            boolean anyRunning = false;
            long totalMs = 0;
            for (ChatStore.ToolEvent te : events) {
                if ("RUNNING".equals(te.status)) anyRunning = true;
                if (te.finishedAt > 0) totalMs += (te.finishedAt - te.startedAt);
            }
            
            if (!anyRunning && !events.isEmpty()) {
                tvSummary.setVisibility(View.VISIBLE);
                tvSummary.setText(String.format(Locale.getDefault(), "%d tools • %.1fs", events.size(), totalMs / 1000f));
                if (isExpanded && !isPartial) {
                    // Auto-collapse when done, if this is an old message being bound
                    if (m.time < System.currentTimeMillis() - 500) {
                         isExpanded = false;
                    }
                }
            } else {
                tvSummary.setVisibility(View.GONE);
            }

            if (!isPartial) {
                rowsContainer.removeAllViews();
            }
            updateRows(events);
            
            ivChevron.setRotation(isExpanded ? 0 : -90);
            rowsContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        }

        private void updateRows(List<ChatStore.ToolEvent> events) {
            int childCount = rowsContainer.getChildCount();
            LayoutInflater inflater = LayoutInflater.from(rowsContainer.getContext());
            
            for (int i = 0; i < events.size(); i++) {
                ChatStore.ToolEvent te = events.get(i);
                View row;
                if (i < childCount) {
                    row = rowsContainer.getChildAt(i);
                } else {
                    row = inflater.inflate(R.layout.item_tool_execution, rowsContainer, false);
                    rowsContainer.addView(row);
                }
                
                TextView name = row.findViewById(R.id.ai_tool_name);
                View progress = row.findViewById(R.id.ai_tool_progress);
                ImageView status = row.findViewById(R.id.ai_tool_status);
                
                name.setText(te.name);
                if ("RUNNING".equals(te.status)) {
                    progress.setVisibility(View.VISIBLE);
                    status.setVisibility(View.GONE);
                } else {
                    progress.setVisibility(View.GONE);
                    status.setVisibility(View.VISIBLE);
                    boolean ok = "OK".equals(te.status);
                    status.setImageResource(ok ? R.drawable.ic_check_circle : R.drawable.ic_error_circle);
                    status.setColorFilter(ok ? 0xFF4CAF50 : 0xFFF44336);
                }
            }
        }

        private void toggleExpand() {
            toggleExpand(!isExpanded);
        }

        private void toggleExpand(boolean expand) {
            if (isExpanded == expand) return;
            isExpanded = expand;
            ivChevron.animate().rotation(isExpanded ? 0 : -90).setDuration(200).start();
            
            if (isExpanded) {
                rowsContainer.setVisibility(View.VISIBLE);
                rowsContainer.setAlpha(0f);
                rowsContainer.animate().alpha(1f).setDuration(200).start();
            } else {
                rowsContainer.animate().alpha(0f).setDuration(200).withEndAction(() -> rowsContainer.setVisibility(View.GONE)).start();
            }
        }
    }

    static class RateLimitViewHolder extends RecyclerView.ViewHolder {
        com.google.android.material.card.MaterialCardView card;
        ImageView ivIcon;
        TextView tvTitle, tvCount, tvSub, tvFinal;
        com.google.android.material.progressindicator.LinearProgressIndicator progress;
        View actions, btnRetry, btnCancel;
        ObjectAnimator pulse;

        RateLimitViewHolder(View v) {
            super(v);
            card = v.findViewById(R.id.card_rate);
            ivIcon = v.findViewById(R.id.iv_rate_icon);
            tvTitle = v.findViewById(R.id.tv_rate_title);
            tvCount = v.findViewById(R.id.tv_rate_count);
            tvSub = v.findViewById(R.id.tv_rate_sub);
            tvFinal = v.findViewById(R.id.tv_rate_final);
            progress = v.findViewById(R.id.prog_rate);
            actions = v.findViewById(R.id.rate_actions);
            btnRetry = v.findViewById(R.id.btn_rate_now);
            btnCancel = v.findViewById(R.id.btn_rate_cancel);

            pulse = ObjectAnimator.ofFloat(ivIcon, "alpha", 0.55f, 1.0f);
            pulse.setDuration(1200);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
        }

        void bind(ChatStore.Message m, List<Object> payloads, OnRateLimitActionListener listener) {
            if (!payloads.isEmpty() && payloads.contains("rate_tick")) {
                updateTick(m);
                return;
            }

            // Full bind
            String status = m.toolState; // Borrowing for "WAITING", "RESUMED", "CANCELLED", "EXHAUSTED"
            if (status == null) status = "WAITING";

            if ("WAITING".equals(status)) {
                card.setCardBackgroundColor(pro.sketchware.utility.ThemeUtils.getColor(itemView.getContext(), R.attr.colorTertiaryContainer));
                tvTitle.setText(R.string.ai_rate_title);
                tvTitle.setVisibility(View.VISIBLE);
                tvCount.setVisibility(View.VISIBLE);
                progress.setVisibility(View.VISIBLE);
                tvSub.setVisibility(View.VISIBLE);
                actions.setVisibility(View.VISIBLE);
                tvFinal.setVisibility(View.GONE);
                if (!pulse.isRunning()) pulse.start();
                updateTick(m);
            } else {
                pulse.cancel();
                ivIcon.setAlpha(1.0f);
                tvCount.setVisibility(View.GONE);
                progress.setVisibility(View.GONE);
                tvSub.setVisibility(View.GONE);
                actions.setVisibility(View.GONE);
                tvFinal.setVisibility(View.VISIBLE);

                if ("RESUMED".equals(status)) {
                    ivIcon.setImageResource(R.drawable.ic_check_circle);
                    tvTitle.setText(R.string.ai_rate_resumed);
                    tvFinal.setVisibility(View.GONE);
                    // Subtle line transition
                } else if ("COLLAPSED".equals(status)) {
                    ivIcon.setImageResource(R.drawable.ic_check_circle);
                    ivIcon.setVisibility(View.VISIBLE);
                    tvTitle.setVisibility(View.GONE);
                    tvFinal.setVisibility(View.VISIBLE);
                    tvFinal.setText(R.string.ai_rate_resumed);
                    card.setCardBackgroundColor(pro.sketchware.utility.ThemeUtils.getColor(itemView.getContext(), android.R.attr.colorBackground));
                    // Add alpha 0.5 to card
                    card.setAlpha(0.5f);
                } else if ("CANCELLED".equals(status)) {
                    ivIcon.setImageResource(R.drawable.ic_hourglass);
                    tvTitle.setVisibility(View.GONE);
                    tvFinal.setText(R.string.ai_rate_cancelled);
                } else if ("EXHAUSTED".equals(status)) {
                    card.setCardBackgroundColor(pro.sketchware.utility.ThemeUtils.getColor(itemView.getContext(), R.attr.colorErrorContainer));
                    ivIcon.setImageResource(R.drawable.ic_error_circle);
                    ivIcon.setColorFilter(0xFFF44336);
                    tvTitle.setText(String.format(itemView.getContext().getString(R.string.ai_rate_exhausted), 3));
                    tvFinal.setVisibility(View.GONE);
                    // Actions might be needed for exhausted state: [Try again][Switch model]
                    // But contract said "Switch to neutral one-liner" for cancel.
                    // For exhausted: title "Still crowded..." + actions [Try again][Switch model][Open settings].
                    actions.setVisibility(View.VISIBLE);
                    ((com.google.android.material.button.MaterialButton)btnRetry).setText("Try again");
                    ((com.google.android.material.button.MaterialButton)btnCancel).setText("Settings");
                }
            }

            btnRetry.setOnClickListener(v -> { if (listener != null) listener.onRetryNow(); });
            btnCancel.setOnClickListener(v -> {
                if (listener != null) {
                    if ("EXHAUSTED".equals(m.toolState)) {
                         // Open settings
                         listener.onRetryExhausted();
                    } else {
                        listener.onCancel();
                    }
                }
            });
        }

        private void updateTick(ChatStore.Message m) {
            // Data stored in m.text as "elapsed|totalWait|attempt|max"
            try {
                String[] parts = m.text.split("\\|");
                long elapsed = Long.parseLong(parts[0]);
                long total = Long.parseLong(parts[1]);
                int attempt = Integer.parseInt(parts[2]);
                int max = Integer.parseInt(parts[3]);

                long remaining = Math.max(0, (total - elapsed) / 1000);
                tvCount.setText(String.format(Locale.getDefault(), "%02d:%02d", remaining / 60, remaining % 60));
                progress.setProgress((int) (elapsed * 100 / total));
                tvSub.setText(itemView.getContext().getString(R.string.ai_rate_retry, attempt, max, (total / 1000)));
            } catch (Exception ignored) {}
        }
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
        android.widget.TextView title;
        android.widget.ImageView icon;
        com.google.android.material.button.MaterialButton btnSwitch, btnStay;
        SuggestionViewHolder(View v) {
            super(v);
            title = v.findViewById(R.id.tv_title);
            icon = v.findViewById(R.id.iv_icon);
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
