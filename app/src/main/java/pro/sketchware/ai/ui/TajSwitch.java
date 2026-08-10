package pro.sketchware.ai.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import pro.sketchware.R;

/**
 * [WHAT] Custom canvas-drawn switch for TAJ Studio.
 * [WHY] P2-1: Supports Dynamic Colors (Monet) by resolving attributes at draw-time.
 * [HOW] Resolves R.attr.colorPrimary, R.attr.colorOnPrimary, etc.
 */
public class TajSwitch extends View {

    private boolean checked = false;
    private OnCheckedChangeListener listener;

    private final Paint paintTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintThumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();

    public interface OnCheckedChangeListener {
        void onCheckedChanged(boolean isChecked);
    }

    public TajSwitch(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paintTrack.setStyle(Paint.Style.FILL);
        paintThumb.setStyle(Paint.Style.FILL);
        paintOutline.setStyle(Paint.Style.STROKE);
        paintOutline.setStrokeWidth(2f);
    }

    public void setChecked(boolean b) { this.checked = b; invalidate(); }
    public boolean isChecked() { return checked; }
    public void setOnCheckedChangeListener(OnCheckedChangeListener l) { this.listener = l; }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float w = 52 * density;
        float h = 28 * density;
        float thumbRadius = 12 * density; // 24dp diameter

        float startX = (getWidth() - w) / 2f;
        float startY = (getHeight() - h) / 2f;
        trackRect.set(startX, startY, startX + w, startY + h);

        // P2-1 Dynamic Color Resolution
        int colorPrimary = getThemeColor(R.attr.colorPrimary);
        int colorOnPrimary = getThemeColor(R.attr.colorOnPrimary);
        int colorSurfaceVariant = getThemeColor(R.attr.colorSurfaceVariant);
        int colorOutline = getThemeColor(R.attr.colorOutline);

        if (checked) {
            paintTrack.setColor(colorPrimary);
            canvas.drawRoundRect(trackRect, h / 2f, h / 2f, paintTrack);

            paintThumb.setColor(colorOnPrimary);
            canvas.drawCircle(startX + w - thumbRadius - 2 * density, startY + h / 2f, thumbRadius, paintThumb);
        } else {
            paintTrack.setColor(colorSurfaceVariant);
            canvas.drawRoundRect(trackRect, h / 2f, h / 2f, paintTrack);

            paintOutline.setColor(colorOutline);
            canvas.drawRoundRect(trackRect, h / 2f, h / 2f, paintOutline);

            paintThumb.setColor(colorOutline);
            canvas.drawCircle(startX + thumbRadius + 2 * density, startY + h / 2f, thumbRadius, paintThumb);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        if (event.getAction() == MotionEvent.ACTION_UP) {
            performClick();
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        checked = !checked;
        invalidate();
        if (listener != null) listener.onCheckedChanged(checked);
        return super.performClick();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        setMeasuredDimension((int)(52 * density), (int)(28 * density));
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
