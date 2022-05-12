package com.galix.opentiktok.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * 视频导出
 *
 * @Author: Galis
 * @Date:2022.03.21
 */
public class ExportProgressView extends androidx.appcompat.widget.AppCompatImageView {

    private static final String TAG = ExportProgressView.class.getSimpleName();
    private Bitmap mBitmap;
    private int mProgress;
    private Paint mPaint;
    private Paint mTextPaint;
    private Path mPath;
    private Path mCurrentPath;
    private int mCurrentX, mCurrentY;
    private boolean mIsFirst = true;
    private static int PADDING = 10;

    public ExportProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        PADDING *= (int) context.getResources().getDisplayMetrics().density;
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mTextPaint.setTextSize(64);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setStrokeWidth(3);
        mTextPaint.setColor(Color.WHITE);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(Color.WHITE);
        mPaint.setStrokeWidth(PADDING);
        mPath = new Path();
        mCurrentPath = new Path();

        setPadding(PADDING / 2, PADDING / 2, PADDING / 2, PADDING / 2);
    }

    public void setProgress(Bitmap bitmap, int progress) {
        mBitmap = bitmap;
        mProgress = progress;
        mCurrentPath.reset();
        setImageBitmap(mBitmap);
        getLayoutParams().height = (int) (getLayoutParams().width * (mBitmap.getHeight() * 1.f / mBitmap.getWidth()));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();

        super.onDraw(canvas);
        mPath.reset();
        mPath.moveTo(0,0);
        mPath.addRect(0, 0, width, height, Path.Direction.CCW);
        mPath.close();
        mPaint.setColor(Color.GRAY);
        canvas.drawPath(mPath, mPaint);
//
        mCurrentPath.reset();
        mCurrentX = mCurrentY = 0;
        mCurrentPath.moveTo(mCurrentX, mCurrentY);
        int progress = mProgress;

        if (progress > 0) {
            mCurrentX = (int) (width * Math.min(1.f, progress / 25.f));
            mCurrentPath.lineTo(mCurrentX, 0);
        }
        progress -= 25;
        if (progress > 0) {
            mCurrentY = (int) (height * Math.min(1.f, progress / 25.f));
            mCurrentPath.lineTo(mCurrentX, mCurrentY);
        }
        progress -= 25;
        if (progress > 0) {
            mCurrentX -= (int) (width * Math.min(1.f, progress / 25.f));
            mCurrentPath.lineTo(mCurrentX, mCurrentY);
        }
        progress -= 25;
        if (progress > 0) {
            mCurrentY -= (int) (height * Math.min(1.f, progress / 25.f));
            mCurrentPath.lineTo(mCurrentX, mCurrentY);
        }
        mPaint.setColor(Color.WHITE);
        canvas.drawPath(mCurrentPath, mPaint);

        canvas.drawText(mProgress + "%", width / 2, height / 2, mTextPaint);

    }
}
