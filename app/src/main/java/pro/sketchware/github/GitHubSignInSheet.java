package pro.sketchware.github;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import pro.sketchware.R;
import pro.sketchware.databinding.SheetGithubSigninBinding;

/**
 * [R5-R8 State Maintenance] GitHubSignInSheet - Centralized sign-in state.
 * (عربي) واجهة تسجيل الدخول - إعادة هندسة الحالة: كاتب وحيد لتدفق المصادقة وحماية النقر المزدوج.
 */
public class GitHubSignInSheet extends BottomSheetDialogFragment {

    private static final String ARG_PROJECT_TITLE = "project_title";
    private SheetGithubSigninBinding binding;
    private String projectTitle;

    // R5: State Model
    private boolean signingIn = false;

    public static GitHubSignInSheet newInstance(String projectTitle) {
        GitHubSignInSheet fragment = new GitHubSignInSheet();
        Bundle args = new Bundle();
        args.putString(ARG_PROJECT_TITLE, projectTitle);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            projectTitle = getArguments().getString(ARG_PROJECT_TITLE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetGithubSigninBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Entry Animations
        binding.identityContainer.setScaleX(0f);
        binding.identityContainer.setScaleY(0f);
        binding.identityContainer.setAlpha(0f);

        binding.identityContainer.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(500)
                .setInterpolator(new OvershootInterpolator())
                .start();

        ObjectAnimator glowAnim = ObjectAnimator.ofFloat(binding.glowView, "alpha", 0.3f, 0.6f, 0.3f);
        glowAnim.setDuration(2000);
        glowAnim.setRepeatCount(ObjectAnimator.INFINITE);
        glowAnim.start();

        binding.btnHowTo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens/new?scopes=repo,read:user&description=TAJ%20Studio"));
            startActivity(intent);
        });

        binding.btnConnect.setOnClickListener(v -> {
            if (signingIn) return;
            
            String token = binding.tokenEditText.getText().toString().trim();
            if (token.isEmpty()) {
                binding.errorText.setText(R.string.github_upload_please_paste_token);
                binding.errorText.setVisibility(View.VISIBLE);
                return;
            }

            applySignInState(true);
            GitHubManager.getInstance(requireContext()).signInWithToken(token, new GitHubManager.SignInCallback() {
                @Override
                public void onSuccess(String login) {
                    if (isAdded()) {
                        applySignInState(false);
                        Toast.makeText(requireContext(), R.string.github_upload_success_toast, Toast.LENGTH_LONG).show();
                        dismissAllowingStateLoss();
                    }
                }

                @Override
                public void onError(String error) {
                    if (isAdded()) {
                        applySignInState(false);
                        binding.errorText.setText(error);
                        binding.errorText.setVisibility(View.VISIBLE);
                    }
                }
            });
        });
    }

    // --- Monopoly Writers (Single Source of Truth) ---

    /**
     * WHAT: applySignInState - The ONLY writer for the sign-in flow UI.
     * WHY: [R5] Synchronizes progress bar, button text, and input state.
     * (عربي) الكاتب الوحيد لحالة تسجيل الدخول - يضمن تجميد المدخلات وإظهار التقدم بدقة.
     */
    private void applySignInState(boolean loading) {
        signingIn = loading;
        if (binding == null) return;
        
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnConnect.setEnabled(!loading);
        binding.btnConnect.setText(loading ? R.string.github_account_connecting : R.string.github_account_connect);
        binding.tokenInputLayout.setEnabled(!loading);
        if (loading) binding.errorText.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
