package com.galix.opentiktok.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.galix.avcore.avcore.AVComponent;
import com.galix.avcore.avcore.AVEngine;
import com.galix.avcore.avcore.AVVideo;
import com.galix.avcore.util.VideoUtil;
import com.galix.opentiktok.R;

import java.util.LinkedList;
import java.util.List;

import static androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING;
import static androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE;


/**
 * 视频预览面板
 *
 * @Author: Galis
 * @Date:2022.04.11
 */
public class VideoPreviewPanel extends RelativeLayout {

    private RecyclerView mThumbPreview;//缩略图预览
    private ClipView mClipView;
    private View mEffectPreview;//特效提示
    private ImageView mSplitView;
    private Button mTrans;//转场按钮
    private Button mAddBtn;//添加视频按钮
    private Size mCurrentViewSize = new Size(0, 0);
    private int mCacheScrollX = 0;
    private boolean mIsDrag = false;

    //数据
    private AVEngine.VideoState mVideoState;
    private List<ViewType> mInfoList = new LinkedList<>();
    private int mThumbSize = 60;

    public VideoPreviewPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        initChildes();
        initOther();
        bindViewTreeCallback();
    }

    private void initOther() {
    }

    private void initChildes() {
        //new
        mThumbSize = compatSize(mThumbSize);
        mThumbPreview = new RecyclerView(getContext());
        mThumbPreview.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        mThumbPreview.setAdapter(new ThumbAdapter());
        mClipView = new ClipView(getContext());
        mSplitView = new ImageView(getContext());
        mSplitView.setScaleType(ImageView.ScaleType.FIT_XY);
        mSplitView.setImageResource(R.drawable.drawable_video_split);
        mTrans = new Button(getContext());
        mAddBtn = new Button(getContext());
        //设置相关属性
        addView(mThumbPreview, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mThumbSize));
        addView(mClipView, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, mThumbSize + 2 * ClipView.LINE_WIDTH));
