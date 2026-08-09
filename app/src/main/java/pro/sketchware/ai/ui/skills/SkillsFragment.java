package pro.sketchware.ai.ui.skills;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.skills.Skill;
import pro.sketchware.ai.skills.SkillCatalog;
import pro.sketchware.ai.ui.AssistantFragment;
import pro.sketchware.ai.ui.SessionFragment;
import pro.sketchware.databinding.FragmentSkillsBinding;
import pro.sketchware.databinding.ItemSkillCardBinding;
import pro.sketchware.databinding.SheetSkillComposeBinding;

/**
 * [WHAT] Rail fragment for AI skills.
 * [WHY] Displays curated workflows for the user to trigger.
 * [HOW] Grid/list of Skill cards; triggers a compose sheet that hands off to Session.
 */
public class SkillsFragment extends Fragment {

    private FragmentSkillsBinding binding;
    private final List<Skill> skills = SkillCatalog.list();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSkillsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(new SkillAdapter());
        applySkillList();
    }

    private void applySkillList() {
        if (binding.recycler.getAdapter() != null) {
            binding.recycler.getAdapter().notifyDataSetChanged();
        }
    }

    private void openComposeSheet(Skill skill) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        SheetSkillComposeBinding sheetBinding = SheetSkillComposeBinding.inflate(getLayoutInflater());
        dialog.setContentView(sheetBinding.getRoot());

        sheetBinding.title.setText(skill.title);
        String hint = switch (skill.id) {
            case "code_review" -> "Which file/class should I review?";
            case "find_bugs" -> "What is the wrong behavior or crash log?";
            case "optimize" -> "Which part of the app feels slow?";
            default -> "Enter your request...";
        };
        sheetBinding.etRequest.setHint(hint);

        sheetBinding.btnRun.setOnClickListener(v -> {
            String request = sheetBinding.etRequest.getText().toString().trim();
            if (request.isEmpty()) return;

            String fullMessage = request + "\n\n[Skill: " + skill.title + "]\n" + skill.promptSuffix;
            
            // Hand off to SessionFragment
            if (getParentFragment() instanceof AssistantFragment) {
                ((AssistantFragment) getParentFragment()).triggerSkill(fullMessage);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private class SkillAdapter extends RecyclerView.Adapter<SkillViewHolder> {
        @NonNull @Override public SkillViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new SkillViewHolder(ItemSkillCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull SkillViewHolder holder, int position) {
            Skill skill = skills.get(position);
            holder.binding.title.setText(skill.title);
            holder.binding.subtitle.setText(skill.subtitle);
            int iconRes = requireContext().getResources().getIdentifier(skill.iconDrawableName, "drawable", requireContext().getPackageName());
            if (iconRes != 0) holder.binding.icon.setImageResource(iconRes);
            
            holder.itemView.setOnClickListener(v -> openComposeSheet(skill));
        }

        @Override public int getItemCount() { return skills.size(); }
    }

    private static class SkillViewHolder extends RecyclerView.ViewHolder {
        ItemSkillCardBinding binding;
        SkillViewHolder(ItemSkillCardBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }
}
