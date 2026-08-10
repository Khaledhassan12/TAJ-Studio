package pro.sketchware.ai.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import pro.sketchware.R;

/**
 * [WHAT] Custom canvas-drawn slider for TAJ Studio.
 * [WHY] P2-1: Supports Dynamic Colors (Monet) by resolving attributes at draw-time.
 * [HOW] Resolves R.attr.colorPrimary, R.attr.colorSurfaceVariant, etc.
 */
public class TajSlider extends View {

    private float min = 0f;
    private float max = 1f;
    private float value = 0.5f;
    private float[] stops = null;

    private final Paint paintTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintThumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintDot = new Paint(Paint.ANTI_ALIAS_FLAG);

    private OnSliderChangeListener listener;

    public interface OnSliderChangeListener {
        void onValueChanged(float val);
    }

    public TajSlider(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paintTrack.setStyle(Paint.Style.FILL);
        paintFill.setStyle(Paint.Style.FILL);
        paintThumb.setStyle(Paint.Style.FILL);
        paintDot.setStyle(Paint.Style.FILL);
    }

    public void setRange(float min, float max) { this.min = min; this.max = max; invalidate(); }
    public void setStops(float[] stops) { this.stops = stops; invalidate(); }
    public void setValue(float val) { this.value = clamp(val); invalidate(); }
    public float getValue() { return value; }
    public void setOnSliderChangeListener(OnSliderChangeListener l) { this.listener = l; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float density = getResources().getDisplayMetrics().density;
        
        // P2-1 Dynamic Color Resolution
        int colorPrimary = getThemeColor(R.attr.colorPrimary);
        int colorSurfaceVariant = getThemeColor(R.attr.colorSurfaceVariant);
        int colorOnSurfaceVariant = getThemeColor(R.attr.colorOnSurfaceVariant);

        paintTrack.setColor(colorSurfaceVariant);
        paintFill.setColor(colorPrimary);
        paintThumb.setColor(colorPrimary);
        paintDot.setColor(colorOnSurfaceVariant);

        float w = getWidth();
        float h = getHeight();
        float centerY = h / 2f;

        float padding = 24 * density;
        float trackH = 12 * density;
        float thumbW = 4 * density;
        float thumbH = 48 * density;

        float startX = padding;
        float endX = w - padding;
        float availableW = endX - startX;

        // Draw track
        RectF trackRect = new RectF(startX, centerY - trackH/2f, endX, centerY + trackH/2f);
        canvas.drawRoundRect(trackRect, trackH/2f, trackH/2f, paintTrack);

        // Calculate thumb position
        float ratio = (value - min) / (max - min);
        float thumbX = startX + ratio * availableW;

        // Draw fill
        RectF fillRect = new RectF(startX, centerY - trackH/2f, thumbX, centerY + trackH/2f);
        canvas.drawRoundRect(fillRect, trackH/2f, trackH/2f, paintFill);

        // Draw stops (tick dots on remainder)
        if (stops != null) {
            for (float stop : stops) {
                float stopRatio = (stop - min) / (max - min);
                if (stopRatio > ratio) {
                    float sx = startX + stopRatio * availableW;
                    canvas.drawCircle(sx, centerY, 2 * density, paintDot);
                }
            }
        }

        // Draw thumb (vertical bar)
        RectF thumbRect = new RectF(thumbX - thumbW/2f, centerY - thumbH/2f, thumbX + thumbW/2f, centerY + thumbH/2f);
        canvas.drawRoundRect(thumbRect, thumbW/2f, thumbW/2f, paintThumb);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                updateValueFromTouch(event.getX());
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateValueFromTouch(float tx) {
        float w = getWidth();
        float density = getResources().getDisplayMetrics().density;
        float padding = 24 * density;
        float startX = padding;
        float availableW = w - 2 * padding;

        float ratio = (tx - startX) / availableW;
        float val = min + ratio * (max - min);

        if (stops != null) {
            val = findNearestStop(val);
        }

        val = clamp(val);
        if (val != value) {
            value = val;
            invalidate();
            if (listener != null) listener.onValueChanged(value);
        }
    }

    private float findNearestStop(float val) {
        if (stops == null || stops.length == 0) return val;
        float nearest = stops[0];
        float minDiff = Math.abs(val - nearest);
        for (float stop : stops) {
            float diff = Math.abs(val - stop);
            if (diff < minDiff) {
                minDiff = diff;
                nearest = stop;
            }
        }
        return nearest;
    }

    private float clamp(float val) {
        return Math.max(min, Math.min(max, val));
    }

    private int getThemeColor(int attrId) {
        TypedValue typedValue = new TypedValue();
        getContext().getTheme().resolveAttribute(attrId, typedValue, true);
        if (typedValue.resourceId != 0) {
            return ContextCompat.getColor(getContext(), typedValue.resourceId);
        }
        return typedValue.data;
    }
}
