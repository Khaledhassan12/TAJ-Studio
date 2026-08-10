package pro.sketchware.ai.ui;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import pro.sketchware.R;
import pro.sketchware.ai.core.*;
import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.data.Paths;
import pro.sketchware.ai.models.ModelManager;
import pro.sketchware.ai.providers.cloud.AnthropicProvider;
import pro.sketchware.ai.providers.cloud.GeminiProvider;
import pro.sketchware.ai.providers.cloud.OpenAiProvider;
import pro.sketchware.ai.providers.local.LlamaProvider;
import pro.sketchware.ai.runtime.RuntimeClient;
import pro.sketchware.utility.ThemeUtils;
import android.content.Context;
import androidx.core.content.ContextCompat;

/**
 * [WHAT] Unified chat interface for both local and cloud AI.
 * [WHY] Drives all AI interactions through a single UI pipeline.
 * [HOW] Routes message logic through selected AiProvider. Persists history in AiStorage.
 */
public class SessionFragment extends Fragment {

    private RecyclerView recycler;
    private ChatAdapter adapter;
    private EditText edMessage;
    private View btnSend;
    private MaterialButton btnCancel;
    private MaterialButton btnProviderSelect;
    private TextView tvActiveModel;
    
    private RuntimeClient runtimeClient;
    private String conversationId;
    private String scId;
    private AiProvider activeProvider;
    private String activeModelId;
    private StreamHandle activeStream;
    private pro.sketchware.ai.agent.AgentManager agentManager;
    private Context appContext;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        appContext = context.getApplicationContext();
    }

    private static class ChatMessage {
        String id;
        String role;
        String content;
        String thought = "";
        Integer totalTokens;

        ChatMessage(String role, String content) { 
            this.id = UUID.randomUUID().toString();
            this.role = role; 
            this.content = content; 
        }
        ChatMessage(String id, String role, String content) {
            this.id = id;
            this.role = role;
            this.content = content;
        }
        AiMessage toAiMessage() {
            return new AiMessage(AiMessage.Role.valueOf(role), content);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_session, container, false);
    }

    public static SessionFragment newInstance(String initialMessage) {
        SessionFragment fragment = new SessionFragment();
        Bundle args = new Bundle();
        args.putString("initial_message", initialMessage);
        fragment.setArguments(args);
        return fragment;
    }

    public static SessionFragment newInstanceWithHistory(String conversationId) {
        SessionFragment fragment = new SessionFragment();
        Bundle args = new Bundle();
        args.putString("conversation_id", conversationId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        scId = com.besome.sketch.design.DesignActivity.sc_id;
        runtimeClient = new RuntimeClient(appContext);
        runtimeClient.bind();
        agentManager = new pro.sketchware.ai.agent.AgentManager(appContext);

        recycler = view.findViewById(R.id.recycler_messages);
        edMessage = view.findViewById(R.id.ed_message);
        btnSend = view.findViewById(R.id.btn_send);
        btnCancel = view.findViewById(R.id.btn_cancel);
        btnProviderSelect = view.findViewById(R.id.btn_provider_select);
        tvActiveModel = view.findViewById(R.id.tv_active_model);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ChatAdapter();
        recycler.setAdapter(adapter);

        initProviders();
        
        String forcedConvId = getArguments() != null ? getArguments().getString("conversation_id") : null;
        if (forcedConvId != null) {
            conversationId = forcedConvId;
            AiStorage.get(appContext).kvPut("active_conv_" + scId, conversationId);
        }

        initConversation();
        loadHistory();
        renderActiveSelection();

        btnSend.setOnClickListener(v -> sendMessage());
        btnCancel.setOnClickListener(v -> cancelGeneration());
        btnProviderSelect.setOnClickListener(this::showProviderMenu);

        String initial = getArguments() != null ? getArguments().getString("initial_message") : null;
        if (initial != null) {
            edMessage.setText(initial);
            sendMessage();
            getArguments().remove("initial_message");
        }
    }

    public void sendSystemBiasedMessage(String message) {
        edMessage.setText(message);
        sendMessage();
    }

    private void initProviders() {
        // No longer clearing or manually registering cloud providers here.
        // We use ProviderRegistry.get(context) as the SSOT.
        
        // Local llama provider still needs the runtimeClient
        activeProvider = new pro.sketchware.ai.providers.local.LlamaProvider(runtimeClient);
    }

    private void initConversation() {
        AiStorage storage = AiStorage.get(appContext);
        conversationId = storage.kvGet("active_conv_" + scId);
        if (conversationId == null) {
            conversationId = UUID.randomUUID().toString();
            ContentValues cv = new ContentValues();
            cv.put("id", conversationId);
            cv.put("scId", scId);
            cv.put("title", "New Chat");
            cv.put("createdAt", System.currentTimeMillis());
            storage.insertConversation(cv);
            storage.kvPut("active_conv_" + scId, conversationId);

            // Preselect default model (Step 4)
            pro.sketchware.ai.models.ModelCatalog.ModelEntry def = pro.sketchware.ai.models.ModelCatalog.get(appContext).getDefaultModel();
            if (def != null) {
                activeModelId = def.modelId;
                if (def.isLocal) {
                    activeProvider = new pro.sketchware.ai.providers.local.LlamaProvider(runtimeClient);
                } else {
                    pro.sketchware.ai.providers.ProviderConfig cfg = pro.sketchware.ai.providers.ProviderRegistry.get(appContext).findById(def.providerId);
                    if (cfg != null) {
                        activeProvider = pro.sketchware.ai.providers.ProviderRegistry.get(appContext).providerFor(cfg, null);
                    }
                }
                if (activeProvider != null) {
                    storage.kvPut("conv_provider_" + conversationId, activeProvider.id());
                    storage.kvPut("conv_model_" + conversationId, activeModelId);
                }
            }
        } else {
            String pId = storage.kvGet("conv_provider_" + conversationId);
            if (pId != null) {
                if ("local-llama".equals(pId)) {
                    activeProvider = new pro.sketchware.ai.providers.local.LlamaProvider(runtimeClient);
                } else {
                    pro.sketchware.ai.providers.ProviderConfig cfg = pro.sketchware.ai.providers.ProviderRegistry.get(appContext).findById(pId);
                    if (cfg != null) {
                        activeProvider = pro.sketchware.ai.providers.ProviderRegistry.get(appContext).providerFor(cfg, null);
                    }
                }
            }
            activeModelId = storage.kvGet("conv_model_" + conversationId);
        }
        if (activeProvider == null) activeProvider = new pro.sketchware.ai.providers.local.LlamaProvider(runtimeClient);
    }

    private void renderActiveSelection() {
        if (activeProvider != null) {
            btnProviderSelect.setText(activeProvider.name());
        }
        tvActiveModel.setText(activeModelId != null ? activeModelId : "No model selected");
    }

    private void showProviderMenu(View v) {
        PopupMenu popup = new PopupMenu(appContext, v);
        List<pro.sketchware.ai.models.ModelCatalog.ModelEntry> usable = pro.sketchware.ai.models.ModelCatalog.get(appContext).usableModels();

        for (int i = 0; i < usable.size(); i++) {
            pro.sketchware.ai.models.ModelCatalog.ModelEntry e = usable.get(i);
            popup.getMenu().add(0, i, 0, e.alias + " (" + e.providerId + ")");
        }

        popup.setOnMenuItemClickListener(item -> {
            pro.sketchware.ai.models.ModelCatalog.ModelEntry e = usable.get(item.getItemId());
            activeModelId = e.modelId;

            if (e.isLocal) {
                activeProvider = new pro.sketchware.ai.providers.local.LlamaProvider(runtimeClient);
            } else {
                pro.sketchware.ai.providers.ProviderConfig cfg = pro.sketchware.ai.providers.ProviderRegistry.get(appContext).findById(e.providerId);
                if (cfg != null) {
                    activeProvider = pro.sketchware.ai.providers.ProviderRegistry.get(appContext).providerFor(cfg, null);
                }
            }

            if (activeProvider != null) {
                AiStorage.get(appContext).kvPut("conv_provider_" + conversationId, activeProvider.id());
                AiStorage.get(appContext).kvPut("conv_model_" + conversationId, activeModelId);
            }
            renderActiveSelection();
            return true;
        });
        popup.show();
    }

    private void loadHistory() {
        AiStorage storage = AiStorage.get(appContext);
        adapter.clear();
        try (Cursor c = storage.listMessages(conversationId)) {
            while (c.moveToNext()) {
                String id = c.getString(c.getColumnIndexOrThrow("id"));
                String role = c.getString(c.getColumnIndexOrThrow("role"));
                String content = c.getString(c.getColumnIndexOrThrow("content"));
                adapter.addMessage(new ChatMessage(id, role, content));
            }
        }
        
        // Also load agent steps if any
        try (Cursor c = storage.listAgentSteps(conversationId)) {
            while (c.moveToNext()) {
                // For P5, we could render these as special bubbles or logs
                // For now, we just ensure they are in the DB
            }
        }
        
        if (adapter.getItemCount() > 0) {
            recycler.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        runtimeClient.unbind();
    }

    private void sendMessage() {
        if (activeProvider == null || activeModelId == null) {
            Toast.makeText(appContext, "Select provider and model", Toast.LENGTH_SHORT).show();
            return;
        }

        String text = edMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        ChatMessage userMsg = new ChatMessage("user", text);
        persistMessage(userMsg);
        adapter.addMessage(userMsg);
        edMessage.setText("");
        
        ChatMessage assistantMsg = new ChatMessage("assistant", "");
        adapter.addMessage(assistantMsg);
        
        applyChatState("streaming");

        agentManager.runTurn(scId, conversationId, text, null, activeProvider, activeModelId, new pro.sketchware.ai.agent.AgentManager.AgentListener() {
            @Override
            public void onStep(pro.sketchware.ai.agent.AgentStep step) {
                if (!isAdded() || getActivity() == null || getActivity().isFinishing()) return;
                if (step.kind == pro.sketchware.ai.agent.AgentStep.Kind.TEXT) {
                    assistantMsg.content += step.payload;
                    adapter.notifyItemChanged(adapter.getItemCount() - 1);
                } else if (step.kind == pro.sketchware.ai.agent.AgentStep.Kind.THOUGHT) {
                    assistantMsg.thought += step.payload;
                    adapter.notifyItemChanged(adapter.getItemCount() - 1);
                } else if (step.kind == pro.sketchware.ai.agent.AgentStep.Kind.ERROR) {
                    String err = step.payload;
                    if (err != null && err.startsWith("LOCAL_CONTEXT_EXCEEDED:")) {
                        assistantMsg.content = mapContextError(err);
                    } else {
                        assistantMsg.content = "Error: " + err;
                    }
                    adapter.notifyItemChanged(adapter.getItemCount() - 1);
                }
            }

            @Override
            public void onDone(AiResponse usage) {
                if (usage != null) assistantMsg.totalTokens = usage.totalTokens;
                persistMessage(assistantMsg);
                if (!isAdded() || getActivity() == null || getActivity().isFinishing()) return;
                getActivity().runOnUiThread(() -> {
                    applyChatState("idle");
                    // Trigger title generation (Change 6)
                    new pro.sketchware.ai.conversations.ConversationTitleGenerator(appContext)
                            .maybeGenerateTitle(scId, conversationId, getAiHistory());
                });
            }
        });
    }

    private List<AiMessage> getAiHistory() {
        List<AiMessage> history = new ArrayList<>();
        for (ChatMessage m : adapter.getMessages()) {
            history.add(m.toAiMessage());
        }
        return history;
    }

    private String mapContextError(String raw) {
        try {
            String[] parts = raw.split(":");
            if (parts.length >= 3) {
                return "Local context exceeded (Prompt: " + parts[1] + ", Model limit: " + parts[2] + " tokens). Try reducing history or increasing context size.";
            }
        } catch (Exception ignored) {}
        return "Local context limit exceeded. Please shorten your prompt or history.";
    }

    private void cancelGeneration() {
        if (activeStream != null) activeStream.cancel();
        runtimeClient.cancel();
        applyChatState("idle");
    }

    private void applyChatState(String state) {
        if ("streaming".equals(state)) {
            btnSend.setVisibility(View.GONE);
            btnCancel.setVisibility(View.VISIBLE);
        } else {
            btnSend.setVisibility(View.VISIBLE);
            btnCancel.setVisibility(View.GONE);
        }
    }

    private void persistMessage(ChatMessage msg) {
        if (appContext == null) return;
        AiStorage storage = AiStorage.get(appContext);
        ContentValues cv = new ContentValues();
        cv.put("id", msg.id);
        cv.put("conversationId", conversationId);
        cv.put("role", msg.role);
        cv.put("content", msg.content);
        cv.put("createdAt", System.currentTimeMillis());
        storage.insertMessage(cv);
    }

    private class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
        private final List<ChatMessage> messages = new ArrayList<>();

        public void addMessage(ChatMessage m) {
            messages.add(m);
            notifyItemInserted(messages.size() - 1);
        }

        public void clear() {
            messages.clear();
            notifyDataSetChanged();
        }

        public List<ChatMessage> getMessages() { return messages; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_bubble, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatMessage m = messages.get(position);
            holder.text.setText(m.content);
            
            // Visualization (Step 6)
            pro.sketchware.ai.generation.GenerationDefaults gd = pro.sketchware.ai.generation.GenerationDefaults.get(appContext);
            if (gd.isVisualizeRollout()) {
                int ctxWindow = gd.getContextWindow();
                if (position < messages.size() - ctxWindow) {
                    holder.itemView.setAlpha(0.45f);
                } else {
                    holder.itemView.setAlpha(1f);
                }
            } else {
                holder.itemView.setAlpha(1f);
            }

            if (m.thought != null && !m.thought.isEmpty()) {
                holder.thoughtContainer.setVisibility(View.VISIBLE);
                holder.thoughtText.setText(m.thought);
                holder.thoughtText.setVisibility(View.GONE); // Initially collapsed
                holder.thoughtHeader.setOnClickListener(v -> {
                    boolean visible = holder.thoughtText.getVisibility() == View.VISIBLE;
                    holder.thoughtText.setVisibility(visible ? View.GONE : View.VISIBLE);
                    holder.thoughtHeader.setText(visible ? "Thinking... (show)" : "Thinking (hide)");
                });
            } else {
                holder.thoughtContainer.setVisibility(View.GONE);
            }

            if (m.totalTokens != null) {
                holder.meta.setVisibility(View.VISIBLE);
                holder.meta.setText("· " + m.totalTokens + " tok");
            } else {
                holder.meta.setVisibility(View.GONE);
            }

            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) holder.card.getLayoutParams();
            if ("user".equals(m.role)) {
                holder.root.setGravity(Gravity.END);
                holder.card.setStrokeColor(ContextCompat.getColor(appContext, R.color.taj_ai_primary));
                lp.gravity = Gravity.END;
                holder.thoughtContainer.setGravity(Gravity.END);
            } else {
                holder.root.setGravity(Gravity.START);
                holder.card.setStrokeColor(ContextCompat.getColor(appContext, R.color.taj_ai_accent));
                lp.gravity = Gravity.START;
                holder.thoughtContainer.setGravity(Gravity.START);
            }
            holder.card.setLayoutParams(lp);
        }

        @Override
        public int getItemCount() { return messages.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text, thoughtText, thoughtHeader, meta;
            LinearLayout thoughtContainer;
            MaterialCardView card;
            LinearLayout root;
            ViewHolder(View v) {
                super(v);
                text = v.findViewById(R.id.tv_message);
                thoughtText = v.findViewById(R.id.tv_thought);
                thoughtHeader = v.findViewById(R.id.tv_thought_header);
                thoughtContainer = (LinearLayout) v.findViewById(R.id.thought_container);
                meta = v.findViewById(R.id.tv_meta);
                card = v.findViewById(R.id.card_bubble);
                root = (LinearLayout) v.findViewById(R.id.chat_bubble_root);
            }
        }
    }
}
