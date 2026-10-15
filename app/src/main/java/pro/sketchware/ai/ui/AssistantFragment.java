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
import pro.sketchware.ai.agent.SessionState;
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
import pro.sketchware.ai.live.UiPoster;

public class AssistantFragment extends Fragment {

    public enum PanelState { LOADING, EMPTY, ERROR, READY, SYNCING }

    private NavigationRailView rail;
    private FrameLayout container;
    private final SparseArray<View> panels = new SparseArray<>();
    private ObjectAnimator connectionPulseAnimator;
    private static final String STATE_SELECTED_PANEL = "ai_selected_panel";
    private int selectedPanelId = R.id.ai_dest_session;
    private static final String STATE_SESSION_ID = "ai_session_id";
    private String restoredSessionId;
    private int baseSessionPaddingLeft;
    private int baseSessionPaddingTop;
    private int baseSessionPaddingRight;
    private int baseSessionPaddingBottom;

    private AIConfigStore configStore;
    private ChatStore chatStore;
    private ChatStore.Session currentSession;
    private ChatAdapter chatAdapter;
    private AgentOrchestrator orchestrator;
    private ToolRegistry toolRegistry;
    private VoiceReader voiceReader;
    private AIProvider.Handle activeHandle;

    private String sc_id;
    private int pendingEditIndex = -1;
    private String currentSpeakingText = null;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private long lastEventTime = 0;
    private final Runnable watchdog = this::handleWatchdogTimeout;

    private void handleWatchdogTimeout() {
        if (AIActivityMonitor.getState() != SessionState.IDLE) {
            long now = System.currentTimeMillis();
            if (now - lastEventTime > 120000) { // 120s
                forceIdle();
                addErrorMessage(getString(R.string.ai_timed_out), "Watchdog triggered: No event for 120s", "Retry");
            } else {
                mainHandler.postDelayed(watchdog, 10000);
            }
        }
    }

    private void pokeWatchdog() {
        lastEventTime = System.currentTimeMillis();
        AIActivityMonitor.poke();
    }

    private final AIActivityMonitor.OnStateChangeListener stateListener = newState -> {
        UiPoster.post(() -> updateStreamingUi(newState != SessionState.IDLE));
    };

    private void forceIdle() {
        AIActivityMonitor.setState(SessionState.IDLE);
        if (orchestrator != null) orchestrator.cancel();
        if (activeHandle != null) activeHandle.cancel();
    }

    private boolean isNearBottom() {
        View panel = panels.get(R.id.ai_dest_session);
        if (panel == null) return true;
        RecyclerView rv = panel.findViewById(R.id.ai_rv_chat);
        LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
        if (lm == null || chatAdapter == null || chatAdapter.getItemCount() == 0) return true;
        return lm.findLastVisibleItemPosition() >= chatAdapter.getItemCount() - 2;
    }

