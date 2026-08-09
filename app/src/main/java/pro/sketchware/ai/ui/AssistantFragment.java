package pro.sketchware.ai.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.navigationrail.NavigationRailView;
import pro.sketchware.R;
import pro.sketchware.ai.ui.conversations.ConversationsFragment;
import pro.sketchware.ai.ui.ModelsFragment;
import pro.sketchware.ai.ui.skills.SkillsFragment;
import pro.sketchware.ai.ui.workspace.WorkspaceFragment;

public class AssistantFragment extends Fragment {

    private String activeScId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_assistant, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Extract scId from activity if available
        if (getActivity() instanceof com.besome.sketch.design.DesignActivity) {
            activeScId = ((com.besome.sketch.design.DesignActivity) getActivity()).sc_id;
        }

        NavigationRailView rail = view.findViewById(R.id.rail);
        rail.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.rail_session) {
                showPane(new SessionFragment());
            } else if (id == R.id.rail_models) {
                showPane(new ModelsFragment());
            } else if (id == R.id.rail_skills) {
                showPane(new SkillsFragment());
            } else if (id == R.id.rail_workspace) {
                showPane(new WorkspaceFragment());
            } else if (id == R.id.rail_conversations) {
                showPane(new ConversationsFragment());
            }
            return true;
        });

        if (savedInstanceState == null) {
            rail.setSelectedItemId(R.id.rail_session);
            showPane(new SessionFragment());
        }
    }

    private void showPane(Fragment fragment) {
        getChildFragmentManager().beginTransaction()
                .replace(R.id.content_pane, fragment)
                .commit();
    }

    public String getActiveScId() {
        return activeScId;
    }

    public void triggerSkill(String fullMessage) {
        Fragment current = getChildFragmentManager().findFragmentById(R.id.content_pane);
        if (current instanceof SessionFragment) {
            ((SessionFragment) current).sendSystemBiasedMessage(fullMessage);
        } else {
            SessionFragment session = SessionFragment.newInstance(fullMessage);
            showPane(session);
            NavigationRailView rail = getView().findViewById(R.id.rail);
            rail.setSelectedItemId(R.id.rail_session);
        }
    }

    public void createNewSession() {
        showPane(new SessionFragment());
        NavigationRailView rail = getView().findViewById(R.id.rail);
        rail.setSelectedItemId(R.id.rail_session);
    }

    public void loadConversation(String conversationId) {
        SessionFragment session = SessionFragment.newInstanceWithHistory(conversationId);
        showPane(session);
        NavigationRailView rail = getView().findViewById(R.id.rail);
        rail.setSelectedItemId(R.id.rail_session);
    }
}
