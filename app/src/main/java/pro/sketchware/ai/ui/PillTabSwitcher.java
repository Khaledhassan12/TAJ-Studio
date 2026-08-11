package pro.sketchware.ai.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import pro.sketchware.R;

/**
 * [WHAT] Two-segment pill tab switcher (P2-MCP editor transport selector).
 * [WHY] Mockup demands a full-pill segmented control: selected segment filled
 * with ?attr/colorPrimary + onPrimary bold text; unselected filled with
 * surfaceContainerHigh + onSurfaceVariant text.
 * [HOW] Pure Java/XML (D14): GradientDrawable backgrounds built at runtime from
 * theme-resolved colors (no hardcoded hex), outer corners rounded to a pill.
 */
public class PillTabSwitcher extends LinearLayout {

    public interface OnTabSelectedListener {
        void onTabSelected(int index);
    }

    private final TextView[] segments = new TextView[2];
    private int selected = 0;
    @Nullable
    private OnTabSelectedListener listener;

    public PillTabSwitcher(Context context) {
        this(context, null);
    }

    public PillTabSwitcher(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        for (int i = 0; i < 2; i++) {
            TextView tv = new TextView(context);
            LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(lp);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(12), dp(12), dp(12), dp(12));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tv.setOnClickListener(v -> {
                int index = (v == segments[0]) ? 0 : 1;
                if (index != selected) {
                    selected = index;
                    applySelection();
                    if (listener != null) listener.onTabSelected(index);
                }
            });
            segments[i] = tv;
            addView(tv);
        }
        applySelection();
    }

    public void setTabs(String first, String second) {
        segments[0].setText(first);
        segments[1].setText(second);
    }

    public void setSelectedIndex(int index) {
        if (index == 0 || index == 1) {
            selected = index;
            applySelection();
        }
    }

    public int getSelectedIndex() {
        return selected;
    }

    public void setOnTabSelectedListener(@Nullable OnTabSelectedListener listener) {
        this.listener = listener;
    }

    private void applySelection() {
        for (int i = 0; i < 2; i++) {
            TextView tv = segments[i];
            boolean isSelected = (i == selected);
            tv.setBackground(segmentBackground(i == 0, isSelected));
            tv.setTextColor(isSelected ? themeColor(com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)
                    : themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
            tv.setTypeface(tv.getTypeface(), isSelected ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    /** Half-pill per segment: outer corners rounded, inner corners square. */
    private GradientDrawable segmentBackground(boolean isLeft, boolean isSelected) {
        GradientDrawable bg = new GradientDrawable();
        float r = dpF(100); // large enough to clamp into a pill
        if (isLeft) {
            bg.setCornerRadii(new float[]{r, r, 0f, 0f, 0f, 0f, r, r});
        } else {
            bg.setCornerRadii(new float[]{0f, 0f, r, r, r, r, 0f, 0f});
        }
        bg.setColor(isSelected
                ? themeColor(R.attr.colorPrimary, Color.DKGRAY)
                : themeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh,
                        themeColor(com.google.android.material.R.attr.colorSurfaceVariant, Color.LTGRAY)));
        return bg;
    }

    /** Resolves a theme attribute to a color int without hardcoding palette values. */
    private int themeColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true)) {
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
            if (value.resourceId != 0) {
                try {
                    return ContextCompat.getColor(getContext(), value.resourceId);
                } catch (Exception ignored) {}
            }
        }
        return fallback;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dpF(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
