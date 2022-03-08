package com.galix.opentiktok.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;


public class CustomHorizontalScrollView extends HorizontalScrollView {

    private ScrollStateChangeListener scrollStateChangeListener;

    public static enum State {
        DOWN,
        IDLE
    }

    public CustomHorizontalScrollView(Context context) {
        super(context);
    }

    public CustomHorizontalScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomHorizontalScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CustomHorizontalScrollView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (scrollStateChangeListener != null) {
                scrollStateChangeListener.onScrollStateChange(State.DOWN);
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (scrollStateChangeListener != null) {
                scrollStateChangeListener.onScrollStateChange(State.IDLE);
            }
        }
        return super.dispatchTouchEvent(event);
    }

    public void setScrollStateListener(ScrollStateChangeListener listener) {
        scrollStateChangeListener = listener;
    }

    public interface ScrollStateChangeListener {
        public void onScrollStateChange(State state);
    }
}
