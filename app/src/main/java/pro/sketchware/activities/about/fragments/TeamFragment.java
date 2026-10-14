package pro.sketchware.activities.about.fragments;

import android.os.Bundle;
import android.os.Build;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import pro.sketchware.activities.about.adapters.TeamAdapter;
import pro.sketchware.activities.about.models.AboutAppViewModel;
import pro.sketchware.databinding.FragmentAboutAppBinding;

public class TeamFragment extends Fragment {
    private FragmentAboutAppBinding binding;
    private AboutAppViewModel aboutAppData;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAboutAppBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        aboutAppData = new ViewModelProvider(requireActivity()).get(AboutAppViewModel.class);

        applyExpressivePortraitEffects();
        initViews();
    }


    /**
     * Expressive layered portrait: blur the full-bleed backdrop while keeping
     * the foreground portrait crisp. Android 12+ provides native GPU blur;
     * older supported versions retain the dark gradient scrim as a graceful fallback.
     */
    private void applyExpressivePortraitEffects() {
        if (binding == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.maintainerBackdrop.setRenderEffect(
                    RenderEffect.createBlurEffect(22f, 22f, Shader.TileMode.CLAMP));
        }

        View hero = binding.maintainerHero;
        View portrait = binding.maintainerPortrait;
        hero.setAlpha(0f);
        hero.setTranslationY(12f);
        hero.animate().alpha(1f).translationY(0f).setDuration(520L).start();

        portrait.setScaleX(0.92f);
        portrait.setScaleY(0.92f);
        portrait.setAlpha(0f);
        portrait.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(120L).setDuration(420L).start();
    }

    private void initViews() {
        aboutAppData.getTeamMembers().observe(getViewLifecycleOwner(), teamArrayList -> {
            if (teamArrayList != null && !teamArrayList.isEmpty()) {
                binding.list.setAdapter(new TeamAdapter(teamArrayList));
            }
        });
    }
}
