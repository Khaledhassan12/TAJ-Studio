package pro.sketchware.ai.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import pro.sketchware.R;

public class AiPlaceholderFragment extends Fragment {

    public static AiPlaceholderFragment newInstance(String title, String subtitle, int icon) {
        AiPlaceholderFragment f = new AiPlaceholderFragment();
        Bundle b = new Bundle();
        b.putString("title", title);
        b.putString("subtitle", subtitle);
        b.putInt("icon", icon);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ai_placeholder, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            ((TextView) view.findViewById(R.id.tv_title)).setText(getArguments().getString("title"));
            ((TextView) view.findViewById(R.id.tv_subtitle)).setText(getArguments().getString("subtitle"));
            ((ImageView) view.findViewById(R.id.img_icon)).setImageResource(getArguments().getInt("icon"));
        }
    }
}
