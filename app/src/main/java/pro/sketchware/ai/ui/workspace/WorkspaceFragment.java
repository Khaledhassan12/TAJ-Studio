package pro.sketchware.ai.ui.workspace;

import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pro.sketchware.ai.agent.tools.Tool;
import pro.sketchware.ai.agent.tools.ToolRegistry;
import pro.sketchware.ai.context.ContextSnapshot;
import pro.sketchware.ai.context.ProjectContextManager;
import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.ui.AssistantFragment;
import pro.sketchware.databinding.FragmentWorkspaceBinding;

/**
 * [WHAT] Rail fragment for AI Workspace.
 * [WHY] Displays current project context, recent activity, and registered tools.
 * [HOW] Drives UI from WorkspaceState SSOT via applyWorkspace().
 */
public class WorkspaceFragment extends Fragment {

    private FragmentWorkspaceBinding binding;
    private AiStorage storage;

    private static class WorkspaceState {
        ContextSnapshot snapshot;
        String recentActivity;
        String toolList;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentWorkspaceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        storage = AiStorage.get(requireContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshWorkspace();
    }

    private void refreshWorkspace() {
        String scId = null;
        if (getParentFragment() instanceof AssistantFragment) {
            scId = ((AssistantFragment) getParentFragment()).getActiveScId();
        }

        WorkspaceState state = new WorkspaceState();
        
        if (scId != null) {
            state.snapshot = ProjectContextManager.snapshot(scId);
            
            // Recent Activity
            StringBuilder sb = new StringBuilder();
            try (Cursor c = storage.listAgentStepsByRecent(3)) { // Need to add this helper or use listAgentSteps
                while (c != null && c.moveToNext()) {
                    String action = c.getString(c.getColumnIndexOrThrow("action"));
                    long time = c.getLong(c.getColumnIndexOrThrow("createdAt"));
                    String date = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(time));
                    sb.append("• ").append(action).append(" (").append(date).append(")\n");
                }
            } catch (Exception ignored) {}
            state.recentActivity = sb.length() > 0 ? sb.toString().trim() : "No recent activity";
        } else {
            state.recentActivity = "Open a project to see its workspace";
        }

        // Tools
        StringBuilder toolsSb = new StringBuilder();
        List<Tool> tools = ToolRegistry.list();
        for (Tool t : tools) {
            toolsSb.append("• ").append(t.spec().name).append(": ").append(t.spec().description).append("\n");
        }
        state.toolList = toolsSb.toString().trim();

        applyWorkspace(state);
    }

    private void applyWorkspace(WorkspaceState state) {
        if (state.snapshot != null) {
            binding.tvProjectName.setText(state.snapshot.projectName);
            binding.tvPackageName.setText(state.snapshot.packageName);
            binding.tvSdks.setText("minSdk: " + state.snapshot.minSdk + " | targetSdk: " + state.snapshot.targetSdk);
        } else {
            binding.tvProjectName.setText("No active project");
            binding.tvPackageName.setText("---");
            binding.tvSdks.setText("---");
        }
        binding.tvRecentActivity.setText(state.recentActivity);
        binding.tvTools.setText(state.toolList);
    }
}
