package pro.sketchware.ai.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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
 * Theme-aware provider picker with locally drawn vector-style provider marks.
 * These are original visual identifiers, not official trademark artwork.
 */
final class ProviderIconAdapter extends ArrayAdapter<ProviderProfile> {
    private final Context context;

    ProviderIconAdapter(Context context, List<ProviderProfile> profiles) {
        super(context, android.R.layout.simple_list_item_1, profiles);
        this.context = context;
    }

    /** AutoCompleteTextView calls this when converting a selected object to text. */
    @Override
    public CharSequence convertResultToString(ProviderProfile profile) {
        return profile == null ? "" : profile.displayName;
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
        ProviderMarkView mark;
        TextView title;
        if (recycled instanceof LinearLayout
                && ((LinearLayout) recycled).getChildCount() >= 2
                && ((LinearLayout) recycled).getChildAt(0) instanceof ProviderMarkView
                && ((LinearLayout) recycled).getChildAt(1) instanceof TextView) {
            row = (LinearLayout) recycled;
            mark = (ProviderMarkView) row.getChildAt(0);
            title = (TextView) row.getChildAt(1);
        } else {
            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(dropdown ? 8 : 2), dp(16), dp(dropdown ? 8 : 2));

            mark = new ProviderMarkView(context);
            LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(38), dp(38));
            markParams.setMarginEnd(dp(12));
            row.addView(mark, markParams);

