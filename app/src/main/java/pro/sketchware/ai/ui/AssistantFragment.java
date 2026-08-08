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

public class AssistantFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_assistant, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        NavigationRailView rail = view.findViewById(R.id.rail);
        rail.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.rail_models) {
                showPane(new ModelsFragment());
            } else if (id == R.id.rail_session) {
                showPane(new SessionFragment());
            } else if (id == R.id.rail_assistant) {
                showPane(AiPlaceholderFragment.newInstance("Assistant", "Personality tuning arrives in P4.", R.drawable.ic_mtrl_team));
            } else if (id == R.id.rail_skills) {
                showPane(AiPlaceholderFragment.newInstance("Skills", "Skill integration arrives in P5.", R.drawable.ic_mtrl_star));
            } else if (id == R.id.rail_tools) {
                showPane(AiPlaceholderFragment.newInstance("Tools", "Agent tools arrive in P5.", R.drawable.ic_mtrl_tune));
            } else if (id == R.id.rail_workspace) {
                showPane(AiPlaceholderFragment.newInstance("Workspace", "Project context arrives in P4.", R.drawable.ic_mtrl_folder));
            } else if (id == R.id.rail_conversations) {
                showPane(AiPlaceholderFragment.newInstance("History", "Chat history arrives in P2.", R.drawable.ic_mtrl_history));
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
}
