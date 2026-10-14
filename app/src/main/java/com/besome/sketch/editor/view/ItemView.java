package com.besome.sketch.editor.view;

import android.view.View;
import android.view.ViewGroup;

import com.besome.sketch.beans.ViewBean;

// 'sy' is used in ViewPane items, example ItemLinearLayout
public interface ItemView {
    ViewBean getBean();

    void setBean(ViewBean viewBean);

    boolean getFixed();

    void setFixed(boolean fixed);

    void setSelection(boolean selection);

    /**
     * Shared insertion logic for custom designer containers.
     * Keeps logical child indices aligned when GONE children are present,
     * and safely appends when an index is outside the current child range.
     */
    static void addViewWithGoneOffset(ViewGroup parent, View child, int index, SuperAddView superAdd) {
        int childCount = parent.getChildCount();
        if (index < 0 || index >= childCount) {
            superAdd.addView(child, -1);
            return;
        }

        int goneIndex = -1;
        for (int i = 0; i < childCount; i++) {
            if (parent.getChildAt(i).getVisibility() == View.GONE) {
                goneIndex = i;
                break;
            }
        }

        int targetIndex = index;
        if (goneIndex >= 0 && index >= goneIndex) {
            targetIndex = index + 1;
        }

        superAdd.addView(child, targetIndex >= childCount ? -1 : targetIndex);
    }

    @FunctionalInterface
    interface SuperAddView {
        void addView(View child, int index);
    }
}
