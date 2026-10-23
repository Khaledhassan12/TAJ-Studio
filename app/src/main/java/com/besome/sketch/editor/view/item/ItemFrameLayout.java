package com.besome.sketch.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.EditorChildren;
import com.besome.sketch.editor.view.ItemView;
import com.besome.sketch.editor.view.ScrollContainer;

import a.a.a.wB;

/** FrameLayout preview container with editor selection and child ordering support. */
public final class ItemFrameLayout extends FrameLayout implements ItemView, ScrollContainer {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ViewBean bean;
    private boolean selected;
    private boolean fixed;
    public ItemFrameLayout(Context context) { super(context); setWillNotDraw(false); setMinimumWidth((int)wB.a(context,32)); setMinimumHeight((int)wB.a(context,32)); paint.setStrokeWidth(wB.a(context,2)); }
    @Override public void reindexChildren() { EditorChildren.reindex(this); }
    @Override public void addView(View child, int index) { super.addView(child, EditorChildren.insertionIndex(this,index)); }
    @Override public ViewBean getBean() { return bean; }
    @Override public void setBean(ViewBean value) { bean=value; }
    @Override public boolean getFixed() { return fixed; }
    @Override public void setFixed(boolean value) { fixed=value; }
    @Override public void setSelection(boolean value) { selected=value; invalidate(); }
    @Override protected void onDraw(@NonNull Canvas canvas) { if(!fixed){ if(selected){paint.setStyle(Paint.Style.FILL);paint.setColor(0x9599d5d0);canvas.drawRect(0,0,getWidth(),getHeight(),paint);} paint.setStyle(Paint.Style.STROKE);paint.setColor(0x60000000);canvas.drawRect(0,0,getWidth(),getHeight(),paint);} super.onDraw(canvas); }
    @Override public void setChildScrollEnabled(boolean enabled) { for(int i=0;i<getChildCount();i++){ View child=getChildAt(i); if(child instanceof ScrollContainer c)c.setChildScrollEnabled(enabled); if(child instanceof ItemHorizontalScrollView s)s.setScrollEnabled(enabled); if(child instanceof ItemVerticalScrollView s)s.setScrollEnabled(enabled); } }
}
