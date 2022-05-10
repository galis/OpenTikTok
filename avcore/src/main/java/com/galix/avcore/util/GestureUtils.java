package com.galix.avcore.util;

import android.graphics.PointF;
import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.RelativeLayout;


import com.galix.avcore.R;

import static android.widget.RelativeLayout.ALIGN_PARENT_LEFT;
import static android.widget.RelativeLayout.ALIGN_PARENT_TOP;
import static android.widget.RelativeLayout.CENTER_IN_PARENT;

/**
 * 设置view相关手势
 */
public class GestureUtils {
    private static final int POINT = R.id.tag_point;
    private static final String TAG = GestureUtils.class.getSimpleName();

    public static void setupView(View view, View.OnClickListener onClickListener) {
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                RelativeLayout parent = (RelativeLayout) v.getParent();
                PointF p = (PointF) view.getTag(POINT);
                if (p == null) {
                    p = new PointF();
                    v.setTag(POINT, p);
                }
                Log.d(TAG, "onTouch#" + event.toString());
                Log.d(TAG, "onTouch#" + "l#" + view.getLeft() +
                        "#t" + view.getTop() + "#r" + view.getRight() + "#b#" + view.getBottom());

                RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
                view.onTouchEvent(event);
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (layoutParams != null) {
                            layoutParams.removeRule(CENTER_IN_PARENT);
                            layoutParams.addRule(ALIGN_PARENT_LEFT);
                            layoutParams.addRule(ALIGN_PARENT_TOP);
                        }
                        layoutParams.leftMargin = view.getLeft();
                        layoutParams.topMargin = view.getTop();
                        view.setLayoutParams(layoutParams);
                        p.set(event.getRawX(), event.getRawY());
                        view.setBackgroundColor(0xffffffff);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float x = event.getRawX() - p.x;
                        float y = event.getRawY() - p.y;
                        p.set(event.getRawX(), event.getRawY());
                        Log.d(TAG, "ACTION_MOVE#x" + x + "#y" + y);
                        layoutParams.leftMargin += x;
                        layoutParams.topMargin += y;
                        layoutParams.leftMargin = MathUtils.clamp(0, parent.getLayoutParams().width - view.getMeasuredWidth(), layoutParams.leftMargin);
                        layoutParams.topMargin = MathUtils.clamp(0, parent.getLayoutParams().height - view.getMeasuredHeight(), layoutParams.topMargin);
                        view.setLayoutParams(layoutParams);
                        break;
                    case MotionEvent.ACTION_UP:
                        view.setBackgroundColor(0x0);
                        if (view instanceof EditText) {
                            ((EditText) view).clearFocus();
                        }
                        if (onClickListener != null) {
                            onClickListener.onClick(v);
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
    }
}
