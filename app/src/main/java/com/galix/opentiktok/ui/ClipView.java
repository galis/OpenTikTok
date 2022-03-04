package com.galix.opentiktok.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * 裁剪View
 */
public class ClipView extends View {
    private static final String TAG = ClipView.class.getSimpleName();
    private static final int mPaintSize = 16;
    private static final int DRAG_BTN_WIDTH = 50;
    private static final int LINE_WIDTH = 5;
    private boolean mIsDrag = false;
    private boolean mDragLeft = false;
    private boolean mDragRight = false;
    private boolean mIsEdit = false;
    private Paint mPaint;
    private Rect[] mRect;
    private PointF mDownPoint = new PointF();
    private ClipCallback mClipCallback;

    public ClipView(Context context) {
        super(context);
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(0xAAFF0000);
        mPaint.setTextSize(mPaintSize);
        mRect = new Rect[6];
        for (int i = 0; i < 6; i++) {
            mRect[i] = new Rect();
        }
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (!mIsEdit) {
                            mIsEdit = true;
                            freshRoi();
                            return false;
                        }
                        mDragLeft = mRect[2].contains((int) event.getX(), (int) event.getY());
                        mDragRight = mRect[3].contains((int) event.getX(), (int) event.getY());
                        mDownPoint.x = event.getX();
                        mDownPoint.y = event.getY();
                        mIsDrag = mDragLeft || mDragRight;
                        if (mIsDrag) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        Log.d(TAG, "ACTION_DOWN" + event.toString());
                    case MotionEvent.ACTION_MOVE:
                        if (mIsDrag) {
                            if (mDragLeft) {
                                mRect[2].left = Math.max(0, (int) event.getX());
                                mRect[2].right = mRect[2].left + DRAG_BTN_WIDTH;
                                mRect[0].left = mRect[1].left = (int) event.getX();
                            } else if (mDragRight) {
                                mRect[3].left = Math.min((int) event.getX(), getMeasuredWidth() - DRAG_BTN_WIDTH);
                                mRect[3].right = mRect[3].left + DRAG_BTN_WIDTH;
                                mRect[0].right = mRect[1].right = (int) event.getX();
                            }
                        }
                        Log.d(TAG, "ACTION_MOVE" + event.toString());
                        break;
                    case MotionEvent.ACTION_UP:
                        mIsDrag = false;
                        getParent().requestDisallowInterceptTouchEvent(false);
                        if (event.getEventTime() - event.getDownTime() < 100) {
                            mIsEdit = !mIsEdit;
                            callbackIfNeed();
                            Log.d(TAG, "ACTION_UP" + event.toString());
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        getParent().requestDisallowInterceptTouchEvent(false);
                        mIsDrag = false;
                        if (event.getEventTime() - event.getDownTime() < 100) {
                            mIsEdit = !mIsEdit;
//                            callbackIfNeed();
                            Log.d(TAG, "ACTION_CANCEL" + event.toString());
                        }
                        break;
                }
                post(new Runnable() {
                    @Override
                    public void run() {
                        invalidate();
                    }
                });
                return true;
            }
        });
    }

    public void freshRoi() {
        mRect[0].left = 0;
        mRect[0].top = 0;
        mRect[0].right = getMeasuredWidth();
        mRect[0].bottom = mRect[0].top + LINE_WIDTH;

        mRect[1].left = 0;
        mRect[1].top = getMeasuredHeight() - LINE_WIDTH;
        mRect[1].right = getMeasuredWidth();
        mRect[1].bottom = mRect[1].top + LINE_WIDTH;

        mRect[2].left = 0;
        mRect[2].top = 0;
        mRect[2].right = mRect[2].left + DRAG_BTN_WIDTH;
        mRect[2].bottom = getMeasuredHeight();

        mRect[3].left = getMeasuredWidth() - DRAG_BTN_WIDTH;
        mRect[3].top = 0;
        mRect[3].right = mRect[3].left + DRAG_BTN_WIDTH;
        mRect[3].bottom = getMeasuredHeight();
    }

    public interface ClipCallback {
        void onClip(Rect src, Rect dst);
    }

    private void callbackIfNeed() {
        if (mClipCallback != null) {
            mRect[4].left = 0;
            mRect[4].right = getMeasuredWidth();
            mRect[5].left = mRect[2].left;
            mRect[5].right = mRect[3].right;
            mClipCallback.onClip(mRect[4], mRect[5]);
        }
    }

    public void setClipCallback(ClipCallback clipCallback) {
        mClipCallback = clipCallback;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mIsEdit) {
            freshRoi();
        }
        canvas.drawRect(mRect[0], mPaint);
        canvas.drawRect(mRect[1], mPaint);
        canvas.drawRect(mRect[2], mPaint);
        canvas.drawRect(mRect[3], mPaint);
    }
}
