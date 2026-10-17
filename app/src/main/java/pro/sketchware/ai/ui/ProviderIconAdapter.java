package pro.sketchware.ai.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.ai.core.ProviderProfile;
import pro.sketchware.utility.ThemeUtils;

/**
 * Theme-aware provider picker. Each provider gets a compact monogram mark,
 * making the list scannable without relying on remote images or brand assets.
 * Colors resolve from Material theme attributes on every view bind.
 */
final class ProviderIconAdapter extends ArrayAdapter<ProviderProfile> {
    private final Context context;

    ProviderIconAdapter(Context context, List<ProviderProfile> profiles) {
        super(context, android.R.layout.simple_list_item_1, profiles);
        this.context = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return makeRow(position, convertView, parent, false);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return makeRow(position, convertView, parent, true);
    }

    private View makeRow(int position, View recycled, ViewGroup parent, boolean dropdown) {
        ProviderProfile profile = getItem(position);
        if (profile == null) return recycled == null ? new View(context) : recycled;

        LinearLayout row;
        TextView mark;
        TextView title;
        if (recycled instanceof LinearLayout
                && ((LinearLayout) recycled).getChildCount() >= 2
                && ((LinearLayout) recycled).getChildAt(0) instanceof TextView
                && ((LinearLayout) recycled).getChildAt(1) instanceof TextView) {
            row = (LinearLayout) recycled;
            mark = (TextView) row.getChildAt(0);
            title = (TextView) row.getChildAt(1);
        } else {
            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(dropdown ? 10 : 4), dp(16), dp(dropdown ? 10 : 4));

            mark = new TextView(context);
            mark.setGravity(Gravity.CENTER);
            mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            mark.setTextSize(11);
            LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(36), dp(36));
            markParams.setMarginEnd(dp(12));
            row.addView(mark, markParams);

            title = new TextView(context);
            title.setTextSize(16);
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }

        int bg = ThemeUtils.getColor(context, R.attr.colorSecondaryContainer);
        int fg = ThemeUtils.getColor(context, R.attr.colorOnSecondaryContainer);
        GradientDrawable badge = new GradientDrawable();
        badge.setShape(GradientDrawable.OVAL);
        badge.setColor(bg);
        mark.setBackground(badge);
        mark.setTextColor(fg);
        mark.setText(monogram(profile.id, profile.displayName));
        mark.setContentDescription(profile.displayName + " provider icon");
        title.setText(profile.displayName);
        title.setTextColor(ThemeUtils.getColor(context, R.attr.colorOnSurface));
        row.setContentDescription(profile.displayName);
        return row;
    }

    private String monogram(String id, String name) {
        String key = id == null ? "" : id.toLowerCase(Locale.ROOT);
        switch (key) {
            case "google-studio-ai": return "AI";
            case "openrouter": return "OR";
            case "huggingface": return "HF";
            case "perplexity": return "PX";
            case "anthropic": return "AN";
            case "deepseek": return "DS";
            case "openai": return "AI";
            case "agentrouter": return "AR";
            case "cerebras": return "CB";
            case "copilot": return "GH";
            case "alibaba": return "AL";
            case "together": return "TO";
            case "ollama": return "OL";
            case "custom": return "+";
            default:
                String clean = name == null ? "AI" : name.replaceAll("[^A-Za-z0-9]", "");
                return clean.length() >= 2 ? clean.substring(0, 2).toUpperCase(Locale.ROOT)
                        : clean.toUpperCase(Locale.ROOT);
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
