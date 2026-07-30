package com.besome.sketch;

import static com.google.android.material.theme.overlay.MaterialThemeOverlay.wrap;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.help.ProgramInfoActivity;
import com.besome.sketch.tools.NewKeyStoreActivity;
import com.google.android.material.navigation.NavigationView;

import a.a.a.mB;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.view.MenuItem;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import android.widget.FrameLayout;
import android.widget.LinearLayout;
import dev.chrisbanes.insetter.Insetter;
import dev.chrisbanes.insetter.Side;
import mod.hilal.saif.activities.tools.AppSettings;
import pro.sketchware.R;
import pro.sketchware.activities.about.AboutActivity;
import pro.sketchware.github.GitHubManager;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

public class MainDrawer extends NavigationView {
    private static final int DEF_STYLE_RES = R.style.Widget_SketchwarePro_NavigationView_Main;

    public MainDrawer(@NonNull Context context) {
        this(context, null);
    }

    public MainDrawer(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.navigationViewStyle);
    }

    public MainDrawer(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(wrap(context, attrs, defStyleAttr, DEF_STYLE_RES), attrs, defStyleAttr);
        context = getContext();

        var layoutDirection = context.getResources().getConfiguration().getLayoutDirection();
        Insetter.builder()
                .margin(WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.navigationBars(),
                        Side.create(layoutDirection == LAYOUT_DIRECTION_LTR,
                                false, layoutDirection == LAYOUT_DIRECTION_RTL, false))
                .applyToView(this);

        ViewGroup headerView = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.main_drawer_header, null);
        headerView.findViewById(R.id.status_bar_overlapper).setMinimumHeight(UI.getStatusBarHeight(context));

        addHeaderView(headerView);
        inflateMenu(R.menu.main_drawer_menu);
        
        GitHubManager.getInstance(context).addListener(signedIn -> updateGitHubItem());
        updateGitHubItem();

        setNavigationItemSelectedListener(item -> {
            initializeSocialLinks(item.getItemId());
            initializeDrawerItems(item.getItemId());

            // Return false to prevent selection
            return false;
        });
    }

    private void initializeSocialLinks(@IdRes int id) {
        if (!mB.a()) {
            @StringRes int url = -1;
            if (id == R.id.social_discord) {
                url = R.string.link_discord_invite;
            } else if (id == R.id.social_telegram) {
                url = R.string.link_telegram_invite;
            } else if (id == R.id.social_github) {
                url = R.string.link_github_url;
            } else if (id == R.id.app_sw_assist) {
                url = R.string.link_sw_assist;
            }

            if (url != -1) {
                openUrl(getContext().getString(url));
            }
        }
    }

    private void initializeDrawerItems(@IdRes int id) {
        Activity activity = unwrap(getContext());
        if (id == R.id.about_team) {
            Intent intent = new Intent(activity, AboutActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        } else if (id == R.id.changelog) {
            Intent intent = new Intent(activity, AboutActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("select", "changelog");
            activity.startActivity(intent);
        } else if (id == R.id.program_info) {
            Intent intent = new Intent(activity, ProgramInfoActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivityForResult(intent, 105);
        } else if (id == R.id.app_settings) {
            Intent intent = new Intent(activity, AppSettings.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        } else if (id == R.id.create_release_keystore) {
            Intent intent = new Intent(activity, NewKeyStoreActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        } else if (id == R.id.github_account) {
            handleGitHubAccountClick();
        }
    }

    private void updateGitHubItem() {
        MenuItem item = getMenu().findItem(R.id.github_account);
        if (item == null) return;

        GitHubManager manager = GitHubManager.getInstance(getContext());
        if (manager.isSignedIn()) {
            item.setTitle(getContext().getString(R.string.github_account_connected_as, manager.getUserLogin()));
            // Optionally load avatar if needed, but standard NavigationView items use static icons.
            // For now, we update the title as requested.
        } else {
            item.setTitle(R.string.github_account);
        }
    }

    private void handleGitHubAccountClick() {
        GitHubManager manager = GitHubManager.getInstance(getContext());
        if (manager.isSignedIn()) {
            showGitHubAccountOptions();
        } else {
            startGitHubAuth();
        }
    }

    private void showGitHubAccountOptions() {
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.github_account)
                .setItems(new CharSequence[]{
                        getContext().getString(R.string.github_account_open_profile),
                        getContext().getString(R.string.github_account_sign_out)
                }, (dialog, which) -> {
                    if (which == 0) {
                        String login = GitHubManager.getInstance(getContext()).getUserLogin();
                        openUrl("https://github.com/" + login);
                    } else if (which == 1) {
                        showSignOutConfirmation();
                    }
                })
                .show();
    }

    private void showSignOutConfirmation() {
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.github_account_sign_out)
                .setMessage(R.string.github_account_sign_out_confirm)
                .setPositiveButton(R.string.common_word_yes, (dialog, which) -> {
                    GitHubManager.getInstance(getContext()).signOut();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startGitHubAuth() {
        Context context = getContext();
        
        TextInputLayout textInputLayout = new TextInputLayout(context);
        textInputLayout.setHint(context.getString(R.string.github_account_token_hint));
        textInputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        
        TextInputEditText editText = new TextInputEditText(context);
        editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        textInputLayout.addView(editText);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) SketchwareUtil.getDip(16);
        layout.setPadding(padding, padding, padding, 0);

        android.widget.TextView descText = new android.widget.TextView(context);
        descText.setText(R.string.github_account_connect_desc);
        descText.setPadding(0, 0, 0, (int) SketchwareUtil.getDip(8));
        layout.addView(descText);

        android.widget.TextView linkText = new android.widget.TextView(context);
        linkText.setText(R.string.github_account_create_token_link);
        
        android.util.TypedValue typedValue = new android.util.TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorPrimary, typedValue, true);
        linkText.setTextColor(typedValue.data);
        
        linkText.setPadding(0, 0, 0, (int) SketchwareUtil.getDip(16));
        linkText.setOnClickListener(v -> openUrl("https://github.com/settings/tokens/new?scopes=repo,read:user&description=TAJ%20Studio"));
        layout.addView(linkText);

        layout.addView(textInputLayout);

        var dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.github_account_connect_title)
                .setView(layout)
                .setPositiveButton(R.string.github_account_connect, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.show();

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String token = editText.getText().toString().trim();
            if (token.isEmpty()) {
                textInputLayout.setError(context.getString(R.string.github_account_token_empty));
                return;
            }

            textInputLayout.setError(null);
            editText.setEnabled(false);
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setText(R.string.github_account_connecting);

            GitHubManager.getInstance(context).signInWithToken(token, new GitHubManager.SignInCallback() {
                @Override
                public void onSuccess(String login) {
                    dialog.dismiss();
                    Toast.makeText(context, context.getString(R.string.github_account_connected_success, login), Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    editText.setEnabled(true);
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setText(R.string.github_account_connect);
                    textInputLayout.setError(error);
                }
            });
        });
    }

    private void openUrl(String url) {
        Activity activity = unwrap(getContext());
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        activity.startActivity(intent);
    }

    private Activity unwrap(Context context) {
        while (!(context instanceof Activity) && context instanceof ContextWrapper) {
            context = ((ContextWrapper) context).getBaseContext();
        }

        return (Activity) context;
    }
}