            title = new TextView(context);
            title.setTextSize(16);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }

        mark.setProvider(profile.id, profile.displayName);
        title.setText(profile.displayName);
        title.setTextColor(ThemeUtils.getColor(context, R.attr.colorOnSurface));
        row.setContentDescription(profile.displayName);
        return row;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Compact custom vector glyphs; no remote assets, font glyphs, or initials. */
    private final class ProviderMarkView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private String id = "";
        private String name = "";
        private int foreground;
        private int accent;

        ProviderMarkView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setProvider(String providerId, String displayName) {
            id = providerId == null ? "" : providerId.toLowerCase(Locale.ROOT);
            name = displayName == null ? "AI provider" : displayName;
            foreground = ThemeUtils.getColor(context, R.attr.colorOnSecondaryContainer);
            accent = ThemeUtils.getColor(context, R.attr.colorSecondary);
            invalidate();
            setContentDescription(name + " vector-style icon");
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            float s = Math.min(w, h);
            float cx = w / 2f, cy = h / 2f, u = s / 38f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ThemeUtils.getColor(context, R.attr.colorSecondaryContainer));
            canvas.drawRoundRect(new RectF(1*u, 1*u, w-1*u, h-1*u), 12*u, 12*u, paint);
            canvas.save();
            canvas.translate(cx, cy);
            canvas.scale(u, u);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(2.5f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(foreground);

            switch (id) {
                case "openai":
                    // Interlocking loop mark.
                    for (int i = 0; i < 6; i++) {
                        canvas.save();
                        canvas.rotate(i * 60f);
                        canvas.drawRoundRect(new RectF(-4.5f, -11f, 4.5f, 2f), 4.5f, 4.5f, paint);
                        canvas.restore();
                    }
                    break;
                case "anthropic":
                    drawRays(canvas, 0, 0, 11, 8, paint);
                    break;
                case "gemini": case "google-studio-ai": case "google":
                    path.reset(); path.moveTo(0,-12); path.cubicTo(2,-4,4,-2,12,0);
                    path.cubicTo(4,2,2,4,0,12); path.cubicTo(-2,4,-4,2,-12,0);
                    path.cubicTo(-4,-2,-2,-4,0,-12); path.close();
                    canvas.drawPath(path, paint);
                    paint.setStyle(Paint.Style.FILL); paint.setColor(accent);
                    canvas.drawCircle(0,0,2.4f,paint);
                    break;
                case "deepseek":
                    path.reset(); path.moveTo(-12,1); path.cubicTo(-9,-8,-1,-10,5,-5);
                    path.cubicTo(11,-8,13,-2,9,2); path.cubicTo(6,5,2,4,0,8);
                    path.cubicTo(-5,10,-11,7,-12,1); path.close();
                    canvas.drawPath(path, paint);
                    canvas.drawCircle(4,-2,1.2f,paint);
                    break;
                case "meta":
                    path.reset(); path.moveTo(-13,5); path.cubicTo(-10,-11,-5,-11,0,1);
                    path.cubicTo(5,13,10,11,13,-5); path.cubicTo(10,-11,5,-11,0,1);
                    path.cubicTo(-5,13,-10,11,-13,5); canvas.drawPath(path, paint);
                    break;
                case "copilot":
                    canvas.drawCircle(-4,-3,6,paint); canvas.drawCircle(4,3,6,paint);
                    paint.setStyle(Paint.Style.FILL); paint.setColor(accent);
                    canvas.drawCircle(-5,-4,1.4f,paint); canvas.drawCircle(5,4,1.4f,paint);
                    break;
                case "perplexity":
                    drawRays(canvas, 0, 0, 10, 5, paint);
                    canvas.drawRect(-5,-5,5,5,paint);
                    break;
                case "openrouter": case "agentrouter": case "together":
                    canvas.drawLine(-9,0,0,-7,paint); canvas.drawLine(0,-7,9,0,paint);
                    canvas.drawLine(-9,0,0,8,paint); canvas.drawLine(0,8,9,0,paint);
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(-9,0,2.5f,paint); canvas.drawCircle(0,-7,2.5f,paint);
                    canvas.drawCircle(9,0,2.5f,paint); canvas.drawCircle(0,8,2.5f,paint);
                    break;
                case "ollama":
                    path.reset(); path.moveTo(-9,9); path.lineTo(-11,-5); path.lineTo(-5,-2);
                    path.lineTo(-2,-10); path.lineTo(2,-10); path.lineTo(5,-2);
                    path.lineTo(11,-5); path.lineTo(9,9); path.cubicTo(4,13,-4,13,-9,9);
                    canvas.drawPath(path, paint); canvas.drawCircle(-4,2,1,paint); canvas.drawCircle(4,2,1,paint);
                    break;
                case "huggingface":
                    canvas.drawArc(new RectF(-11,-10,11,10), 200, 140, false, paint);
                    canvas.drawCircle(-5,-1,1.3f,paint); canvas.drawCircle(5,-1,1.3f,paint);
                    canvas.drawArc(new RectF(-6,-3,6,7), 15, 150, false, paint);
                    break;
                case "nvidia":
                    canvas.drawOval(new RectF(-12,-8,12,8),paint);
                    canvas.drawCircle(0,0,5,paint); canvas.drawCircle(1,0,1.8f,paint);
                    break;
                case "grok":
                    canvas.drawLine(-9,-9,9,9,paint); canvas.drawLine(9,-9,-9,9,paint);
                    break;
                case "groq": case "cerebras":
                    canvas.drawRoundRect(new RectF(-9,-9,9,9),3,3,paint);
                    for(int i=-1;i<=1;i++){canvas.drawLine(-13,i*5,-9,i*5,paint);canvas.drawLine(9,i*5,13,i*5,paint);}
                    break;
                case "qwen": case "alibaba":
                    path.reset(); path.moveTo(-10,8); path.lineTo(-5,-7); path.lineTo(0,2);
                    path.lineTo(5,-7); path.lineTo(10,8); canvas.drawPath(path,paint);
                    break;
                case "zai":
                    canvas.drawRoundRect(new RectF(-9,-9,9,9),2,2,paint);
                    canvas.drawLine(-4,-3,4,-3,paint); canvas.drawLine(-4,2,2,2,paint);
                    break;
                case "mistral":
                    for(int i=0;i<4;i++) canvas.drawLine(-9,-7+i*5,9-(i%2)*5,-7+i*5,paint);
                    break;
                case "ai21":
                    canvas.drawOval(new RectF(-12,-5,12,5),paint);
                    canvas.drawCircle(0,0,3,paint);
                    break;
                case "azure":
                    path.reset(); path.moveTo(-11,7); path.lineTo(-5,-7); path.lineTo(1,7);
                    path.lineTo(5,-2); path.lineTo(11,7); path.close(); canvas.drawPath(path,paint);
                    break;
                default:
                    // Clean abstract AI-node mark for custom and future providers.
                    canvas.drawCircle(0,0,4,paint);
                    canvas.drawCircle(-9,-7,2.5f,paint); canvas.drawCircle(9,-7,2.5f,paint);
                    canvas.drawCircle(-9,7,2.5f,paint); canvas.drawCircle(9,7,2.5f,paint);
                    canvas.drawLine(-7,-5,-3,-2,paint); canvas.drawLine(7,-5,3,-2,paint);
                    canvas.drawLine(-7,5,-3,2,paint); canvas.drawLine(7,5,3,2,paint);
                    break;
            }
            canvas.restore();
        }

        private void drawRays(Canvas canvas, float x, float y, float outer, float inner, Paint p) {
            path.reset();
            for (int i=0;i<8;i++) {
                double a = Math.PI * i / 4d;
                float r = (i % 2 == 0) ? outer : inner;
                float px = x + (float)Math.cos(a)*r;
                float py = y + (float)Math.sin(a)*r;
                if (i == 0) path.moveTo(px,py); else path.lineTo(px,py);
            }
            path.close();
            canvas.drawPath(path,p);
        }
    }
}
