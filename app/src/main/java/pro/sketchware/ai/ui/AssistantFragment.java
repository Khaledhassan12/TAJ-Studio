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
                replacePane(new ModelsFragment());
                return true;
            }
            // Other rail items placeholders
            return true;
        });

        // Default pane
        if (savedInstanceState == null) {
            rail.setSelectedItemId(R.id.rail_models);
            replacePane(new ModelsFragment());
        }
    }

    private void replacePane(Fragment fragment) {
        getChildFragmentManager().beginTransaction()
                .replace(R.id.content_pane, fragment)
                .commit();
    }
}
