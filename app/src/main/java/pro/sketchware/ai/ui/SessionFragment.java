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

    private static class ChatMessage {
        String id;
        String role;
        String content;
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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        scId = com.besome.sketch.design.DesignActivity.sc_id;
        runtimeClient = new RuntimeClient(requireContext());
        runtimeClient.bind();
        agentManager = new pro.sketchware.ai.agent.AgentManager(requireContext());

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
        initConversation();
        loadHistory();
        renderActiveSelection();

        btnSend.setOnClickListener(v -> sendMessage());
        btnCancel.setOnClickListener(v -> cancelGeneration());
        btnProviderSelect.setOnClickListener(this::showProviderMenu);
    }

    private void initProviders() {
        ProviderRegistry.clear();
        ProviderRegistry.register(new LlamaProvider(runtimeClient));
        
        pro.sketchware.ai.data.SecureKeyStore ks = pro.sketchware.ai.data.SecureKeyStore.get(requireContext());
        String openai = ks.getKey("openai");
        if (openai != null) ProviderRegistry.register(new OpenAiProvider(openai));
        
        String anthropic = ks.getKey("anthropic");
        if (anthropic != null) ProviderRegistry.register(new AnthropicProvider(anthropic));
        
        String gemini = ks.getKey("gemini");
        if (gemini != null) ProviderRegistry.register(new GeminiProvider(gemini));

        // Default
        activeProvider = ProviderRegistry.get("local-llama");
    }

    private void initConversation() {
        AiStorage storage = AiStorage.get(requireContext());
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
        } else {
            String pId = storage.kvGet("conv_provider_" + conversationId);
            if (pId != null) activeProvider = ProviderRegistry.get(pId);
            activeModelId = storage.kvGet("conv_model_" + conversationId);
        }
        if (activeProvider == null) activeProvider = ProviderRegistry.get("local-llama");
    }

    private void renderActiveSelection() {
        if (activeProvider != null) {
            btnProviderSelect.setText(activeProvider.name());
        }
        tvActiveModel.setText(activeModelId != null ? activeModelId : "No model selected");
    }

    private void showProviderMenu(View v) {
        PopupMenu popup = new PopupMenu(requireContext(), v);
        List<AiProvider> list = ProviderRegistry.list();
        for (int i = 0; i < list.size(); i++) {
            popup.getMenu().add(0, i, 0, list.get(i).name());
        }
        popup.setOnMenuItemClickListener(item -> {
            activeProvider = list.get(item.getItemId());
            if ("local-llama".equals(activeProvider.id())) {
                activeModelId = ModelManager.get(requireContext()).getActiveId();
            } else {
                // Hardcoded default models for P3 tests
                if ("openai".equals(activeProvider.id())) activeModelId = "gpt-4o";
                else if ("anthropic".equals(activeProvider.id())) activeModelId = "claude-3-5-sonnet-20240620";
                else if ("gemini".equals(activeProvider.id())) activeModelId = "gemini-1.5-flash";
            }
            AiStorage.get(requireContext()).kvPut("conv_provider_" + conversationId, activeProvider.id());
            AiStorage.get(requireContext()).kvPut("conv_model_" + conversationId, activeModelId);
            renderActiveSelection();
            return true;
        });
        popup.show();
    }

    private void loadHistory() {
        AiStorage storage = AiStorage.get(requireContext());
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
            Toast.makeText(requireContext(), "Select provider and model", Toast.LENGTH_SHORT).show();
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

        agentManager.runTurn(scId, conversationId, text, activeProvider, activeModelId, new pro.sketchware.ai.agent.AgentManager.AgentListener() {
            @Override
            public void onStep(pro.sketchware.ai.agent.AgentStep step) {
                if (step.kind == pro.sketchware.ai.agent.AgentStep.Kind.TEXT) {
                    assistantMsg.content += step.payload;
                    adapter.notifyItemChanged(adapter.getItemCount() - 1);
                } else if (step.kind == pro.sketchware.ai.agent.AgentStep.Kind.ERROR) {
                    assistantMsg.content = "Error: " + step.payload;
                    adapter.notifyItemChanged(adapter.getItemCount() - 1);
                }
                // Persistent steps to AiStorage would happen here in full P5
            }

            @Override
            public void onDone() {
                persistMessage(assistantMsg);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> applyChatState("idle"));
                }
            }
        });
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
        AiStorage storage = AiStorage.get(requireContext());
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
            
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) holder.card.getLayoutParams();
            if ("user".equals(m.role)) {
                holder.root.setGravity(Gravity.END);
                holder.card.setStrokeColor(ThemeUtils.getColor(requireContext(), R.attr.colorPrimary));
                lp.gravity = Gravity.END;
            } else {
                holder.root.setGravity(Gravity.START);
                holder.card.setStrokeColor(ThemeUtils.getColor(requireContext(), R.attr.colorAccent));
                lp.gravity = Gravity.START;
            }
            holder.card.setLayoutParams(lp);
        }

        @Override
        public int getItemCount() { return messages.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text;
            MaterialCardView card;
            LinearLayout root;
            ViewHolder(View v) {
                super(v);
                text = v.findViewById(R.id.tv_message);
                card = v.findViewById(R.id.card_bubble);
                root = (LinearLayout) v.findViewById(R.id.chat_bubble_root);
            }
        }
    }
}
