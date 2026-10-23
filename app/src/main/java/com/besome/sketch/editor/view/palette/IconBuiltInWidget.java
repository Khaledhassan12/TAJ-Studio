package com.besome.sketch.editor.view.palette;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;

import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.ViewBean;

import mod.agus.jcoderz.beans.ViewBeans;
import pro.sketchware.R;

/** A typed palette entry for framework/AndroidX widgets that do not need a bespoke icon class. */
public final class IconBuiltInWidget extends IconBase {
    private final String widget;
    public IconBuiltInWidget(Context context, String widget) {
        super(context);
        this.widget = widget;
        setWidgetImage(switch (widget) {
            case "NestedScrollView" -> R.drawable.ic_mtrl_swap_vertical;
            case "ConstraintLayout" -> R.drawable.ic_mtrl_view_relative;
            case "TextInputEditText" -> R.drawable.ic_mtrl_edittext;
            case "MaterialSwitch" -> R.drawable.ic_mtrl_toggle;
            case "ImageButton" -> R.drawable.ic_mtrl_image;
            default -> R.drawable.ic_mtrl_rectangle;
        });
        setWidgetName(widget);
    }
    @Override public ViewBean getBean() {
        ViewBean bean = new ViewBean();
        switch (widget) {
            case "FrameLayout" -> { bean.type = ViewBeans.VIEW_TYPE_LAYOUT_FRAMELAYOUT; bean.convert = "FrameLayout"; bean.layout.width = -1; bean.layout.height = -2; bean.layout.orientation = LayoutBean.ORIENTATION_VERTICAL; }
            case "Include" -> { bean.type = ViewBeans.VIEW_TYPE_LAYOUT_INCLUDE; bean.convert = "include"; bean.layout.width = -1; }
            case "ConstraintLayout" -> { bean.type = ViewBeans.VIEW_TYPE_LAYOUT_CONSTRAINTLAYOUT; bean.convert = "androidx.constraintlayout.widget.ConstraintLayout"; bean.layout.width = -1; bean.layout.height = 240; bean.layout.paddingLeft = bean.layout.paddingTop = bean.layout.paddingRight = bean.layout.paddingBottom = 8; }
            case "NestedScrollView" -> { bean.type = ViewBeans.VIEW_TYPE_LAYOUT_NESTEDSCROLLVIEW; bean.convert = "androidx.core.widget.NestedScrollView"; bean.layout.width = -1; bean.layout.height = -1; }
            case "MaterialCardView" -> { bean.type = ViewBeans.VIEW_TYPE_LAYOUT_CARDVIEW; bean.convert = "com.google.android.material.card.MaterialCardView"; bean.layout.width = -1; bean.layout.orientation = LayoutBean.ORIENTATION_VERTICAL; bean.layout.paddingLeft = bean.layout.paddingTop = bean.layout.paddingRight = bean.layout.paddingBottom = 12; bean.inject = "app:cardCornerRadius=\"12dp\"\napp:cardElevation=\"2dp\""; }
            case "ImageButton" -> { bean.type = ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW; bean.convert = "ImageButton"; bean.image.resName = "default_image"; bean.layout.width = 48; bean.layout.height = 48; bean.inject = "android:background=\"?attr/selectableItemBackgroundBorderless\""; }
            case "View", "Divider", "Space" -> { bean.type = ViewBeans.VIEW_TYPE_WIDGET_VIEW; bean.convert = widget.equals("Space") ? "Space" : "View"; bean.layout.width = widget.equals("Divider") ? -1 : 48; bean.layout.height = widget.equals("Divider") ? 1 : 16; if (widget.equals("Divider")) { bean.layout.marginTop = 8; bean.layout.marginBottom = 8; bean.layout.backgroundColor = 0x1F000000; } else if (widget.equals("View")) bean.layout.backgroundColor = 0xFFE0E0E0; }
            case "TextInputEditText" -> { bean.type = ViewBean.VIEW_TYPE_WIDGET_EDITTEXT; bean.convert = "com.google.android.material.textfield.TextInputEditText"; bean.layout.width = -1; bean.text.hint = "Text"; }
            case "MaterialSwitch" -> { bean.type = ViewBean.VIEW_TYPE_WIDGET_SWITCH; bean.convert = "com.google.android.material.materialswitch.MaterialSwitch"; bean.layout.width = -1; bean.text.text = "Switch"; bean.layout.gravity = Gravity.CENTER_VERTICAL; }
            default -> { return null; }
        }
        return bean;
    }
}