//        addView(mEffectPreview, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
//        addView(mTrans, new RelativeLayout.LayoutParams(compatSize(30), compatSize(30)));
//        addView(mAddBtn, new RelativeLayout.LayoutParams(compatSize(30), compatSize(30)));
        addView(mSplitView, new RelativeLayout.LayoutParams(
                compatSize(2),
                ViewGroup.LayoutParams.MATCH_PARENT));
        ((LayoutParams) mClipView.getLayoutParams()).addRule(CENTER_VERTICAL);
        ((LayoutParams) mSplitView.getLayoutParams()).addRule(CENTER_IN_PARENT);
        ((LayoutParams) mThumbPreview.getLayoutParams()).addRule(CENTER_IN_PARENT);

        //绑定回调
        mClipView.setClipCallback(new ClipView.ClipCallback() {
            @Override
            public void onClip(Rect src, Rect dst) {
                if (mClipCallback != null) {
                    mClipCallback.onClip(src, dst);
                }
            }
        });
        mThumbPreview.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == SCROLL_STATE_IDLE) {
                    mIsDrag = false;
                } else if (newState == SCROLL_STATE_DRAGGING) {
                    mIsDrag = true;
                }
                if (mOnScrollListener != null) {
                    mOnScrollListener.onScrollStateChanged(recyclerView, newState);
                }
                Log.d("onScrolled",newState+"#mOnScrollListener");
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (mOnScrollListener != null) {
                    mOnScrollListener.onScrolled(recyclerView, dx, dy);
                }
                mCacheScrollX += dx;
                updateClip();
                Log.d("onScrolled",mCacheScrollX+"#mCacheScrollX");
            }
        });
    }

    private void bindViewTreeCallback() {
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mCurrentViewSize = new Size(getMeasuredWidth(), getMeasuredHeight());
                getViewTreeObserver().removeOnGlobalLayoutListener(this);
                updateData(mVideoState);
            }
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    private int compatSize(int size) {
        return (int) (size * getContext().getResources().getDisplayMetrics().density);
    }

    public void updateData(AVEngine.VideoState videoState) {
        if (videoState == null) return;
        mVideoState = videoState;
        //处理预览缩略等相关数据
        mInfoList.clear();
        mInfoList.add(new ViewType(TYPE_HEAD_FOOT));
        long currentPts = 0;
        while (currentPts < videoState.durationUS) {
            AVVideo avVideo = (AVVideo) mVideoState.findComponents(AVComponent.AVComponentType.VIDEO, currentPts).get(0);
            if (avVideo == null) break;
            mInfoList.add(new ViewType(TYPE_THUMB));
            mInfoList.get(mInfoList.size() - 1).component = avVideo;
            //计算正确的pts，针对单个文件
            long correctFilePts = currentPts - avVideo.getEngineStartTime() + avVideo.getClipStartTime();
            mInfoList.get(mInfoList.size() - 1).imgPath = VideoUtil.getThumbJpg(getContext(), avVideo.getPath(), correctFilePts);
            //处理开头不满1s
            if (correctFilePts % 1000000 != 0) {
                mInfoList.get(mInfoList.size() - 1).duration = 1000000 - correctFilePts % 1000000;
                mInfoList.get(mInfoList.size() - 1).clipStart = false;
                currentPts += mInfoList.get(mInfoList.size() - 1).duration;
                continue;
            }
            //处理最后一帧不满1s
            if (currentPts + 1000000 > avVideo.getEngineEndTime()) {
                mInfoList.get(mInfoList.size() - 1).duration = avVideo.getEngineEndTime() - currentPts;
                mInfoList.get(mInfoList.size() - 1).clipStart = true;
                currentPts = avVideo.getEngineEndTime();
                continue;
            }
            //正常
            mInfoList.get(mInfoList.size() - 1).duration = 1000000;
            mInfoList.get(mInfoList.size() - 1).clipStart = true;
            currentPts += 1000000;
        }
        mInfoList.add(new ViewType(TYPE_HEAD_FOOT));
        mThumbPreview.getAdapter().notifyDataSetChanged();
    }

    public void updateScroll() {
        if (mVideoState.status == AVEngine.VideoState.VideoStatus.START) {
            int correctScrollX = (int) (mThumbSize / 1000000.f * AVEngine.getVideoEngine().getClock(mVideoState.extClock));
            mThumbPreview.smoothScrollBy(correctScrollX - mCacheScrollX, 0);
        }
    }


    public void updateClip() {
        if (mVideoState.isEdit) {
            RelativeLayout.LayoutParams layoutParams = (LayoutParams) mClipView.getLayoutParams();
            layoutParams.width = (int) (mVideoState.editComponent.getEngineDuration() / 1000000.f * mThumbSize) + 2 * ClipView.DRAG_BTN_WIDTH;
            layoutParams.height = mThumbSize + 2 * ClipView.LINE_WIDTH;
            layoutParams.leftMargin = (int) (mVideoState.editComponent.getEngineStartTime() / 1000000.f * mThumbSize) + mCurrentViewSize.getWidth() / 2 -
                    ClipView.DRAG_BTN_WIDTH - mCacheScrollX;
            mClipView.requestLayout();
        }
        mClipView.setVisibility(mVideoState.isEdit ? VISIBLE : GONE);
    }

    private ClipView.ClipCallback mClipCallback;
    private RecyclerView.OnScrollListener mOnScrollListener;

    public void setDragCallback(RecyclerView.OnScrollListener onScrollChangeListener) {
        mOnScrollListener = onScrollChangeListener;
    }

    public void setClipCallback(ClipView.ClipCallback clipCallback) {
        mClipCallback = clipCallback;
    }

    public void setBtnAddCallback(OnClickListener onClickListener) {
        mAddBtn.setOnClickListener(onClickListener);
    }

    public void setBtnTranCallback(OnClickListener onClickListener) {
        mTrans.setOnClickListener(onClickListener);
    }

    private static class ViewType {
        public int type;
        public String imgPath;
        public AVComponent component;
        public long duration;
        public boolean clipStart;

        public ViewType(int type) {
            this.type = type;
        }
    }

    private static final int TYPE_HEAD_FOOT = 0;
    private static final int TYPE_THUMB = 1;

    private static class ThumbViewHolder extends RecyclerView.ViewHolder {

        public ThumbViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    private class ThumbAdapter extends RecyclerView.Adapter<ThumbViewHolder> {

        @NonNull
        @Override
        public ThumbViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView view = new ImageView(parent.getContext());
            view.setScaleType(ImageView.ScaleType.FIT_XY);
            view.setLayoutParams(new RecyclerView.LayoutParams(viewType == TYPE_HEAD_FOOT ?
                    mCurrentViewSize.getWidth() / 2 : mThumbSize, mThumbSize));
            return new ThumbViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ThumbViewHolder holder, int position) {
            ViewType viewType = mInfoList.get(position);
            if (viewType.type == TYPE_THUMB) {
                holder.itemView.getLayoutParams().width = (int) (viewType.duration / 1000000.f * mThumbSize);
                if (viewType.duration < 1000000.f) {
                    Glide.with(getContext())
                            .load(viewType.imgPath)
                            .asBitmap()
                            .transform(new BitmapTransformation(getContext()) {
                                @Override
                                protected Bitmap transform(BitmapPool pool, Bitmap toTransform, int outWidth, int outHeight) {
                                    if (toTransform.getWidth() == holder.itemView.getLayoutParams().width) {
                                        return toTransform;
                                    }
                                    if (viewType.clipStart) {
                                        return Bitmap.createBitmap(toTransform, 0, 0,
                                                (int) (toTransform.getWidth() * viewType.duration / 1000000.f), toTransform.getHeight());
                                    } else {
                                        return Bitmap.createBitmap(toTransform, (int) (toTransform.getWidth() * (1000000.f - viewType.duration) / 1000000.f), 0,
                                                (int) (toTransform.getWidth() * viewType.duration / 1000000.f), toTransform.getHeight());
                                    }
                                }

                                @Override
                                public String getId() {
                                    return viewType.imgPath + "_clip";
                                }
                            }).into((ImageView) holder.itemView);
                } else {
                    Glide.with(getContext())
                            .load(viewType.imgPath)
                            .into((ImageView) holder.itemView);
                }
                holder.itemView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mVideoState.lock();
                        mVideoState.isEdit = !mVideoState.isEdit;
                        mVideoState.editComponent = mInfoList.get(position).component;
                        mVideoState.unlock();
                        updateClip();
                    }
                });
            } else {
                holder.itemView.getLayoutParams().width = mCurrentViewSize.getWidth() / 2;
                Glide.with(getContext())
                        .load("")
                        .into((ImageView) holder.itemView);
            }
        }

        @Override
        public int getItemCount() {
            return mInfoList.size();
        }

        @Override
        public int getItemViewType(int position) {
            return mInfoList.get(position).type;
        }
    }
}
