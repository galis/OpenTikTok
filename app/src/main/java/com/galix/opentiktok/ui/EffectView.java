package com.galix.opentiktok.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.galix.avcore.avcore.AVComponent;
import com.galix.avcore.avcore.AVEngine;

import java.util.List;

public class EffectView extends LinearLayout {
    public EffectView(Context context) {
        super(context);
        setOrientation(VERTICAL);
    }

    public void update(int initX, int scrollX, int thumbSize) {
        List<AVComponent> components = AVEngine.getVideoEngine().getVideoState().findComponents(AVComponent.AVComponentType.STICKER, -1);
        components.addAll(AVEngine.getVideoEngine().getVideoState().findComponents(AVComponent.AVComponentType.WORD, -1));
        components.addAll(AVEngine.getVideoEngine().getVideoState().findComponents(AVComponent.AVComponentType.PAG, -1));
        if (components.size() != getChildCount()) {
            removeAllViews();
            for (AVComponent avComponent : components) {
                addView(genChild(avComponent));
            }
        }
        for (int i = 0; i < getChildCount(); i++) {
            LinearLayout.LayoutParams layoutParams = (LayoutParams) getChildAt(i).getLayoutParams();
            layoutParams.width = (int) (components.get(i).getEngineDuration() * thumbSize / 1000000.f);
            layoutParams.leftMargin = initX + (int) (components.get(i).getEngineStartTime() * thumbSize / 1000000.f - scrollX);
        }
        requestLayout();
    }

    private View genChild(AVComponent comm) {
        View child = new View(getContext());
        child.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 10));
        ((LayoutParams) child.getLayoutParams()).bottomMargin = 10;
        if (comm.getType() == AVComponent.AVComponentType.STICKER) {
            child.setBackgroundColor(Color.YELLOW);
        } else if (comm.getType() == AVComponent.AVComponentType.WORD) {
            child.setBackgroundColor(Color.WHITE);
        } else if (comm.getType() == AVComponent.AVComponentType.PAG) {
            child.setBackgroundColor(Color.BLUE);
        } else {
            child.setBackgroundColor(Color.RED);
        }
        return child;
    }


}
