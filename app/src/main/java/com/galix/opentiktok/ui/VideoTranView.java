package com.galix.opentiktok.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

import androidx.recyclerview.widget.RecyclerView;

import com.galix.avcore.avcore.AVTransaction;
import com.galix.avcore.avcore.AVVideo;
import com.galix.avcore.util.LogUtil;
import com.galix.avcore.util.TimeUtils;
import com.galix.avcore.util.VideoUtil;
import com.galix.opentiktok.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 转场控件
 *
 * @Date:2022.05.06
 * @Author: Galis
 */
public class VideoTranView extends RelativeLayout {

    private AVTransaction mAVTransaction;
    private Button mSplitBtn;
    private OnClickListener mOnClickListener;
    private Paint mPaint;
    private Paint mEmptyPaint;
    private Paint mXModelPaint;
    private Map<String, Bitmap> mCache = new HashMap<>();
    private int mThumbSize = 60;
    private Rect mSrc = new Rect();
    private Rect mDst = new Rect();
    private Path mPath = new Path();

    public VideoTranView(Context context, RecyclerView.LayoutParams layoutParams) {
        super(context);
        mSplitBtn = new Button(getContext());
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mEmptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mEmptyPaint.setColor(0x00000000);
        mEmptyPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        mXModelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mXModelPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
        mSplitBtn.setBackgroundResource(R.drawable.icon_split);
        mSplitBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mOnClickListener != null) {
                    mOnClickListener.onClick(v);
                }
            }
        });
        setLayoutParams(layoutParams);
        addView(mSplitBtn, new RelativeLayout.LayoutParams(compatSize(30), compatSize(30)));
        ((LayoutParams) mSplitBtn.getLayoutParams()).addRule(CENTER_VERTICAL);
        ((LayoutParams) mSplitBtn.getLayoutParams()).addRule(ALIGN_PARENT_LEFT);
        setWillNotDraw(false);
    }

    public void updateTransaction(AVTransaction transaction, int thumbSize) {
        mAVTransaction = transaction;
        mThumbSize = thumbSize;
        getLayoutParams().width = (int) (transaction.getEngineDuration() * thumbSize / 1000000.f);
        getLayoutParams().height = thumbSize;
        RelativeLayout.LayoutParams layoutParams = (LayoutParams) mSplitBtn.getLayoutParams();
        if (!transaction.isVisible()) {
            layoutParams.leftMargin = (int) ((transaction.trans1().getEngineEndTime() - transaction.getEngineStartTime()) * thumbSize / 1000000.f - compatSize(30) / 2);
        } else {
            layoutParams.leftMargin = (int) (((transaction.getClipStartTime() + transaction.getClipEndTime()) / 2 - transaction.getEngineStartTime()) * thumbSize / 1000000.f - compatSize(30) / 2);
        }
        mSplitBtn.setVisibility(VISIBLE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
//        canvas.drawColor(0x0000000);
        if (mAVTransaction != null) {
            if (!mAVTransaction.isVisible()) {
                long start = mAVTransaction.getEngineStartTime();
                long end = mAVTransaction.trans1().getEngineEndTime();
                int left = 0;
                int width = 0;
                int totalLeft = 0;
                //画左边
                while (start < end) {
                    if (end - start >= 1000000) {
                        width = mThumbSize;
                        canvas.drawBitmap(getCache(mAVTransaction.trans1(), start), totalLeft, 0, mPaint);
                        start += 1000000;
                    } else {
                        width = (int) (mThumbSize * (end - start) / 1000000.f);
                        mSrc.set(0, 0, width, mThumbSize);
                        mDst.set(totalLeft, 0, totalLeft + width, mThumbSize);
                        canvas.drawBitmap(getCache(mAVTransaction.trans1(), start), mSrc, mDst, mPaint);
                        start = end;
                    }
                    totalLeft += width;
                }

                //画右边
                end = mAVTransaction.getEngineEndTime();
                if (mAVTransaction.trans2().getClipStartTime() != 0) {//被裁剪过的。。逻辑真复杂。。
                    long correctDuration = (TimeUtils.rightTime(mAVTransaction.trans2().getEngineStartTime()) -
                            mAVTransaction.trans2().getEngineStartTime());
                    width = (int) (correctDuration / 1000000.f * mThumbSize);
                    left = mThumbSize - width;
                    mSrc.set(left, 0, mThumbSize, mThumbSize);
                    mDst.set(totalLeft, 0, totalLeft + width, mThumbSize);
                    canvas.drawBitmap(getCache(mAVTransaction.trans2(), start), mSrc, mDst, mPaint);
                    start += correctDuration;
                } else {
                    left = 0;
                    width = mThumbSize;
                    canvas.drawBitmap(getCache(mAVTransaction.trans2(), start), totalLeft, 0, mPaint);
                    start += 1000000;
                }
                totalLeft += width;

                while (start < end) {
                    if (end - start >= 1000000) {
                        width = mThumbSize;
                        canvas.drawBitmap(getCache(mAVTransaction.trans2(), start), totalLeft, 0, mPaint);
                        start += 1000000;
                    } else {
                        width = (int) (mThumbSize * (end - start) / 1000000.f);
                        mSrc.set(0, 0, width, mThumbSize);
                        mDst.set(totalLeft, 0, totalLeft + width, mThumbSize);
                        canvas.drawBitmap(getCache(mAVTransaction.trans2(), start), mSrc, mDst, mPaint);
                        start = end;
                    }
                    totalLeft += width;
                }

            } else {

                long start = mAVTransaction.getEngineStartTime();
                long end = mAVTransaction.getClipEndTime();
                int left = 0;
                int width = 0;
                int totalLeft = 0;
                while (start < end) {
                    if (start + 1000000 < end) {
                        canvas.drawBitmap(getCache(mAVTransaction.trans1(), start), totalLeft, 0, mPaint);
                        totalLeft += mThumbSize;
                        start += 1000000;
                    } else {
                        left = 0;
                        width = (int) ((end - start) / 1000000.f * mThumbSize);
                        mSrc.set(left, 0, left + width, mThumbSize);
                        mDst.set(totalLeft, 0, totalLeft + width, mThumbSize);
                        canvas.drawBitmap(getCache(mAVTransaction.trans1(), start), mSrc, mDst, mPaint);
                        totalLeft += width;
                        start = end;
                    }
                }

                //画转场三角形mask
                mPath.reset();
                int clipStartX = (int) ((mAVTransaction.getClipStartTime() -
                        mAVTransaction.getEngineStartTime()) * 1.0f / mAVTransaction.getEngineDuration() * getLayoutParams().width);
                int clipEndX = (int) (getLayoutParams().width -
                        ((mAVTransaction.getEngineEndTime() - mAVTransaction.getClipEndTime()) * 1.0f / mAVTransaction.getEngineDuration() * getLayoutParams().width));
                mPath.moveTo(clipStartX, getLayoutParams().height);
                mPath.lineTo(getLayoutParams().width, getLayoutParams().height);
                mPath.lineTo(clipEndX, 0);
                mPath.close();
                canvas.drawPath(mPath, mEmptyPaint);

                //画剩下的右边,mask作为蒙版。。。
                start = mAVTransaction.trans2().getEngineStartTime();
                end = mAVTransaction.getEngineEndTime();
                totalLeft = clipStartX;
                LogUtil.logEngine("draw tran#" + start + "#" + end);
                while (start < end) {
                    if (start + 1000000 <= end) {
                        canvas.drawBitmap(getCache(mAVTransaction.trans2(), start), totalLeft, 0, mXModelPaint);
                        totalLeft += mThumbSize;
                        start += 1000000;
                        LogUtil.logEngine("draw tran#start + 1000000 < end");
                    } else {
                        left = 0;
                        width = (int) ((end - start) / 1000000.f * mThumbSize);
                        mSrc.set(left, 0, left + width, mThumbSize);
                        mDst.set(totalLeft, 0, totalLeft + width, mThumbSize);
                        canvas.drawBitmap(getCache(mAVTransaction.trans2(), start), mSrc, mDst, mXModelPaint);
                        totalLeft += width;
                        start = end;
                        LogUtil.logEngine("draw tran#start + 1000000 " + width);
                    }
                }
            }
        }
        super.onDraw(canvas);
    }

    private Bitmap getCache(AVVideo avVideo, long pts) {
        String path = VideoUtil.getThumbJpg(getContext(), avVideo.getPath(),
                TimeUtils.quzheng(
                        TimeUtils.engineTime2FileTime(pts, avVideo)));
        if (mCache.containsKey(path) && !mCache.get(path).isRecycled()) {
            return mCache.get(path);
        }
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        mCache.put(path, bitmap);
        return bitmap;
    }

    public void setAddCallback(OnClickListener onClickListener) {
        mOnClickListener = onClickListener;
    }

    private int compatSize(int size) {
        return (int) (size * getContext().getResources().getDisplayMetrics().density);
    }

}
