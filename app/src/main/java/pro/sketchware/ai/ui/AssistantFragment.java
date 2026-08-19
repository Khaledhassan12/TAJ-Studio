package pro.sketchware.ai.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.design.DesignActivity;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigationrail.NavigationRailView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.agent.AgentOrchestrator;
import pro.sketchware.ai.agent.AgentSuggester;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.ToolRegistry;
import pro.sketchware.ai.config.AIConfigStore;
import pro.sketchware.ai.core.AIMessage;
import pro.sketchware.ai.core.AIProvider;
import pro.sketchware.ai.core.AIProviderRegistry;
import pro.sketchware.ai.core.AIResponse;
import pro.sketchware.ai.core.ChatStore;
import pro.sketchware.ai.core.StreamCallbacks;
import pro.sketchware.ai.core.ToolCall;

public class AssistantFragment extends Fragment {

    private NavigationRailView rail;
    private FrameLayout container;
    private final SparseArray<View> panels = new SparseArray<>();

    private AIConfigStore configStore;
    private ChatStore chatStore;
    private ChatStore.Session currentSession;
    private ChatAdapter chatAdapter;
    private AgentOrchestrator orchestrator;
    private ToolRegistry toolRegistry;

    private String sc_id;
    private boolean isStreaming = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DesignActivity activity = (DesignActivity) requireActivity();
        sc_id = DesignActivity.sc_id;
        configStore = AIConfigStore.getInstance(activity);
        chatStore = new ChatStore(activity, sc_id);
        toolRegistry = new ToolRegistry();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_assistant, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        rail = view.findViewById(R.id.navigation_rail);
        container = view.findViewById(R.id.panel_container);

        rail.setOnItemSelectedListener(item -> {
            showPanel(item.getItemId());
            return true;
        });

