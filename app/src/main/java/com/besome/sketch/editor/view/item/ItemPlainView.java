package com.besome.sketch.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import androidx.annotation.NonNull;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.ItemView;
import a.a.a.wB;

/** Minimal visual representation for framework View, Divider and Space beans. */
public final class ItemPlainView extends View implements ItemView {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); private ViewBean bean; private boolean selected; private boolean fixed;
 public ItemPlainView(Context context){super(context);setWillNotDraw(false);paint.setStrokeWidth(wB.a(context,1.0F));}
 @Override public ViewBean getBean(){return bean;} @Override public void setBean(ViewBean value){bean=value;}
 @Override public boolean getFixed(){return fixed;} @Override public void setFixed(boolean value){fixed=value;}
 @Override public void setSelection(boolean value){selected=value;invalidate();}
 @Override protected void onDraw(@NonNull Canvas canvas){super.onDraw(canvas);if(bean!=null&&bean.convert!=null&&bean.convert.equals("View")){paint.setStyle(Paint.Style.FILL);paint.setColor(bean.layout.backgroundColor);canvas.drawRect(0,0,getWidth(),getHeight(),paint);}if(selected&&!fixed){paint.setStyle(Paint.Style.STROKE);paint.setColor(0xFF607D8B);canvas.drawRect(0,0,Math.max(1,getWidth()-1),Math.max(1,getHeight()-1),paint);}}
}
