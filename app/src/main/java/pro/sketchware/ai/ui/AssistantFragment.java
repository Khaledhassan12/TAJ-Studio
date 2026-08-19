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
import pro.sketchware.ai.core.ProviderProfile;
import pro.sketchware.ai.core.StreamCallbacks;
import pro.sketchware.ai.core.ThinkingDetector;
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
    private VoiceReader voiceReader;

    private String sc_id;
    private boolean isStreaming = false;
    private int pendingEditIndex = -1;
    private String currentSpeakingText = null;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isNearBottom() {
        View panel = panels.get(R.id.ai_dest_session);
        if (panel == null) return true;
        RecyclerView rv = panel.findViewById(R.id.rv_chat);
        LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
        if (lm == null || chatAdapter.getItemCount() == 0) return true;
        return lm.findLastVisibleItemPosition() >= chatAdapter.getItemCount() - 2;
    }

    private void scrollToBottom(boolean smooth) {
        View panel = panels.get(R.id.ai_dest_session);
        if (panel == null) return;
        RecyclerView rv = panel.findViewById(R.id.rv_chat);
        rv.post(() -> {
            int n = chatAdapter.getItemCount();
            if (n == 0) return;
            if (smooth) rv.smoothScrollToPosition(n - 1);
            else rv.scrollToPosition(n - 1);
        });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DesignActivity activity = (DesignActivity) requireActivity();
        sc_id = DesignActivity.sc_id;
        configStore = AIConfigStore.getInstance(activity);
        chatStore = new ChatStore(activity, sc_id);
        toolRegistry = new ToolRegistry();
        voiceReader = new VoiceReader(activity, new VoiceReader.OnStateListener() {
            @Override public void onStart() {}
            @Override public void onDone() { currentSpeakingText = null; updateVoiceIcons(); }
            @Override
            public void onError(String message) {
                currentSpeakingText = null;
                updateVoiceIcons();
                Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    private void updateVoiceIcons() {
        mainHandler.post(() -> {
            if (chatAdapter != null) chatAdapter.setCurrentSpeakingText(currentSpeakingText);
        });
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

    @Override
    public void onResume() {
        super.onResume();
        View settings = panels.get(R.id.ai_dest_settings);
        if (settings != null) {
            refreshReadback(settings);
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

        com.google.android.material.materialswitch.MaterialSwitch switchThinking = panel.findViewById(R.id.switch_thinking);
        if (switchThinking != null) {
            switchThinking.setChecked(configStore.isShowThinking());
            switchThinking.setOnCheckedChangeListener((v, checked) -> configStore.setShowThinking(checked));
        }

        refreshReadback(panel);
    }

    private void refreshReadback(View panel) {
        TextView tv = panel.findViewById(R.id.tv_readback);
        if (tv != null) {
            String report = "provider=" + configStore.getProviderId() +
                    "\nmodel=" + configStore.safeModel() +
                    "\nbaseUrl=" + configStore.getBaseUrl() +
                    "\nkey=" + pro.sketchware.ai.config.AIConfigStore.maskKey(configStore.safeKey());
            tv.setText(report);
        }
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
                scrollToBottom(false);
            }
        });

        panel.findViewById(R.id.fab_new_session).setOnClickListener(v -> {
            currentSession = new ChatStore.Session();
            if (chatAdapter != null) {
                chatAdapter.setMessages(new ArrayList<>());
                scrollToBottom(false);
            }
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

        chatAdapter.setOnMessageActionListener(new ChatAdapter.OnMessageActionListener() {
            @Override
            public void onCopy(ChatStore.Message message) {
                android.content.ClipboardManager cb = (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                cb.setPrimaryClip(android.content.ClipData.newPlainText("TAG Assistant", message.text));
                Snackbar.make(panel, R.string.ai_msg_copied, Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onEdit(ChatStore.Message message, int position) {
                EditText input = panel.findViewById(R.id.et_input);
                input.setText(message.text);
                input.requestFocus();
                pendingEditIndex = position;
                panel.findViewById(R.id.chip_edit_banner).setVisibility(View.VISIBLE);
            }

            @Override
            public void onBranch(ChatStore.Message message, int position) {
                ChatStore.Session branch = ChatStore.branchSession(currentSession, position);
                chatStore.saveSession(branch);
                currentSession = branch;
                chatAdapter.setMessages(currentSession.messages);
                scrollToBottom(false);
                Snackbar.make(panel, R.string.ai_msg_branched, Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onShare(ChatStore.Message message) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, message.text);
                startActivity(Intent.createChooser(intent, null));
            }

            @Override
            public void onRegenerate(ChatStore.Message message, int position) {
                if (isStreaming) return;
                // Remove all messages after the last USER message before this one
                int lastUserIdx = -1;
                for (int i = position; i >= 0; i--) {
                    if ("user".equals(currentSession.messages.get(i).role)) {
                        lastUserIdx = i;
                        break;
                    }
                }
                if (lastUserIdx != -1) {
                    String lastUserText = currentSession.messages.get(lastUserIdx).text;
                    while (currentSession.messages.size() > lastUserIdx + 1) {
                        currentSession.messages.remove(lastUserIdx + 1);
                    }
                    chatAdapter.setMessages(currentSession.messages);
                    sendMessage(lastUserText, true);
                }
            }

            @Override
            public void onVoice(ChatStore.Message message, View btnVoice) {
                if (message.text.equals(currentSpeakingText)) {
                    voiceReader.stop();
                    currentSpeakingText = null;
                } else {
                    voiceReader.speak(message.text);
                    currentSpeakingText = message.text;
                }
                updateVoiceIcons();
            }
        });

        chatAdapter.setOnErrorActionListener(new ChatAdapter.OnErrorActionListener() {
            @Override
            public void onAction(ChatStore.Message message) {
                if ("Test key".equals(message.action)) {
                    testKeyLive();
                } else {
                    startActivity(new Intent(getActivity(), TagAssistantActivity.class));
                }
            }

            @Override
            public void onLongClick(ChatStore.Message message) {
                if (message.toolState != null && !message.toolState.isEmpty()) {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Raw Error Details")
                            .setMessage(message.toolState)
                            .setPositiveButton("OK", null)
                            .show();
                }
            }
        });

        chatAdapter.setOnSuggestionListener(new ChatAdapter.OnSuggestionListener() {
            @Override
            public void onSwitchToAgent(int position) {
                configStore.setAgentEnabled(true);
                View sessionPanel = panels.get(R.id.ai_dest_session);
                if (sessionPanel != null) updateSessionHeader(sessionPanel);
                // Resend the message that triggered this
                if (position > 0) {
                    ChatStore.Message prev = currentSession.messages.get(position - 1);
                    if ("user".equals(prev.role)) {
                        // Remove suggestion and everything after it
                        while (currentSession.messages.size() > position) {
                            currentSession.messages.remove(position);
                        }
                        chatAdapter.setMessages(currentSession.messages);
                        isStreaming = false;
                        sendMessage(prev.text, true);
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
            rv.post(() -> scrollToBottom(false));
        }

        EditText input = panel.findViewById(R.id.et_input);
        FloatingActionButton send = panel.findViewById(R.id.fab_send);
        send.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (!text.isEmpty()) {
                if (pendingEditIndex != -1) {
                    while (currentSession.messages.size() > pendingEditIndex) {
                        currentSession.messages.remove(pendingEditIndex);
                    }
                    chatAdapter.setMessages(currentSession.messages);
                    pendingEditIndex = -1;
                    panel.findViewById(R.id.chip_edit_banner).setVisibility(View.GONE);
                }
                sendMessage(text);
                input.setText("");
            }
        });

        com.google.android.material.chip.Chip editBanner = panel.findViewById(R.id.chip_edit_banner);
        editBanner.setOnCloseIconClickListener(v -> {
            pendingEditIndex = -1;
            editBanner.setVisibility(View.GONE);
            input.setText("");
        });

        View dot = panel.findViewById(R.id.connection_dot);
        ObjectAnimator pulse = ObjectAnimator.ofFloat(dot, "alpha", 0.4f, 1.0f);
        pulse.setDuration(1200);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.start();

        Chip chipAgent = panel.findViewById(R.id.chip_agent_state);
        if (chipAgent != null) {
            chipAgent.setOnClickListener(v -> {
                boolean next = !configStore.isAgentEnabled();
                configStore.setAgentEnabled(next);
                updateSessionHeader(panel);
                Snackbar.make(panel, next ? "Agent mode enabled — I can now act on your project" : "Agent mode off — chat only", Snackbar.LENGTH_SHORT).show();
            });
        }

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
        Chip chipAgent = panel.findViewById(R.id.chip_agent_state);

        if (chipAgent != null) {
            boolean enabled = configStore.isAgentEnabled();
            chipAgent.setText(enabled ? R.string.ai_agent_on : R.string.ai_agent_off);
            int bgColor = pro.sketchware.utility.ThemeUtils.getColor(getContext(),
                    enabled ? R.attr.colorTertiaryContainer : R.attr.colorSurfaceContainerHigh);
            chipAgent.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(bgColor));
            
            chipAgent.setAlpha(0f);
            chipAgent.animate().alpha(1f).setDuration(150).start();
        }

        String providerId = configStore.getProviderId();
        String model = configStore.safeModel();
        if (model.isEmpty()) {
            List<pro.sketchware.ai.core.ModelItem> cache = configStore.loadModelsCache(providerId);
            if (!cache.isEmpty()) {
                // Try to find a FREE model first
                for (pro.sketchware.ai.core.ModelItem item : cache) {
                    if (item.free != null && item.free) {
                        model = item.id;
                        break;
                    }
                }
                if (model.isEmpty()) model = cache.get(0).id;
                configStore.setModel(model);
            }
        }
        modelInfo.setText(model.isEmpty() ? "No model selected" : model);

        boolean isLocal = "ollama".equals(providerId) || providerId.contains("local");
        location.setText(isLocal ? R.string.ai_badge_local : R.string.ai_badge_cloud);
    }

    private void sendMessage(String text) {
        sendMessage(text, false);
    }

    private void sendMessage(String text, boolean isRegen) {
        if (isStreaming) return;

        View panel = panels.get(R.id.ai_dest_session);
        if (panel != null) panel.findViewById(R.id.empty_state).setVisibility(View.GONE);

        if (!isRegen) {
            ChatStore.Message userMsg = new ChatStore.Message("user", text);
            currentSession.messages.add(userMsg);
            chatAdapter.addMessage(userMsg);
            scrollToBottom(true);
        }
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

        boolean agentEnabled = configStore.isAgentEnabled();
        List<AIMessage> history = new ArrayList<>();
        for (ChatStore.Message m : currentSession.messages) {
            if ("user".equals(m.role)) history.add(AIMessage.user(m.text));
            else if ("assistant".equals(m.role)) history.add(AIMessage.assistant(m.text));
        }

        String providerId = configStore.getSelectedProviderId();
        String model = configStore.getModel(providerId);

        if (agentEnabled) {
            orchestrator = new AgentOrchestrator(provider, toolRegistry, new Tool.ToolContext(activity, sc_id));
            orchestrator.run("You are a helpful Android development assistant.", history, model, 0.7f, new StreamCallbacks() {
                private ChatStore.Message assistantMsg;
                private ChatStore.Message thinkingMsg;
                private long thinkingStart = 0;

                @Override
                public void onReasoningToken(String token) {
                    mainHandler.post(() -> {
                        if (thinkingMsg == null) {
                            thinkingStart = System.currentTimeMillis();
                            thinkingMsg = new ChatStore.Message("thinking", "");
                            int pos = currentSession.messages.size();
                            if (assistantMsg != null) {
                                pos = currentSession.messages.indexOf(assistantMsg);
                            }
                            currentSession.messages.add(pos, thinkingMsg);
                            chatAdapter.insertMessage(pos, thinkingMsg);
                            scrollToBottom(true);
                        }
                        int idx = currentSession.messages.indexOf(thinkingMsg);
                        chatAdapter.appendReasoning(idx, token);
                    });
                }

                @Override
                public void onToken(String token) {
                    mainHandler.post(() -> {
                        if (thinkingMsg != null && "thinking".equals(thinkingMsg.role)) {
                            long elapsed = (System.currentTimeMillis() - thinkingStart) / 1000;
                            chatAdapter.completeThinking(currentSession.messages.indexOf(thinkingMsg), elapsed);
                        }
                        if (assistantMsg == null) {
                            assistantMsg = new ChatStore.Message("assistant", token);
                            currentSession.messages.add(assistantMsg);
                            chatAdapter.addMessage(assistantMsg);
                            scrollToBottom(true);
                        } else {
                            int idx = currentSession.messages.indexOf(assistantMsg);
                            chatAdapter.appendToken(idx, token);
                            if (isNearBottom()) scrollToBottom(false);
                        }
                    });
                }

                @Override
                public void onToolCall(ToolCall call) {
                    mainHandler.post(() -> {
                        if (thinkingMsg != null && "thinking".equals(thinkingMsg.role)) {
                            long elapsed = (System.currentTimeMillis() - thinkingStart) / 1000;
                            chatAdapter.completeThinking(currentSession.messages.indexOf(thinkingMsg), elapsed);
                        }
                        ChatStore.Message toolMsg = new ChatStore.Message("tool", call.name);
                        currentSession.messages.add(toolMsg);
                        chatAdapter.addMessage(toolMsg);
                        scrollToBottom(true);
                    });
                }

                @Override
                public void onComplete(AIResponse response) {
                    mainHandler.post(() -> {
                        if (thinkingMsg != null && "thinking".equals(thinkingMsg.role)) {
                            long elapsed = (System.currentTimeMillis() - thinkingStart) / 1000;
                            chatAdapter.completeThinking(currentSession.messages.indexOf(thinkingMsg), elapsed);
                        }
                        isStreaming = false;
                        updateStreamingUi(false);
                        chatStore.saveSession(currentSession);
                    });
                }

                @Override
                public void onError(Throwable error) {
                    mainHandler.post(() -> {
                        if (thinkingMsg != null && "thinking".equals(thinkingMsg.role)) {
                            long elapsed = (System.currentTimeMillis() - thinkingStart) / 1000;
                            chatAdapter.completeThinking(currentSession.messages.indexOf(thinkingMsg), elapsed);
                        }
                        isStreaming = false;
                        updateStreamingUi(false);
                        handleChatError(error);
                    });
                }
            });
        } else {
            if (AgentSuggester.needsAgent(text)) {
                ChatStore.Message suggestion = new ChatStore.Message("suggestion", "locked");
                currentSession.messages.add(suggestion);
                chatAdapter.addMessage(suggestion);
            }

            pro.sketchware.ai.core.AIRequest request = new pro.sketchware.ai.core.AIRequest.Builder()
                    .model(model)
                    .messages(history)
                    .thinkingEnabled(configStore.isShowThinking() && ThinkingDetector.hint(model))
                    .build();

            provider.stream(request, new StreamCallbacks() {
                private ChatStore.Message assistantMsg;
                private ChatStore.Message thinkingMsg;
                private long thinkingStart = 0;

                @Override
                public void onReasoningToken(String token) {
                    mainHandler.post(() -> {
                        if (thinkingMsg == null) {
                            thinkingStart = System.currentTimeMillis();
                            thinkingMsg = new ChatStore.Message("thinking", "");
                            int pos = currentSession.messages.size();
                            if (assistantMsg != null) {
                                pos = currentSession.messages.indexOf(assistantMsg);
                            }
                            currentSession.messages.add(pos, thinkingMsg);
                            chatAdapter.insertMessage(pos, thinkingMsg);
                            scrollToBottom(true);
                        }
                        int idx = currentSession.messages.indexOf(thinkingMsg);
                        chatAdapter.appendReasoning(idx, token);
                    });
                }

                @Override
                public void onToken(String token) {
                    mainHandler.post(() -> {
                        if (thinkingMsg != null && "thinking".equals(thinkingMsg.role)) {
                            long elapsed = (System.currentTimeMillis() - thinkingStart) / 1000;
                            chatAdapter.completeThinking(currentSession.messages.indexOf(thinkingMsg), elapsed);
                        }
                        
                        if (assistantMsg == null) {
                            assistantMsg = new ChatStore.Message("assistant", token);
                            currentSession.messages.add(assistantMsg);
                            chatAdapter.addMessage(assistantMsg);
                            scrollToBottom(true);
                        } else {
                            int idx = currentSession.messages.indexOf(assistantMsg);
                            chatAdapter.appendToken(idx, token);
                            if (isNearBottom()) scrollToBottom(false);
                        }
                    });
                }

                @Override
                public void onToolCall(ToolCall call) {}

                @Override
                public void onComplete(AIResponse response) {
                    mainHandler.post(() -> {
                        if (thinkingMsg != null && "thinking".equals(thinkingMsg.role)) {
                            long elapsed = (System.currentTimeMillis() - thinkingStart) / 1000;
                            chatAdapter.completeThinking(currentSession.messages.indexOf(thinkingMsg), elapsed);
                        }
                        isStreaming = false;
                        updateStreamingUi(false);
                        chatStore.saveSession(currentSession);
                    });
                }

                @Override
                public void onError(Throwable error) {
                    mainHandler.post(() -> {
                        if (thinkingMsg != null && "thinking".equals(thinkingMsg.role)) {
                            long elapsed = (System.currentTimeMillis() - thinkingStart) / 1000;
                            chatAdapter.completeThinking(currentSession.messages.indexOf(thinkingMsg), elapsed);
                        }
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

    private void handleChatError(Throwable error) {
        String providerId = configStore.getProviderId();
        if (configStore.safeKey().isEmpty() && configStore.requiresKeyFor(providerId)) {
            addErrorMessage("No API key stored. Open TAG Assistant Manager and Save your key.", "", "Open settings");
            return;
        }

        String raw = "";
        boolean isAuth = false;
        if (error instanceof pro.sketchware.ai.net.AIException) {
            pro.sketchware.ai.net.AIException ae = (pro.sketchware.ai.net.AIException) error;
            raw = ae.rawBody;
            if (raw.length() > 200) raw = raw.substring(0, 200);
            if (ae.type == pro.sketchware.ai.net.AIException.Type.AUTH) isAuth = true;
        }

        if (isAuth) {
            addErrorMessage("Authentication failed. Double-check your API key.", raw, "Test key");
        } else {
            addErrorMessage("Error: " + error.getMessage(), raw, "Open settings");
        }
    }

    private void addErrorMessage(String text, String raw, String action) {
        ChatStore.Message msg = new ChatStore.Message("error", text);
        msg.toolState = raw; // Borrowing toolState for raw error details
        msg.action = action;
        currentSession.messages.add(msg);
        chatAdapter.addMessage(msg);
        chatStore.saveSession(currentSession);
    }

    private void testKeyLive() {
        String providerId = configStore.getProviderId();
        ProviderProfile profile = AIProviderRegistry.getInstance(getContext()).get(providerId);
        String key = configStore.safeKey();
        if (profile == null) return;

        new Thread(() -> {
            String verdict = pro.sketchware.ai.net.ModelSyncService.validateKey(profile, key);
            mainHandler.post(() -> {
                if (getContext() == null) return;
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.ai_test_connection)
                        .setMessage(verdict)
                        .setPositiveButton(R.string.common_word_ok, null)
                        .show();
            });
        }).start();
    }

    private void updateStreamingUi(boolean streaming) {
        View panel = panels.get(R.id.ai_dest_session);
        if (panel != null) {
            RecyclerView rv = panel.findViewById(R.id.rv_chat);
            if (streaming) {
                rv.setItemAnimator(null);
            } else {
                rv.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());
            }

            LinearProgressIndicator progress = panel.findViewById(R.id.streaming_progress);
            progress.setVisibility(streaming ? View.VISIBLE : View.GONE);
            FloatingActionButton fab = panel.findViewById(R.id.fab_send);
            fab.setImageResource(streaming ? R.drawable.ic_mtrl_stop : R.drawable.ic_mtrl_send);
        }
    }

    @Override
    public void onDestroy() {
        if (orchestrator != null) orchestrator.shutdown();
        if (voiceReader != null) voiceReader.shutdown();
        super.onDestroy();
    }
}