        if (savedInstanceState == null) {
            rail.setSelectedItemId(R.id.ai_dest_session);
        }
    }

    private void showPanel(int id) {
        View panel = panels.get(id);
        if (panel == null) {
            panel = inflatePanel(id);
            if (panel != null) {
                panels.put(id, panel);
                setupPanel(id, panel);
            }
        }

        if (panel != null) {
            container.removeAllViews();
            container.addView(panel);
            panel.setAlpha(0);
            panel.animate().alpha(1).setDuration(150).start();
        }
    }

    private View inflatePanel(int id) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        if (id == R.id.ai_dest_session) return inflater.inflate(R.layout.panel_session, container, false);
        if (id == R.id.ai_dest_models) return inflater.inflate(R.layout.panel_models, container, false);
        if (id == R.id.ai_dest_agents) return inflater.inflate(R.layout.panel_agents, container, false);
        if (id == R.id.ai_dest_favorites) return inflater.inflate(R.layout.panel_favorites, container, false);
        if (id == R.id.ai_dest_files) return inflater.inflate(R.layout.panel_files, container, false);
        if (id == R.id.ai_dest_settings) return inflater.inflate(R.layout.panel_settings, container, false);
        if (id == R.id.ai_dest_history) return inflater.inflate(R.layout.panel_history, container, false);
        return null;
    }

    private void setupPanel(int id, View panel) {
        if (id == R.id.ai_dest_session) {
            setupSessionPanel(panel);
        } else if (id == R.id.ai_dest_models) {
            setupModelsPanel(panel);
        } else if (id == R.id.ai_dest_agents) {
            setupAgentsPanel(panel);
        } else if (id == R.id.ai_dest_history) {
            setupHistoryPanel(panel);
        } else if (id == R.id.ai_dest_files) {
            setupFilesPanel(panel);
        } else if (id == R.id.ai_dest_settings) {
            setupSettingsPanel(panel);
        }
    }

    private void setupFilesPanel(View panel) {
        panel.findViewById(R.id.btn_refresh_context).setOnClickListener(v -> {
            Snackbar.make(panel, "Context refreshed from project", Snackbar.LENGTH_SHORT).show();
        });
        // Snapshot logic would go here
    }

    private void setupSettingsPanel(View panel) {
        panel.findViewById(R.id.btn_full_settings).setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), TagAssistantActivity.class));
        });
        panel.findViewById(R.id.btn_diagnostics).setOnClickListener(v -> {
            String report = pro.sketchware.ai.net.ConformanceDiagnostics.runReport();
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Conformance Report")
                    .setMessage(report)
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    private void setupModelsPanel(View panel) {
        RecyclerView rv = panel.findViewById(R.id.rv_models);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        ModelAdapter adapter = new ModelAdapter();
        rv.setAdapter(adapter);

        String providerId = configStore.getSelectedProviderId();
        List<pro.sketchware.ai.core.ModelItem> cached = configStore.loadModelsCache(providerId);
        adapter.setModels(cached);

        adapter.setOnModelClickListener(model -> {
            configStore.setModel(providerId, model.id);
            View sessionPanel = panels.get(R.id.ai_dest_session);
            if (sessionPanel != null) updateSessionHeader(sessionPanel);
            Snackbar.make(panel, "Model switched to " + model.id, Snackbar.LENGTH_SHORT).show();
        });

        panel.findViewById(R.id.btn_sync).setOnClickListener(v -> {
            Snackbar.make(panel, "Open Settings to sync models", Snackbar.LENGTH_LONG)
                    .setAction("Open", v2 -> {
                        startActivity(new Intent(getActivity(), TagAssistantActivity.class));
                    }).show();
        });

        TextView tvProvider = panel.findViewById(R.id.tv_provider_name);
        TextView tvModel = panel.findViewById(R.id.tv_current_model);
        tvProvider.setText(AIProviderRegistry.getInstance(getContext()).get(providerId).displayName);
        tvModel.setText(configStore.getModel(providerId));
    }

    private void setupAgentsPanel(View panel) {
        RecyclerView rv = panel.findViewById(R.id.rv_tools);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        ToolAdapter adapter = new ToolAdapter();
        rv.setAdapter(adapter);
        adapter.setTools(toolRegistry.all());
    }

    private void setupHistoryPanel(View panel) {
        RecyclerView rv = panel.findViewById(R.id.rv_history);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        SessionAdapter adapter = new SessionAdapter();
        rv.setAdapter(adapter);

        adapter.setSessions(chatStore.loadAllSessions());
        adapter.setOnSessionClickListener(session -> {
            currentSession = session;
            rail.setSelectedItemId(R.id.ai_dest_session);
            if (chatAdapter != null) {
                chatAdapter.setMessages(session.messages);
            }
        });

        panel.findViewById(R.id.fab_new_session).setOnClickListener(v -> {
            currentSession = new ChatStore.Session();
            if (chatAdapter != null) chatAdapter.setMessages(new ArrayList<>());
            rail.setSelectedItemId(R.id.ai_dest_session);
        });
    }

    private void setupSessionPanel(View panel) {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(panel, (v, insets) -> {
            int ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), ime);
            return insets;
        });

        RecyclerView rv = panel.findViewById(R.id.rv_chat);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        chatAdapter = new ChatAdapter();
        rv.setAdapter(chatAdapter);
        chatAdapter.setOnSuggestionListener(new ChatAdapter.OnSuggestionListener() {
            @Override
            public void onSwitchToAgent(int position) {
                configStore.setDefaultMode(AIConfigStore.MODE_AGENT);
                View modeToggle = panel.findViewById(R.id.mode_toggle_group);
                if (modeToggle instanceof com.google.android.material.button.MaterialButtonToggleGroup) {
                    ((com.google.android.material.button.MaterialButtonToggleGroup) modeToggle).check(R.id.btn_mode_agent);
                }
                // Resend the message that triggered this
                if (position > 0) {
                    ChatStore.Message prev = currentSession.messages.get(position - 1);
                    if ("user".equals(prev.role)) {
                        // Remove suggestion item
                        currentSession.messages.remove(position);
                        chatAdapter.notifyItemRemoved(position);
                        sendMessage(prev.text);
                    }
                }
            }

            @Override
            public void onStayInChat(int position) {
                currentSession.messages.remove(position);
                chatAdapter.notifyItemRemoved(position);
            }
        });

        List<ChatStore.Session> sessions = chatStore.loadAllSessions();
        if (sessions.isEmpty()) {
            currentSession = new ChatStore.Session();
            panel.findViewById(R.id.empty_state).setVisibility(View.VISIBLE);
        } else {
            currentSession = sessions.get(0);
            chatAdapter.setMessages(currentSession.messages);
            panel.findViewById(R.id.empty_state).setVisibility(View.GONE);
        }

        EditText input = panel.findViewById(R.id.et_input);
        FloatingActionButton send = panel.findViewById(R.id.fab_send);
        send.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (!text.isEmpty()) {
                sendMessage(text);
                input.setText("");
            }
        });

        View dot = panel.findViewById(R.id.connection_dot);
        ObjectAnimator pulse = ObjectAnimator.ofFloat(dot, "alpha", 0.4f, 1.0f);
        pulse.setDuration(1200);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.start();

        MaterialButtonToggleGroup modeToggle = panel.findViewById(R.id.mode_toggle_group);
        String currentMode = configStore.getDefaultMode();
        modeToggle.check(AIConfigStore.MODE_AGENT.equals(currentMode) ? R.id.btn_mode_agent : R.id.btn_mode_chat);
        modeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                configStore.setDefaultMode(checkedId == R.id.btn_mode_agent ? AIConfigStore.MODE_AGENT : AIConfigStore.MODE_CHAT);
            }
        });

        com.google.android.material.chip.ChipGroup suggestions = panel.findViewById(R.id.empty_suggestions);
        for (int i = 0; i < suggestions.getChildCount(); i++) {
            View child = suggestions.getChildAt(i);
            if (child instanceof com.google.android.material.chip.Chip chip) {
                child.setOnClickListener(v -> sendMessage(chip.getText().toString()));
            }
        }

        updateSessionHeader(panel);
    }

    private void updateSessionHeader(View panel) {
        TextView modelInfo = panel.findViewById(R.id.tv_model_info);
        Chip location = panel.findViewById(R.id.chip_location);

        String providerId = configStore.getSelectedProviderId();
        String model = configStore.safeModel();
        if (model.isEmpty()) {
            List<pro.sketchware.ai.core.ModelItem> cache = configStore.loadModelsCache(providerId);
            if (!cache.isEmpty()) {
                model = cache.get(0).id;
                configStore.setModel(providerId, model);
            }
        }
        modelInfo.setText(model.isEmpty() ? "No model selected" : model);

        boolean isLocal = "ollama".equals(providerId) || providerId.contains("local");
        location.setText(isLocal ? R.string.ai_badge_local : R.string.ai_badge_cloud);
    }

    private void sendMessage(String text) {
        if (isStreaming) return;

        View panel = panels.get(R.id.ai_dest_session);
        if (panel != null) panel.findViewById(R.id.empty_state).setVisibility(View.GONE);

        ChatStore.Message userMsg = new ChatStore.Message("user", text);
        currentSession.messages.add(userMsg);
        chatAdapter.addMessage(userMsg);
        chatStore.saveSession(currentSession);

        DesignActivity activity = (DesignActivity) getActivity();
        if (activity == null) return;

        AIProvider provider = AIProviderRegistry.getInstance(activity).createActiveProvider(activity);
        if (provider == null) {
            addAssistantMessage("Error: Provider not found.");
            return;
        }

        isStreaming = true;
        updateStreamingUi(true);

        String mode = configStore.getDefaultMode();
        List<AIMessage> history = new ArrayList<>();
        for (ChatStore.Message m : currentSession.messages) {
            if ("user".equals(m.role)) history.add(AIMessage.user(m.text));
            else if ("assistant".equals(m.role)) history.add(AIMessage.assistant(m.text));
        }

        String providerId = configStore.getSelectedProviderId();
        String model = configStore.getModel(providerId);

        if (AIConfigStore.MODE_AGENT.equals(mode)) {
            orchestrator = new AgentOrchestrator(provider, toolRegistry, new Tool.ToolContext(activity, sc_id));
            orchestrator.run("You are a helpful Android development assistant.", history, model, 0.7f, new StreamCallbacks() {
                private ChatStore.Message assistantMsg;

                @Override
                public void onToken(String token) {
                    mainHandler.post(() -> {
                        if (assistantMsg == null) {
                            assistantMsg = new ChatStore.Message("assistant", token);
                            currentSession.messages.add(assistantMsg);
                            chatAdapter.addMessage(assistantMsg);
                        } else {
                            assistantMsg.text += token;
                            chatAdapter.notifyItemChanged(currentSession.messages.size() - 1);
                        }
                    });
                }

                @Override
                public void onToolCall(ToolCall call) {
                    mainHandler.post(() -> {
                        ChatStore.Message toolMsg = new ChatStore.Message("tool", call.name);
                        currentSession.messages.add(toolMsg);
                        chatAdapter.addMessage(toolMsg);
                    });
                }

                @Override
                public void onComplete(AIResponse response) {
                    mainHandler.post(() -> {
                        isStreaming = false;
                        updateStreamingUi(false);
                        chatStore.saveSession(currentSession);
                    });
                }

                @Override
                public void onError(Throwable error) {
                    mainHandler.post(() -> {
                        isStreaming = false;
                        updateStreamingUi(false);
                        addAssistantMessage("Error: " + error.getMessage());
                    });
                }
            });
        } else {
            if (AgentSuggester.needsAgent(text)) {
                ChatStore.Message suggestion = new ChatStore.Message("suggestion", "");
                currentSession.messages.add(suggestion);
                chatAdapter.addMessage(suggestion);
            }

            pro.sketchware.ai.core.AIRequest request = new pro.sketchware.ai.core.AIRequest.Builder()
                    .model(model)
                    .messages(history)
                    .build();

            provider.stream(request, new StreamCallbacks() {
                private ChatStore.Message assistantMsg;

                @Override
                public void onToken(String token) {
                    mainHandler.post(() -> {
                        if (assistantMsg == null) {
                            assistantMsg = new ChatStore.Message("assistant", token);
                            currentSession.messages.add(assistantMsg);
                            chatAdapter.addMessage(assistantMsg);
                        } else {
                            assistantMsg.text += token;
                            chatAdapter.notifyItemChanged(currentSession.messages.size() - 1);
                        }
                    });
                }

                @Override
                public void onToolCall(ToolCall call) {}

                @Override
                public void onComplete(AIResponse response) {
                    mainHandler.post(() -> {
                        isStreaming = false;
                        updateStreamingUi(false);
                        chatStore.saveSession(currentSession);
                    });
                }

                @Override
                public void onError(Throwable error) {
                    mainHandler.post(() -> {
                        isStreaming = false;
                        updateStreamingUi(false);
                        addAssistantMessage("Error: " + error.getMessage());
                    });
                }
            });
        }
    }

    private void addAssistantMessage(String text) {
        ChatStore.Message msg = new ChatStore.Message("assistant", text);
        currentSession.messages.add(msg);
        chatAdapter.addMessage(msg);
        chatStore.saveSession(currentSession);
    }

    private void updateStreamingUi(boolean streaming) {
        View panel = panels.get(R.id.ai_dest_session);
        if (panel != null) {
            LinearProgressIndicator progress = panel.findViewById(R.id.streaming_progress);
            progress.setVisibility(streaming ? View.VISIBLE : View.GONE);
            FloatingActionButton fab = panel.findViewById(R.id.fab_send);
            fab.setImageResource(streaming ? R.drawable.ic_mtrl_stop : R.drawable.ic_mtrl_send);
        }
    }

    @Override
    public void onDestroy() {
        if (orchestrator != null) orchestrator.shutdown();
        super.onDestroy();
    }
}
