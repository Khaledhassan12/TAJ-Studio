package pro.sketchware.ai.ui;

import android.content.Context;
import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * [WHAT] Helper to apply status-bar insets as top padding to AI headers.
 * [WHY] Fixes overlap with status bar in edge-to-edge mode (P2-HI).
 * [HOW] Sets a dimen-based fallback synchronously (lands BEFORE first draw, no
 * jump, and covers devices where the inset listener never dispatches), then a
 * ViewCompat.setOnApplyWindowInsetsListener overwrites it with the real value
 * (including 0 on landscape/gesture-bar devices => no phantom padding).
 */
public class AiHeaderInsets {

    public static void apply(View headerRoot) {
        if (headerRoot == null) return;

        // Synchronous fallback: padding present before first draw, and kept
        // only if the inset listener below is never dispatched.
        setTopPadding(headerRoot, getStatusBarHeight(headerRoot.getContext()));

        ViewCompat.setOnApplyWindowInsetsListener(headerRoot, (v, insets) -> {
            // Real value wins; may legitimately be 0 (no status bar).
            setTopPadding(v, insets.getInsets(WindowInsetsCompat.Type.statusBars()).top);
            return insets;
        });

        // Request dispatch once attached so the real insets land ASAP.
        if (ViewCompat.isAttachedToWindow(headerRoot)) {
            ViewCompat.requestApplyInsets(headerRoot);
        } else {
            headerRoot.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    v.removeOnAttachStateChangeListener(this);
                    ViewCompat.requestApplyInsets(v);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {}
            });
        }
    }

    private static void setTopPadding(View v, int top) {
        v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
    }

    private static int getStatusBarHeight(Context context) {
        int result = 0;
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = context.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }
}