    private void scrollToBottom(boolean smooth) {
        View panel = panels.get(R.id.ai_dest_session);
        if (panel == null) return;
        RecyclerView rv = panel.findViewById(R.id.ai_rv_chat);
        if (rv == null || chatAdapter == null) return;
        rv.post(() -> {
            if (chatAdapter == null || rv.getAdapter() != chatAdapter) return;
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
        
        AIActivityMonitor.setOnTimeoutAction(() -> {
            UiPoster.post(() -> {
            if (!isAdded() || getView() == null) return;
                addErrorMessage(getString(R.string.ai_timed_out), "", "Retry");
                // Mark running tools as error
                View panel = panels.get(R.id.ai_dest_session);
                if (panel != null && currentSession != null) {
                    for (ChatStore.Message m : currentSession.messages) {
                        if ("tools_card".equals(m.role) && m.toolEvents != null) {
                            for (ChatStore.ToolEvent te : m.toolEvents) {
                                if ("RUNNING".equals(te.status)) {
                                    te.status = "ERROR";
                                    te.finishedAt = System.currentTimeMillis();
                                }
                            }
                        }
                    }
                    if (chatAdapter != null) chatAdapter.notifyDataSetChanged();
                    chatStore.saveSession(currentSession);
                }
            });
        });
        
        voiceReader = new VoiceReader(activity, new VoiceReader.OnStateListener() {
            @Override public void onStart() {}
            @Override public void onDone() { currentSpeakingText = null; updateVoiceIcons(); }
            @Override
            public void onError(String message) {
                currentSpeakingText = null;
                updateVoiceIcons();
                View currentView = getView();
                if (isAdded() && currentView != null) {
                    Snackbar.make(currentView, message, Snackbar.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void updateVoiceIcons() {
        UiPoster.post(() -> {
            if (!isAdded() || getView() == null) return;
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
            selectedPanelId = item.getItemId();
            showPanel(selectedPanelId);
            return true;
        });

        int restoredPanel = savedInstanceState == null
                ? R.id.ai_dest_session
                : savedInstanceState.getInt(STATE_SELECTED_PANEL, R.id.ai_dest_session);
        selectedPanelId = restoredPanel;
        restoredSessionId = savedInstanceState == null ? null : savedInstanceState.getString(STATE_SESSION_ID);
        rail.setSelectedItemId(restoredPanel);
    }

    @Override
    public void onResume() {
        super.onResume();
        AIActivityMonitor.addListener(stateListener);
        View settings = panels.get(R.id.ai_dest_settings);
        if (settings != null) {
            refreshReadback(settings);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_SELECTED_PANEL, selectedPanelId);
        if (currentSession != null) outState.putString(STATE_SESSION_ID, currentSession.id);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPause() {
        AIActivityMonitor.removeListener(stateListener);
        if (currentSession != null) chatStore.saveSession(currentSession);
        super.onPause();
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
        render(panel, PanelState.READY);
        if (id == R.id.ai_dest_session) {
            setupSessionPanel(panel);
        } else if (id == R.id.ai_dest_models) {
            setupModelsPanel(panel);
        } else if (id == R.id.ai_dest_agents) {
            setupAgentsPanel(panel);
        } else if (id == R.id.ai_dest_favorites) {
            setupFavoritesPanel(panel);
        } else if (id == R.id.ai_dest_history) {
            setupHistoryPanel(panel);
        } else if (id == R.id.ai_dest_files) {
            setupFilesPanel(panel);
        } else if (id == R.id.ai_dest_settings) {
            setupSettingsPanel(panel);
        }
    }

    private void render(View panel, PanelState state) {
        if (panel == null) return;
        View loading = panel.findViewById(R.id.ai_loading_state);
        View empty = panel.findViewById(R.id.ai_empty_state);
        View error = panel.findViewById(R.id.ai_error_state);
        View content = panel.findViewById(R.id.ai_content);
        View syncing = panel.findViewById(R.id.ai_syncing_indicator);

        if (loading != null) loading.setVisibility(state == PanelState.LOADING ? View.VISIBLE : View.GONE);
        if (empty != null) empty.setVisibility(state == PanelState.EMPTY ? View.VISIBLE : View.GONE);
        if (error != null) error.setVisibility(state == PanelState.ERROR ? View.VISIBLE : View.GONE);
        if (content != null) content.setVisibility(state == PanelState.READY || state == PanelState.SYNCING ? View.VISIBLE : View.GONE);
        if (syncing != null) syncing.setVisibility(state == PanelState.SYNCING ? View.VISIBLE : View.GONE);
    }

    private void setupFilesPanel(View panel) {
        RecyclerView rv = panel.findViewById(R.id.rv_context_files);
        if (rv == null) return;
        rv.setLayoutManager(new LinearLayoutManager(panel.getContext()));
        ContextFilesAdapter adapter = new ContextFilesAdapter();
        rv.setAdapter(adapter);

        View refresh = panel.findViewById(R.id.btn_refresh_context);
        if (refresh != null) refresh.setOnClickListener(v -> refreshContextFiles(panel, adapter, refresh));
        refreshContextFiles(panel, adapter, refresh);
    }

    private void refreshContextFiles(View panel, ContextFilesAdapter adapter, View refreshButton) {
        if (refreshButton != null) refreshButton.setEnabled(false);
        final String projectId = sc_id;
        new Thread(() -> {
            List<String> files = new ArrayList<>();
            try {
                addProjectFiles(new java.io.File(pro.sketchware.ai.agent.ProjectPaths.resourceRoot(projectId)), "res/", files, 250);
                addProjectFiles(new java.io.File(pro.sketchware.ai.agent.ProjectPaths.assetsRoot(projectId)), "assets/", files, 250);
                addProjectFiles(new java.io.File(pro.sketchware.ai.agent.ProjectPaths.projectJavaRoot(projectId)), "java/", files, 250);
                java.util.Collections.sort(files, String.CASE_INSENSITIVE_ORDER);
            } catch (Exception e) {
                android.util.Log.w("AssistantFragment", "Could not refresh project context files", e);
            }
            final List<String> result = files;
            UiPoster.post(() -> {
                if (!isAdded() || getView() == null || panel.getParent() == null) return;
                adapter.setFiles(result);
                if (refreshButton != null) refreshButton.setEnabled(true);
                if (result.isEmpty()) {
                    Snackbar.make(panel, "No project files found (or project storage is unavailable)", Snackbar.LENGTH_SHORT).show();
                } else {
                    Snackbar.make(panel, "Context files refreshed: " + result.size(), Snackbar.LENGTH_SHORT).show();
                }
            });
        }, "ai-context-files").start();
    }

    private void addProjectFiles(java.io.File root, String prefix, List<String> output, int limit) {
        if (root == null || !root.isDirectory() || output.size() >= limit) return;
        final String canonicalRoot;
        try {
            canonicalRoot = root.getCanonicalPath();
        } catch (java.io.IOException e) {
            return;
        }
        java.util.ArrayDeque<java.io.File> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && output.size() < limit && visited < 3000) {
            java.io.File current = queue.removeFirst();
            visited++;
            java.io.File[] children = current.listFiles();
            if (children == null) continue;
            for (java.io.File child : children) {
                if (output.size() >= limit) break;
                try {
                    String canonical = child.getCanonicalPath();
                    if (!canonical.startsWith(canonicalRoot + java.io.File.separator)) continue;
                    if (child.isDirectory()) {
                        if (!child.getName().startsWith(".") && current.toPath().getNameCount() - root.toPath().getNameCount() < 8) {
                            queue.addLast(child);
                        }
                    } else if (child.isFile()) {
                        String relative = root.toPath().relativize(child.toPath()).toString().replace(java.io.File.separatorChar, '/');
                        output.add(prefix + relative);
                    }
                } catch (Exception ignored) {
                    // Skip inaccessible entries and paths that cannot be canonicalized.
                }
            }
        }
    }

    private static final class ContextFilesAdapter extends RecyclerView.Adapter<ContextFilesAdapter.FileHolder> {
        private final List<String> files = new ArrayList<>();

        void setFiles(List<String> values) {
            files.clear();
            if (values == null || values.isEmpty()) files.add("No project files found");
            else files.addAll(values);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public FileHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView text = new TextView(parent.getContext());
            int padding = (int) (12 * parent.getResources().getDisplayMetrics().density);
            text.setPadding(padding, padding, padding, padding);
            text.setTextSize(14);
            text.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new FileHolder(text);
        }

        @Override
        public void onBindViewHolder(@NonNull FileHolder holder, int position) {
            holder.text.setText(files.get(position));
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        static final class FileHolder extends RecyclerView.ViewHolder {
            final TextView text;
            FileHolder(TextView itemView) {
                super(itemView);
                text = itemView;
            }
        }
    }

    private void setupSettingsPanel(View panel) {
        View btnFullSettings = panel.findViewById(R.id.btn_full_settings);
        if (btnFullSettings != null) {
            btnFullSettings.setOnClickListener(v -> {
                startActivity(new Intent(getActivity(), TagAssistantActivity.class));
            });
        }
        View btnDiagnostics = panel.findViewById(R.id.btn_diagnostics);
        if (btnDiagnostics != null) {
            btnDiagnostics.setOnClickListener(v -> {
                String report = pro.sketchware.ai.net.ConformanceDiagnostics.runReport();
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Conformance Report")
                        .setMessage(report)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }

        com.google.android.material.materialswitch.MaterialSwitch switchStreaming = panel.findViewById(R.id.switch_streaming);
        if (switchStreaming != null) {
            switchStreaming.setChecked(configStore.isStreamingEnabled());
            switchStreaming.setOnCheckedChangeListener((v, checked) -> configStore.setStreamingEnabled(checked));
        }

        com.google.android.material.materialswitch.MaterialSwitch switchAutoSuggest = panel.findViewById(R.id.switch_auto_suggest);
        if (switchAutoSuggest != null) {
            switchAutoSuggest.setChecked(configStore.isAutoSuggestEnabled());
            switchAutoSuggest.setOnCheckedChangeListener((v, checked) -> configStore.setAutoSuggestEnabled(checked));
        }

        com.google.android.material.materialswitch.MaterialSwitch switchHaptics = panel.findViewById(R.id.switch_haptics);
        if (switchHaptics != null) {
            switchHaptics.setChecked(configStore.isHapticsEnabled());
            switchHaptics.setOnCheckedChangeListener((v, checked) -> configStore.setHapticsEnabled(checked));
        }

        com.google.android.material.slider.Slider temperatureSlider = panel.findViewById(R.id.slider_temp);
        if (temperatureSlider != null) {
            temperatureSlider.setValue(configStore.getTemperature());
            temperatureSlider.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser) configStore.setTemperature(value);
            });
        }

        // ChatAdapter currently displays plain text; disable the misleading control
        // until markdown rendering is implemented rather than pretending it works.
        com.google.android.material.materialswitch.MaterialSwitch switchMarkdown = panel.findViewById(R.id.switch_markdown);
        if (switchMarkdown != null) {
            switchMarkdown.setChecked(false);
            switchMarkdown.setEnabled(false);
            switchMarkdown.setText("Markdown rendering (not available yet)");
        }

        com.google.android.material.materialswitch.MaterialSwitch switchThinking = panel.findViewById(R.id.switch_thinking);
        if (switchThinking != null) {
            switchThinking.setChecked(configStore.isShowThinking());
            switchThinking.setOnCheckedChangeListener((v, checked) -> configStore.setShowThinking(checked));
        }

        com.google.android.material.materialswitch.MaterialSwitch switchAutoRetry = panel.findViewById(R.id.switch_auto_retry);
        if (switchAutoRetry != null) {
            switchAutoRetry.setChecked(configStore.isAutoRetry());
            switchAutoRetry.setOnCheckedChangeListener((v, checked) -> configStore.setAutoRetry(checked));
        }

        com.google.android.material.chip.ChipGroup cgPerm = panel.findViewById(R.id.cg_perm_mode);
        if (cgPerm != null) {
            String current = configStore.getAgentPermMode();
            if ("full".equals(current)) cgPerm.check(R.id.chip_perm_full);
            else if ("consent".equals(current)) cgPerm.check(R.id.chip_perm_consent);
            else if ("strict".equals(current)) cgPerm.check(R.id.chip_perm_strict);
            
            cgPerm.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) configStore.clearAgentPermMode();
                else {
                    int id = checkedIds.get(0);
                    if (id == R.id.chip_perm_full) configStore.setAgentPermMode("full");
                    else if (id == R.id.chip_perm_consent) configStore.setAgentPermMode("consent");
                    else if (id == R.id.chip_perm_strict) configStore.setAgentPermMode("strict");
                }
            });
        }
        
        View btnReset = panel.findViewById(R.id.btn_reset_perm);
        if (btnReset != null) {
            btnReset.setOnClickListener(v -> {
                configStore.clearAgentPermMode();
                if (cgPerm != null) cgPerm.clearCheck();
                Snackbar.make(panel, "Permissions reset — will ask next time", Snackbar.LENGTH_SHORT).show();
            });
        }

        refreshReadback(panel);
    }

    private void refreshReadback(View panel) {
        TextView tv = panel.findViewById(R.id.ai_tv_readback);
        if (tv != null) {
            String report = "provider=" + configStore.getProviderId() +
                    "\nmodel=" + configStore.safeModel() +
                    "\nbaseUrl=" + configStore.getBaseUrl() +
                    "\nkey=" + pro.sketchware.ai.config.AIConfigStore.maskKey(configStore.safeKey());
            tv.setText(report);
        }
    }

    private void setupModelsPanel(View panel) {
        RecyclerView rv = panel.findViewById(R.id.ai_rv_models);
        if (rv != null) {
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
        }

        View btnSync = panel.findViewById(R.id.ai_btn_sync);
        if (btnSync != null) {
            btnSync.setOnClickListener(v -> {
                Snackbar.make(panel, "Open Settings to sync models", Snackbar.LENGTH_LONG)
                        .setAction("Open", v2 -> {
                            startActivity(new Intent(getActivity(), TagAssistantActivity.class));
                        }).show();
            });
        }

        TextView tvProvider = panel.findViewById(R.id.ai_tv_provider_name);
        TextView tvModel = panel.findViewById(R.id.ai_tv_current_model);
        String providerId = configStore.getSelectedProviderId();
        if (tvProvider != null) tvProvider.setText(AIProviderRegistry.getInstance(getContext()).get(providerId).displayName);
        if (tvModel != null) tvModel.setText(configStore.getModel(providerId));
    }

    private void setupAgentsPanel(View panel) {
        RecyclerView rv = panel.findViewById(R.id.ai_rv_tools);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(getContext()));
            ToolAdapter adapter = new ToolAdapter();
            rv.setAdapter(adapter);
            adapter.setTools(toolRegistry.all());
        }
    }

    private void setupFavoritesPanel(View panel) {
        RecyclerView rv = panel.findViewById(R.id.rv_favorites);
        if (rv == null) return;
        rv.setLayoutManager(new LinearLayoutManager(panel.getContext()));
        FavoritesAdapter adapter = new FavoritesAdapter();
        rv.setAdapter(adapter);
        adapter.reload();
    }

    private final class FavoritesAdapter extends RecyclerView.Adapter<FavoritesAdapter.FavoriteHolder> {
        private final List<FavoriteEntry> entries = new ArrayList<>();

        void reload() {
            entries.clear();
            for (ChatStore.Session session : chatStore.loadAllSessions()) {
                if (session.messages == null) continue;
                for (ChatStore.Message message : session.messages) {
                    if (message.favorite && "assistant".equals(message.role)) entries.add(new FavoriteEntry(session, message));
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public FavoriteHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView text = new TextView(parent.getContext());
            int padding = (int) (14 * parent.getResources().getDisplayMetrics().density);
            text.setPadding(padding, padding, padding, padding);
            text.setTextSize(14);
            text.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            text.setBackgroundResource(android.R.drawable.list_selector_background);
            return new FavoriteHolder(text);
        }

        @Override
        public void onBindViewHolder(@NonNull FavoriteHolder holder, int position) {
            FavoriteEntry entry = entries.get(position);
            String body = entry.message.text == null ? "" : entry.message.text;
            holder.text.setText("★  " + (body.length() > 240 ? body.substring(0, 240) + "…" : body) + "\nTap to remove from favorites");
            holder.text.setOnClickListener(v -> {
                entry.message.favorite = false;
                chatStore.saveSession(entry.session);
                reload();
                Snackbar.make(holder.text, "Removed from favorites", Snackbar.LENGTH_SHORT).show();
            });
            holder.text.setOnLongClickListener(v -> {
                rail.setSelectedItemId(R.id.ai_dest_session);
                currentSession = entry.session;
                if (chatAdapter != null) chatAdapter.setMessages(entry.session.messages);
                View sessionPanel = panels.get(R.id.ai_dest_session);
                if (sessionPanel != null) render(sessionPanel, PanelState.READY);
                scrollToBottom(false);
                return true;
            });
        }

        @Override public int getItemCount() { return entries.size(); }

        final class FavoriteHolder extends RecyclerView.ViewHolder {
            final TextView text;
            FavoriteHolder(TextView text) { super(text); this.text = text; }
        }
    }

    private static final class FavoriteEntry {
        final ChatStore.Session session;
        final ChatStore.Message message;
        FavoriteEntry(ChatStore.Session session, ChatStore.Message message) {
            this.session = session;
            this.message = message;
        }
    }

    private void setupHistoryPanel(View panel) {
        RecyclerView rv = panel.findViewById(R.id.ai_rv_history);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(getContext()));
            SessionAdapter adapter = new SessionAdapter();
            rv.setAdapter(adapter);

            adapter.setSessions(chatStore.loadAllSessions());
            adapter.setOnSessionClickListener(session -> {
                currentSession = session;
                View sessionPanel = panels.get(R.id.ai_dest_session);
                if (sessionPanel != null) render(sessionPanel, PanelState.READY);
                rail.setSelectedItemId(R.id.ai_dest_session);
                if (chatAdapter != null) {
                    chatAdapter.setMessages(session.messages);
                    scrollToBottom(false);
                }
            });
        }

        View btnNew = panel.findViewById(R.id.ai_fab_new_session);
        if (btnNew != null) {
            btnNew.setOnClickListener(v -> {
                currentSession = new ChatStore.Session();
                View sessionPanel = panels.get(R.id.ai_dest_session);
                if (sessionPanel != null) render(sessionPanel, PanelState.EMPTY);
                if (chatAdapter != null) {
                    chatAdapter.setMessages(new ArrayList<>());
                    scrollToBottom(false);
                }
                rail.setSelectedItemId(R.id.ai_dest_session);
            });
        }
    }

    private void setupSessionPanel(View panel) {
        baseSessionPaddingLeft = panel.getPaddingLeft();
        baseSessionPaddingTop = panel.getPaddingTop();
        baseSessionPaddingRight = panel.getPaddingRight();
        baseSessionPaddingBottom = panel.getPaddingBottom();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(panel, (v, insets) -> {
            int ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom;
            v.setPadding(baseSessionPaddingLeft, baseSessionPaddingTop, baseSessionPaddingRight,
                    baseSessionPaddingBottom + ime);
            return insets;
        });

        RecyclerView rv = panel.findViewById(R.id.ai_rv_chat);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        chatAdapter = new ChatAdapter();
        rv.setAdapter(chatAdapter);

        chatAdapter.setOnRateLimitActionListener(new ChatAdapter.OnRateLimitActionListener() {
            @Override
            public void onRetryNow() {
                if (orchestrator != null) orchestrator.skipWait();
                if (activeHandle != null) activeHandle.skipWait();
            }

            @Override
            public void onCancel() {
                forceIdle();
            }

            @Override
            public void onRetryExhausted() {
                startActivity(new Intent(getActivity(), TagAssistantActivity.class));
            }
        });

        chatAdapter.setOnMessageActionListener(new ChatAdapter.OnMessageActionListener() {
            @Override
            public void onCopy(ChatStore.Message message) {
                android.content.ClipboardManager cb = (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                cb.setPrimaryClip(android.content.ClipData.newPlainText("TAG Assistant", message.text));
                Snackbar.make(panel, R.string.ai_msg_copied, Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onEdit(ChatStore.Message message, int position) {
                EditText input = panel.findViewById(R.id.ai_et_input);
                if (input != null) {
                    input.setText(message.text);
                    input.requestFocus();
                }
                pendingEditIndex = position;
                View editBanner = panel.findViewById(R.id.ai_chip_edit_banner);
                if (editBanner != null) editBanner.setVisibility(View.VISIBLE);
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
                if (AIActivityMonitor.isBusy()) {
                    Snackbar.make(panel, R.string.ai_busy, Snackbar.LENGTH_SHORT).show();
                    return;
                }
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
                    AIActivityMonitor.setState(SessionState.IDLE);
                    sendMessage(lastUserText, true);
                }
            }

            @Override
            public void onFavorite(ChatStore.Message message) {
                if (currentSession != null) chatStore.saveSession(currentSession);
                View favoritesPanel = panels.get(R.id.ai_dest_favorites);
                if (favoritesPanel != null) {
                    RecyclerView favoritesList = favoritesPanel.findViewById(R.id.rv_favorites);
                    if (favoritesList != null && favoritesList.getAdapter() instanceof FavoritesAdapter) {
                        ((FavoritesAdapter) favoritesList.getAdapter()).reload();
                    }
                }
                Snackbar.make(panel, message.favorite ? "Added to favorites" : "Removed from favorites", Snackbar.LENGTH_SHORT).show();
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
                if (position > 0) {
                    ChatStore.Message prev = currentSession.messages.get(position - 1);
                    if ("user".equals(prev.role)) {
                        while (currentSession.messages.size() > position) {
                            currentSession.messages.remove(position);
                        }
                        chatAdapter.setMessages(currentSession.messages);
                        AIActivityMonitor.setState(SessionState.IDLE);
                        sendMessage(prev.text, true);
                    }
                }
            }

            @Override
            public void onStayInChat(int position) {
                UiPoster.post(() -> {
            if (!isAdded() || getView() == null) return;
                    currentSession.messages.remove(position);
                    chatAdapter.notifyItemRemoved(position);
                });
            }
        });

        List<ChatStore.Session> sessions = chatStore.loadAllSessions();
        if (sessions.isEmpty()) {
            currentSession = new ChatStore.Session();
            render(panel, PanelState.EMPTY);
        } else {
            currentSession = sessions.get(0);
            if (restoredSessionId != null) {
                for (ChatStore.Session savedSession : sessions) {
                    if (restoredSessionId.equals(savedSession.id)) {
                        currentSession = savedSession;
                        break;
                    }
                }
            }
            restoredSessionId = currentSession.id;
            chatAdapter.setMessages(currentSession.messages);
            render(panel, PanelState.READY);
            rv.post(() -> scrollToBottom(false));
        }

        EditText input = panel.findViewById(R.id.ai_et_input);
        FloatingActionButton send = panel.findViewById(R.id.ai_fab_send);
        if (send != null) {
            send.setOnClickListener(v -> {
                if (AIActivityMonitor.isBusy()) {
                    forceIdle();
                    ChatStore.Message note = new ChatStore.Message("system", getString(R.string.ai_stopped));
                    currentSession.messages.add(note);
                    chatAdapter.addMessage(note);
                    scrollToBottom(true);
                    return;
                }
                if (input == null) return;
                String text = input.getText().toString().trim();
                if (!text.isEmpty()) {
                    if (configStore.isHapticsEnabled()) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    }
                    if (pendingEditIndex != -1) {
                        while (currentSession.messages.size() > pendingEditIndex) {
                            currentSession.messages.remove(pendingEditIndex);
                        }
                        chatAdapter.setMessages(currentSession.messages);
                        pendingEditIndex = -1;
                        View editBanner = panel.findViewById(R.id.ai_chip_edit_banner);
                        if (editBanner != null) editBanner.setVisibility(View.GONE);
                    }
                    sendMessage(text);
                    input.setText("");
                }
            });
        }

        com.google.android.material.chip.Chip editBanner = panel.findViewById(R.id.ai_chip_edit_banner);
        if (editBanner != null) {
            editBanner.setOnCloseIconClickListener(v -> {
                pendingEditIndex = -1;
                editBanner.setVisibility(View.GONE);
                input.setText("");
            });
        }

        View dot = panel.findViewById(R.id.ai_connection_dot);
        if (connectionPulseAnimator != null) {
            connectionPulseAnimator.cancel();
        }
        if (dot != null) {
            connectionPulseAnimator = ObjectAnimator.ofFloat(dot, "alpha", 0.4f, 1.0f);
            connectionPulseAnimator.setDuration(1200);
            connectionPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            connectionPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
            connectionPulseAnimator.start();
        }

        Chip chipAgent = panel.findViewById(R.id.ai_chip_agent_state);
        if (chipAgent != null) {
            chipAgent.setOnClickListener(v -> {
                boolean next = !configStore.isAgentEnabled();
                configStore.setAgentEnabled(next);
                updateSessionHeader(panel);
                Snackbar.make(panel, next ? "Agent mode enabled" : "Agent mode off", Snackbar.LENGTH_SHORT).show();
            });
        }

        updateSessionHeader(panel);
    }

    private void updateSessionHeader(View panel) {
        TextView modelInfo = panel.findViewById(R.id.ai_tv_model_info);
        Chip chipAgent = panel.findViewById(R.id.ai_chip_agent_state);

        if (chipAgent != null) {
            boolean enabled = configStore.isAgentEnabled();
            chipAgent.setText(enabled ? R.string.ai_agent_on : R.string.ai_agent_off);
            int bgColor = pro.sketchware.utility.ThemeUtils.getColor(getContext(),
                    enabled ? R.attr.colorTertiaryContainer : R.attr.colorSurfaceContainerHigh);
            chipAgent.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(bgColor));
        }

        String model = configStore.safeModel();
        if (modelInfo != null) modelInfo.setText(model.isEmpty() ? "No model selected" : model);
    }

    private ChatStore.Message assistantMsg;
    private ChatStore.Message thinkingMsg;
    private ChatStore.Message toolsCard;
    private ChatStore.Message rateLimitMsg;
    private long thinkingStart = 0;
    private long rateLimitStartTime;
    private long rateLimitTotalWait;
    private int rateLimitAttempt;
    private int rateLimitMax;

    private void onContentToken(String token) {
        if (token == null || token.isEmpty()) return;
        pokeWatchdog();
        if (AIActivityMonitor.getState() == SessionState.WAITING_RATE_LIMIT) {
            AIActivityMonitor.setState(SessionState.MODEL_STREAMING);
        }
        if (!configStore.isStreamingEnabled()) return;
        UiPoster.post(() -> {
            if (!isAdded() || getView() == null) return;
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
            chatStore.saveSession(currentSession);
        });
    }

    private void onReasoningToken(String token) {
        if (token == null || token.isEmpty() || !configStore.isShowThinking()) return;
        pokeWatchdog();
        UiPoster.post(() -> {
            if (!isAdded() || getView() == null) return;
            if (thinkingMsg == null) {
                thinkingStart = System.currentTimeMillis();
                thinkingMsg = new ChatStore.Message("thinking", "");
                int pos = currentSession.messages.size();
                if (assistantMsg != null) pos = currentSession.messages.indexOf(assistantMsg);
                currentSession.messages.add(pos, thinkingMsg);
                chatAdapter.insertMessage(pos, thinkingMsg);
                scrollToBottom(true);
            }
            int idx = currentSession.messages.indexOf(thinkingMsg);
            chatAdapter.appendReasoning(idx, token);
            chatStore.saveSession(currentSession);
        });
    }

    private String getToolDomain(String name) {
        if (name == null) return null;
        if (name.contains("java")) return "JAVA";
        if (name.contains("res")) return "RES";
        if (name.contains("asset")) return "ASSET";
        if (name.contains("block")) return "BLOCK";
        if (name.contains("manifest")) return "MANIFEST";
        if (name.contains("fs_")) return "FS";
        if (name.contains("lib")) return "LIB";
        return "PROJ";
    }

    private int activeToolCount = 0;

    private void onRateLimitWait(long waitMs, int attempt, int maxAttempts) {
        pokeWatchdog();
        AIActivityMonitor.setState(SessionState.WAITING_RATE_LIMIT);
        UiPoster.post(() -> {
            if (!isAdded() || getView() == null) return;
            if (rateLimitMsg == null) {
                rateLimitMsg = new ChatStore.Message("rate_limit", "");
                currentSession.messages.add(rateLimitMsg);
                chatAdapter.addMessage(rateLimitMsg);
                scrollToBottom(true);
            }
            rateLimitMsg.toolState = "WAITING";
            rateLimitStartTime = System.currentTimeMillis();
            rateLimitTotalWait = waitMs;
            rateLimitAttempt = attempt;
            rateLimitMax = maxAttempts;
            chatStore.saveSession(currentSession);
        });
    }

    private void sendMessage(String text) {
        sendMessage(text, false);
    }

    private void sendMessage(String text, boolean isRegen) {
        if (!isAdded() || getView() == null || chatAdapter == null || currentSession == null) return;
        if (AIActivityMonitor.isBusy()) {
            Snackbar.make(requireView(), R.string.ai_busy, Snackbar.LENGTH_SHORT).show();
            return;
        }

        View sessionPanel = panels.get(R.id.ai_dest_session);
        if (sessionPanel != null) render(sessionPanel, PanelState.READY);
        if (!isRegen) {
            ChatStore.Message userMsg = new ChatStore.Message("user", text);
            currentSession.messages.add(userMsg);
            chatAdapter.addMessage(userMsg);
            scrollToBottom(true);
        }
        chatStore.saveSession(currentSession);

        assistantMsg = null;
        thinkingMsg = null;
        toolsCard = null;
        rateLimitMsg = null;
        thinkingStart = 0;
        lastEventTime = System.currentTimeMillis();
        mainHandler.removeCallbacks(watchdog);
        mainHandler.postDelayed(watchdog, 10000);

        DesignActivity activity = (DesignActivity) getActivity();
        if (activity == null) return;

        AIProvider provider = AIProviderRegistry.getInstance(activity).createActiveProvider(activity);
        if (provider == null) {
            addAssistantMessage("Error: Provider not found.");
            return;
        }

        AIActivityMonitor.setState(SessionState.MODEL_STREAMING);
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
            orchestrator.run(AgentOrchestrator.DEFAULT_SYSTEM_PROMPT, history, model, configStore.getTemperature(), new StreamCallbacks() {
                @Override
                public void onReasoningToken(String token) {
                    AssistantFragment.this.onReasoningToken(token);
                }

                @Override
                public void onToken(String token) {
                    AssistantFragment.this.onContentToken(token);
                }

                @Override
                public void onRateLimitWait(long waitMs, int attempt, int maxAttempts) {
                    AssistantFragment.this.onRateLimitWait(waitMs, attempt, maxAttempts);
                }

                @Override
                public void onToolCall(ToolCall call) {
                    pokeWatchdog();
                    UiPoster.post(() -> {
            if (!isAdded() || getView() == null) return;
                        if (thinkingMsg != null && "thinking".equals(thinkingMsg.role)) {
                            long elapsed = (System.currentTimeMillis() - thinkingStart) / 1000;
                            chatAdapter.completeThinking(currentSession.messages.indexOf(thinkingMsg), elapsed);
                        }
                    });
                }

                @Override
                public void onToolStart(String name) {
                    pokeWatchdog();
                    activeToolCount++;
                    AIActivityMonitor.setState(SessionState.TOOL_RUNNING);
                    UiPoster.post(() -> {
            if (!isAdded() || getView() == null) return;
                        if (toolsCard == null) {
                            toolsCard = new ChatStore.Message("tools_card", "");
                            toolsCard.toolEvents = new ArrayList<>();
                            currentSession.messages.add(toolsCard);
                            chatAdapter.addMessage(toolsCard);
                            scrollToBottom(true);
                        }
                        toolsCard.toolEvents.add(new ChatStore.ToolEvent(name, getToolDomain(name)));
                        int idx = currentSession.messages.indexOf(toolsCard);
                        chatAdapter.notifyItemChanged(idx, "tool_events");
                    });
                }

                @Override
                public void onToolEnd(String name, boolean success) {
                    pokeWatchdog();
                    activeToolCount = Math.max(0, activeToolCount - 1);
                    AIActivityMonitor.setState(activeToolCount > 0 ? SessionState.TOOL_RUNNING : SessionState.MODEL_STREAMING);
                    UiPoster.post(() -> {
            if (!isAdded() || getView() == null) return;
                        if (toolsCard != null) {
                            for (ChatStore.ToolEvent te : toolsCard.toolEvents) {
                                if (te.name.equals(name) && "RUNNING".equals(te.status)) {
                                    te.status = success ? "OK" : "ERROR";
                                    te.finishedAt = System.currentTimeMillis();
                                    break;
                                }
                            }
                            int idx = currentSession.messages.indexOf(toolsCard);
                            chatAdapter.notifyItemChanged(idx, "tool_events");
                        }
                    });
                }

                @Override
                public void onComplete(AIResponse response) {
                    mainHandler.removeCallbacks(watchdog);
                    UiPoster.post(() -> {
            if (!isAdded() || getView() == null) return;
                        reconcileTools(toolsCard);
                        if (thinkingMsg != null && "thinking".equals(thinkingMsg.role)) {
                            long elapsed = (System.currentTimeMillis() - thinkingStart) / 1000;
                            chatAdapter.completeThinking(currentSession.messages.indexOf(thinkingMsg), elapsed);
                        }
                        
                        String finalTest = (response == null || response.text == null) ? "" : response.text;
                        if (finalTest.isEmpty()) {
                            int toolCount = toolsCard != null ? toolsCard.toolEvents.size() : 0;
                            if (toolCount > 0) {
                                finalTest = "Done. Used " + toolCount + " tools.";
                            } else {
                                // Check if we were rate limited or cancelled
                                if (rateLimitMsg != null && "EXHAUSTED".equals(rateLimitMsg.toolState)) {
                                    finalTest = "Provider is consistently busy. Please try again later or switch models.";
                                }
                            }
                        }
                        
                        if (!finalTest.isEmpty()) {
                            if (assistantMsg == null) {
                                assistantMsg = new ChatStore.Message("assistant", finalTest);
                                currentSession.messages.add(assistantMsg);
                                chatAdapter.addMessage(assistantMsg);
                            } else {
                                assistantMsg.text = finalTest;
                                chatAdapter.notifyItemChanged(currentSession.messages.indexOf(assistantMsg));
                            }
                        }

                        AIActivityMonitor.setState(SessionState.IDLE);
                        chatStore.saveSession(currentSession);
                    });
                }

                @Override
                public void onError(Throwable error) {
                    mainHandler.removeCallbacks(watchdog);
                    UiPoster.post(() -> {
            if (!isAdded() || getView() == null) return;
                        reconcileTools(toolsCard);
                        if (thinkingMsg != null && "thinking".equals(thinkingMsg.role)) {
                            long elapsed = (System.currentTimeMillis() - thinkingStart) / 1000;
                            chatAdapter.completeThinking(currentSession.messages.indexOf(thinkingMsg), elapsed);
                        }
                        AIActivityMonitor.setState(SessionState.IDLE);
                        handleChatError(error);
                    });
                }

                @Override
                public void onCancel() {
                    mainHandler.removeCallbacks(watchdog);
                    UiPoster.post(() -> {
            if (!isAdded() || getView() == null) return;
                        reconcileTools(toolsCard);
                        AIActivityMonitor.setState(SessionState.IDLE);
                    });
                }
            });
        } else {
            if (configStore.isAutoSuggestEnabled() && AgentSuggester.needsAgent(text)) {
                ChatStore.Message suggestion = new ChatStore.Message("suggestion", "locked");
                currentSession.messages.add(suggestion);
                chatAdapter.addMessage(suggestion);
            }

            pro.sketchware.ai.core.AIRequest request = new pro.sketchware.ai.core.AIRequest.Builder()
                    .model(model)
                    .messages(history)
                    .thinkingEnabled(configStore.isShowThinking() && ThinkingDetector.hint(model))
                    .build();

            activeHandle = provider.stream(request, new StreamCallbacks() {
                @Override
                public void onReasoningToken(String token) {
                    AssistantFragment.this.onReasoningToken(token);
                }

                @Override
                public void onToken(String token) {
                    AssistantFragment.this.onContentToken(token);
                }

                @Override
                public void onRateLimitWait(long waitMs, int attempt, int maxAttempts) {
                    AssistantFragment.this.onRateLimitWait(waitMs, attempt, maxAttempts);
                }

                @Override
                public void onToolCall(ToolCall call) {}

                @Override
                public void onComplete(AIResponse response) {
                    mainHandler.removeCallbacks(watchdog);
                    UiPoster.post(() -> {
            if (!isAdded() || getView() == null) return;
                        if (thinkingMsg != null && "thinking".equals(thinkingMsg.role)) {
                            long elapsed = (System.currentTimeMillis() - thinkingStart) / 1000;
                            chatAdapter.completeThinking(currentSession.messages.indexOf(thinkingMsg), elapsed);
                        }
                        AIActivityMonitor.setState(SessionState.IDLE);
                        chatStore.saveSession(currentSession);
                        activeHandle = null;
                    });
                }

                @Override
                public void onError(Throwable error) {
                    mainHandler.removeCallbacks(watchdog);
                    UiPoster.post(() -> {
            if (!isAdded() || getView() == null) return;
                        if (thinkingMsg != null && "thinking".equals(thinkingMsg.role)) {
                            long elapsed = (System.currentTimeMillis() - thinkingStart) / 1000;
                            chatAdapter.completeThinking(currentSession.messages.indexOf(thinkingMsg), elapsed);
                        }
                        AIActivityMonitor.setState(SessionState.IDLE);
                        addAssistantMessage("Error: " + error.getMessage());
                        activeHandle = null;
                    });
                }

                @Override
                public void onCancel() {
                    mainHandler.removeCallbacks(watchdog);
                    UiPoster.post(() -> {
            if (!isAdded() || getView() == null) return;
                        AIActivityMonitor.setState(SessionState.IDLE);
                        activeHandle = null;
                    });
                }
            });
        }
    }

    private void reconcileTools(ChatStore.Message toolsCard) {
        if (toolsCard != null && toolsCard.toolEvents != null) {
            boolean changed = false;
            for (ChatStore.ToolEvent te : toolsCard.toolEvents) {
                if ("RUNNING".equals(te.status)) {
                    te.status = "ERROR";
                    te.finishedAt = System.currentTimeMillis();
                    changed = true;
                }
            }
            if (changed) {
                chatAdapter.notifyItemChanged(currentSession.messages.indexOf(toolsCard), "tool_events");
            }
        }
    }

    private void addAssistantMessage(String text) {
        ChatStore.Message msg = new ChatStore.Message("assistant", text);
        currentSession.messages.add(msg);
        chatAdapter.addMessage(msg);
        chatStore.saveSession(currentSession);
    }

    private void handleChatError(Throwable error) {
        addErrorMessage("Error: " + error.getMessage(), "", "Open settings");
    }

    private void addErrorMessage(String text, String raw, String action) {
        ChatStore.Message msg = new ChatStore.Message("error", text);
        msg.toolState = raw; 
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
            UiPoster.post(() -> {
            if (!isAdded() || getView() == null) return;
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
            FloatingActionButton fab = panel.findViewById(R.id.ai_fab_send);
            if (fab != null) {
                fab.setImageResource(AIActivityMonitor.getState() == SessionState.IDLE ? R.drawable.ic_mtrl_send : R.drawable.ic_mtrl_stop);
            }
        }
    }

    @Override
    public void onDestroyView() {
        mainHandler.removeCallbacks(watchdog);
        forceIdle();
        if (orchestrator != null) {
            orchestrator.shutdown();
            orchestrator = null;
        }
        activeHandle = null;
        if (voiceReader != null) voiceReader.stop();
        if (currentSession != null) {
            for (ChatStore.Message message : currentSession.messages) {
                if (message.toolEvents == null) continue;
                for (ChatStore.ToolEvent event : message.toolEvents) {
                    if ("RUNNING".equals(event.status)) {
                        event.status = "ERROR";
                        event.finishedAt = System.currentTimeMillis();
                    }
                }
            }
            if (chatStore != null) chatStore.saveSession(currentSession);
        }
        if (connectionPulseAnimator != null) {
            connectionPulseAnimator.cancel();
            connectionPulseAnimator = null;
        }
        for (int i = 0; i < panels.size(); i++) {
            View panel = panels.valueAt(i);
            if (panel != null) {
                panel.animate().cancel();
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(panel, null);
            }
        }
        panels.clear();
        rail = null;
        container = null;
        chatAdapter = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(watchdog);
        AIActivityMonitor.removeListener(stateListener);
        AIActivityMonitor.setOnTimeoutAction(null);

        // This fragment owns its request handles; never leave a request or
        // agent executor retaining a destroyed UI.
        if (orchestrator != null) {
            orchestrator.cancel();
            orchestrator.shutdown();
            orchestrator = null;
        }
        if (activeHandle != null) {
            activeHandle.cancel();
            activeHandle = null;
        }
        AIActivityMonitor.setState(SessionState.IDLE);

        if (voiceReader != null) voiceReader.shutdown();
        super.onDestroy();
    }
}
