package pro.sketchware.activities.about;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.activities.about.fragments.BetaChangesFragment;
import pro.sketchware.activities.about.fragments.ChangeLogFragment;
import pro.sketchware.activities.about.fragments.TeamFragment;
import pro.sketchware.activities.about.models.AboutAppViewModel;
import pro.sketchware.activities.about.models.AboutResponseModel;
import pro.sketchware.databinding.ActivityAboutAppBinding;
import pro.sketchware.utility.Network;

public class AboutActivity extends BaseAppCompatActivity {

    private final Network network = new Network();
    public AboutAppViewModel aboutAppData;
    private ActivityAboutAppBinding binding;
    private SharedPreferences sharedPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityAboutAppBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        aboutAppData = new ViewModelProvider(this).get(AboutAppViewModel.class);
        sharedPref = getSharedPreferences("AppData", Activity.MODE_PRIVATE);

        initViews();
        initData();
    }

    private void initViews() {
        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.discordButton.setOnClickListener(v -> {
            String discordLink = aboutAppData.getDiscordInviteLink().getValue();
            if (discordLink != null) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(discordLink)));
            }
        });
        AboutAdapter adapter = new AboutAdapter(this);
        binding.viewPager.setOffscreenPageLimit(3);
        binding.viewPager.setAdapter(adapter);

        String[] tabTitles = new String[]{
                Helper.getResString(R.string.about_team_title),
                Helper.getResString(R.string.about_changelog_title),
                Helper.getResString(R.string.about_beta_changes_title)
        };

        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> tab.setText(tabTitles[position])).attach();

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    binding.discordButton.extend();
                } else {
                    binding.discordButton.shrink();
                }
            }
        });

        String toSelect = getIntent().getStringExtra("select");
        if (toSelect != null) {
            if ("changelog".equals(toSelect)) {
                binding.viewPager.setCurrentItem(1);
            } else if ("betaChanges".equals(toSelect)) {
                binding.viewPager.setCurrentItem(2);
            }
        }
    }

    private void initData() {
        network.get(Helper.getResString(R.string.link_about_team), response -> {
            Gson gson = new Gson();
            AboutResponseModel data = parseAboutResponse(gson, response);

            // Cache only a successfully parsed response. Never replace good data with an
            // HTTP error page, an empty body, or malformed JSON.
            if (data != null) {
                if (response != null) {
                    sharedPref.edit().putString("aboutData", response).apply();
                }
                publishAboutData(data);
                return;
            }

            String cachedResponse = sharedPref.getString("aboutData", null);
            data = parseAboutResponse(gson, cachedResponse);
            if (data != null) {
                publishAboutData(data);
            }
        });
    }

    private AboutResponseModel parseAboutResponse(Gson gson, String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }

        try {
            AboutResponseModel data = gson.fromJson(response, AboutResponseModel.class);
            // A valid JSON object must contain at least one expected payload field.
            if (data == null || (data.getTeam() == null && data.getChangelog() == null
                    && data.getDiscordInviteLink() == null)) {
                return null;
            }
            return data;
        } catch (JsonSyntaxException | IllegalStateException exception) {
            return null;
        }
    }

    private void publishAboutData(AboutResponseModel data) {
        aboutAppData.setDiscordInviteLink(data.getDiscordInviteLink());
        aboutAppData.setTeamMembers(data.getTeam() != null
                ? data.getTeam() : new ArrayList<>());
        aboutAppData.setChangelog(data.getChangelog() != null
                ? data.getChangelog() : new ArrayList<>());
    }

    // ----------------- classes ----------------- //

    public static class AboutAdapter extends FragmentStateAdapter {
        public AboutAdapter(AppCompatActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return switch (position) {
                case 1 -> new ChangeLogFragment();
                case 2 -> new BetaChangesFragment();
                default -> new TeamFragment();
            };
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }
}
