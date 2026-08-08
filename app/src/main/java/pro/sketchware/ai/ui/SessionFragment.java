package pro.sketchware.ai.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;
import pro.sketchware.R;
import pro.sketchware.ai.models.ModelManager;
import pro.sketchware.ai.runtime.RuntimeClient;

public class SessionFragment extends Fragment {

    private RecyclerView recycler;
    private ChatAdapter adapter;
    private EditText edMessage;
    private View btnSend, btnCancel;
    private TextView tvActiveModel;
    private RuntimeClient runtimeClient;

    private static class ChatMessage {
        String role;
        String content;
        ChatMessage(String role, String content) { this.role = role; this.content = content; }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_session, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        runtimeClient = new RuntimeClient(requireContext());
        runtimeClient.bind();

        recycler = view.findViewById(R.id.recycler_messages);
        edMessage = view.findViewById(R.id.ed_message);
        btnSend = view.findViewById(R.id.btn_send);
        btnCancel = view.findViewById(R.id.btn_cancel);
        tvActiveModel = view.findViewById(R.id.tv_active_model);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ChatAdapter();
        recycler.setAdapter(adapter);

        String activeId = ModelManager.get(requireContext()).getActiveId();
        tvActiveModel.setText(activeId != null ? activeId : "No model selected");

        btnSend.setOnClickListener(v -> sendMessage());
        btnCancel.setOnClickListener(v -> runtimeClient.cancel());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        runtimeClient.unbind();
    }

    private void sendMessage() {
        String text = edMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        adapter.addMessage(new ChatMessage("user", text));
        edMessage.setText("");
        
        ChatMessage assistantMsg = new ChatMessage("assistant", "");
        adapter.addMessage(assistantMsg);
        
        btnSend.setVisibility(View.GONE);
        btnCancel.setVisibility(View.VISIBLE);

        runtimeClient.complete(text, new RuntimeClient.Callback() {
            @Override
            public void onToken(String token) {
                assistantMsg.content += token;
                adapter.notifyItemChanged(adapter.getItemCount() - 1);
                recycler.scrollToPosition(adapter.getItemCount() - 1);
            }

            @Override
            public void onDone() {
                requireActivity().runOnUiThread(() -> {
                    btnSend.setVisibility(View.VISIBLE);
                    btnCancel.setVisibility(View.GONE);
                });
            }

            @Override
            public void onError(String error) {
                requireActivity().runOnUiThread(() -> {
                    assistantMsg.content = "Error: " + error;
                    adapter.notifyItemChanged(adapter.getItemCount() - 1);
                    btnSend.setVisibility(View.VISIBLE);
                    btnCancel.setVisibility(View.GONE);
                });
            }
        });
    }

    private class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
        private final List<ChatMessage> messages = new ArrayList<>();

        public void addMessage(ChatMessage m) {
            messages.add(m);
            notifyItemInserted(messages.size() - 1);
        }

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
                holder.card.setStrokeColor(requireContext().getColor(pro.sketchware.R.color.colorAccent));
                lp.gravity = Gravity.END;
            } else {
                holder.root.setGravity(Gravity.START);
                holder.card.setStrokeColor(requireContext().getColor(pro.sketchware.R.color.colorPrimary));
                lp.gravity = Gravity.START;
            }
            holder.card.setLayoutParams(lp);
        }

        @Override
        public int getItemCount() { return messages.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text;
            MaterialCardView card;
            View root;
            ViewHolder(View v) {
                super(v);
                text = v.findViewById(R.id.tv_message);
                card = v.findViewById(R.id.card_bubble);
                root = v.findViewById(R.id.chat_bubble_root);
            }
        }
    }
}
