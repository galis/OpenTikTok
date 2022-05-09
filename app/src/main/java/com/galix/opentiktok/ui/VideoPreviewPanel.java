package com.galix.opentiktok.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
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
import com.galix.avcore.avcore.AVTransaction;
import com.galix.avcore.avcore.AVVideo;
import com.galix.avcore.util.LogUtil;
import com.galix.avcore.util.TimeUtils;
import com.galix.avcore.util.VideoUtil;
import com.galix.opentiktok.R;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE;
import static com.galix.avcore.avcore.AVEngine.VideoState.VideoStatus.SEEK;


/**
 * 视频预览面板
 *
 * @Author: Galis
 * @Date:2022.04.11
 */
public class VideoPreviewPanel extends RelativeLayout {

    private RecyclerView mThumbPreview;//缩略图预览
    private ClipView mClipView;
    private EffectView mEffectPreview;//特效提示
    private ImageView mSplitView;
    private Button mAddBtn;//添加视频按钮
    private Size mLayoutSize = new Size(0, 0);
    private int mCacheScrollX = 0;
    private boolean mIsDrag = false;

    //数据
    private AVEngine.VideoState mVideoState;
    private List<ViewType> mInfoList = new LinkedList<>();
    private int mThumbSize = 60;
    private static int ADD_SIZE = 40;

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

        mAddBtn = new Button(getContext());
        mAddBtn.setBackgroundResource(R.drawable.icon_add);

        RelativeLayout relativeLayout = new RelativeLayout(getContext());
        relativeLayout.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, compatSize(120)));
        addView(relativeLayout);

        //设置相关属性
        relativeLayout.addView(mThumbPreview, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mThumbSize));
        relativeLayout.addView(mClipView, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, mThumbSize + 2 * ClipView.LINE_WIDTH));
//        addView(mEffectPreview, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        relativeLayout.addView(mAddBtn, new RelativeLayout.LayoutParams(compatSize(ADD_SIZE), compatSize(ADD_SIZE)));
        relativeLayout.addView(mSplitView, new RelativeLayout.LayoutParams(
                compatSize(2),
                ViewGroup.LayoutParams.MATCH_PARENT));
        ((LayoutParams) mClipView.getLayoutParams()).addRule(CENTER_VERTICAL);
        ((LayoutParams) mClipView.getLayoutParams()).addRule(ALIGN_PARENT_LEFT);
        ((LayoutParams) mAddBtn.getLayoutParams()).addRule(CENTER_VERTICAL);
        ((LayoutParams) mAddBtn.getLayoutParams()).addRule(ALIGN_PARENT_RIGHT);
        ((LayoutParams) mSplitView.getLayoutParams()).addRule(CENTER_IN_PARENT);
        ((LayoutParams) mThumbPreview.getLayoutParams()).addRule(CENTER_IN_PARENT);

        //绑定回调
        mClipView.setClipCallback(new ClipView.ClipCallback() {
            @Override
            public void onClip(Rect src, Rect dst) {
                if (mClipCallback != null) {
                    mClipCallback.onClip(src, dst);
                }
                AVEngine.getVideoEngine().updateEdit(mVideoState.editComponent, false);
                updateClip();
            }
        });
        mThumbPreview.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AVEngine.getVideoEngine().seek(true);//进入seek模式
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    AVEngine.getVideoEngine().seek(false);//退出seek模式，处于暂停状态
                }
                updateCorrectScrollX();
                LogUtil.logMain("onScrollStateChanged@" + newState + "@" + getScrollX());
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updateCorrectScrollX();
                updateClip();
                updateEffect();
                if (recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE &&
                        AVEngine.getVideoEngine().getVideoState().status == SEEK) {
                    AVEngine.getVideoEngine().seek((long) (1000000.f / mThumbSize * mCacheScrollX));
                }
                LogUtil.logMain("onScrolled@" + mCacheScrollX);
            }
        });


        //文字，特效，贴纸
        mEffectPreview = new EffectView(getContext());
        mEffectPreview.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        RelativeLayout.LayoutParams layoutParams = (LayoutParams) mEffectPreview.getLayoutParams();
        layoutParams.topMargin = compatSize(100);
        addView(mEffectPreview);
    }

    private void updateCorrectScrollX() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) mThumbPreview.getLayoutManager();
        int firstPos = layoutManager.findFirstVisibleItemPosition();
        if (firstPos == 0) {
            mCacheScrollX = -layoutManager.findViewByPosition(0).getLeft();
        } else {
            int totalScroll = mLayoutSize.getWidth() / 2;
            for (int i = 1; i < firstPos; i++) {
                totalScroll += mInfoList.get(i).duration / 1000000.f * mThumbSize;
            }
            totalScroll += -layoutManager.findViewByPosition(firstPos).getLeft();
            mCacheScrollX = totalScroll;
        }
    }

    private void bindViewTreeCallback() {
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mLayoutSize = new Size(getMeasuredWidth(), getMeasuredHeight());
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
            List<AVComponent> avComponents = mVideoState.findComponents(AVComponent.AVComponentType.TRANSACTION, currentPts);
            if (!avComponents.isEmpty()) {
                AVTransaction avTransaction = (AVTransaction) avComponents.get(0);
                ViewType viewType = new ViewType(TYPE_TRAN);
                viewType.duration = avTransaction.getEngineDuration();
                viewType.type = TYPE_TRAN;
                viewType.component = avTransaction;
                viewType.clip = 0;
                mInfoList.add(viewType);
                currentPts += viewType.duration;
                continue;
            }
            avComponents = mVideoState.findComponents(AVComponent.AVComponentType.VIDEO, currentPts);
            if (avComponents.isEmpty()) {
                break;
            }
            AVVideo avVideo = (AVVideo) avComponents.get(0);
            mInfoList.add(new ViewType(TYPE_THUMB));
            mInfoList.get(mInfoList.size() - 1).component = avVideo;
            //计算正确的pts，针对单个文件
            long correctFilePts = currentPts - avVideo.getEngineStartTime() + avVideo.getClipStartTime();
            mInfoList.get(mInfoList.size() - 1).imgPath = VideoUtil.getThumbJpg(getContext(), avVideo.getPath(), correctFilePts);
            //处理开头不满1s
            if (correctFilePts % 1000000 != 0) {
                mInfoList.get(mInfoList.size() - 1).duration = TimeUtils.rightTime(correctFilePts) - correctFilePts;
                mInfoList.get(mInfoList.size() - 1).clip = 1;
                currentPts += mInfoList.get(mInfoList.size() - 1).duration;
                continue;
            }
            //处理最后一帧不满1s
            if (currentPts + 1000000 > avVideo.getEngineEndTime()) {
                mInfoList.get(mInfoList.size() - 1).duration = avVideo.getEngineEndTime() - currentPts;
                mInfoList.get(mInfoList.size() - 1).clip = 2;
                currentPts = avVideo.getEngineEndTime();
                continue;
            }
            //正常
            mInfoList.get(mInfoList.size() - 1).duration = 1000000;
            mInfoList.get(mInfoList.size() - 1).clip = 0;
            currentPts += 1000000;
        }
        mInfoList.add(new ViewType(TYPE_HEAD_FOOT));
        mThumbPreview.getAdapter().notifyDataSetChanged();

    }

    public void updateScroll(boolean isSmooth) {
        if (mVideoState == null) return;
        boolean needScroll = mVideoState.status == AVEngine.VideoState.VideoStatus.START
                || (mVideoState.status == AVEngine.VideoState.VideoStatus.PAUSE && mThumbPreview.getScrollState() == SCROLL_STATE_IDLE
        );
        if (needScroll) {
            int correctScrollX = (int) (mThumbSize / 1000000.f * AVEngine.getVideoEngine().getMainClock());
            if (isSmooth) {
                mThumbPreview.smoothScrollBy(correctScrollX - mCacheScrollX, 0);
            } else {
                mThumbPreview.scrollBy(correctScrollX - mCacheScrollX, 0);
            }
        }

    }


    public void updateClip() {
        if (mVideoState.isEdit) {
            updateCorrectScrollX();
            int eStartTime = (int) (mVideoState.editComponent.getEngineStartTime() / 1000000.f * mThumbSize);
            RelativeLayout.LayoutParams layoutParams = (LayoutParams) mClipView.getLayoutParams();
            layoutParams.width = (int) (mVideoState.editComponent.getEngineDuration() / 1000000.f * mThumbSize) + 2 * ClipView.DRAG_BTN_WIDTH;
            layoutParams.height = mThumbSize + 2 * ClipView.LINE_WIDTH;
            layoutParams.leftMargin = eStartTime + mLayoutSize.getWidth() / 2 -
                    ClipView.DRAG_BTN_WIDTH - mCacheScrollX;
            LogUtil.logMain("updateClip@" + mCacheScrollX + "@" + eStartTime);
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

    public void updateEffect() {
        mEffectPreview.update(mLayoutSize.getWidth() / 2, mCacheScrollX, mThumbSize);
    }

    private static class ViewType {
        public int type;
        public String imgPath;
        public AVComponent component;
        public long duration;
        public int clip;

        public ViewType(int type) {
            this.type = type;
        }
    }

    private static final int TYPE_HEAD_FOOT = 0;
    private static final int TYPE_THUMB = 1;
    private static final int TYPE_TRAN = 2;

    private static class ThumbViewHolder extends RecyclerView.ViewHolder {

        public ThumbViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    private class ThumbAdapter extends RecyclerView.Adapter<ThumbViewHolder> {

        @NonNull
        @Override
        public ThumbViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_TRAN) {
                VideoTranView videoTranView = new VideoTranView(parent.getContext(),
                        new RecyclerView.LayoutParams(mThumbSize, mThumbSize));
                return new ThumbViewHolder(videoTranView);
            }
            ImageView view = new ImageView(parent.getContext());
            view.setScaleType(ImageView.ScaleType.FIT_XY);
            view.setLayoutParams(new RecyclerView.LayoutParams(viewType == TYPE_HEAD_FOOT ?
                    mLayoutSize.getWidth() / 2 : mThumbSize, mThumbSize));
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
                                    if (viewType.clip == 2) {
                                        return Bitmap.createBitmap(toTransform, 0, 0,
                                                (int) (toTransform.getWidth() * viewType.duration / 1000000.f), toTransform.getHeight());
                                    } else {
                                        return Bitmap.createBitmap(toTransform, (int) (toTransform.getWidth() * (1000000.f - viewType.duration) / 1000000.f), 0,
                                                (int) (toTransform.getWidth() * viewType.duration / 1000000.f), toTransform.getHeight());
                                    }
                                }

                                @Override
                                public String getId() {
                                    return viewType.imgPath + "#" + position + "#" + viewType.duration + "_clip";
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
                        AVEngine.getVideoEngine().updateEdit(mInfoList.get(position).component, !mVideoState.isEdit);
                        updateClip();
                    }
                });
            } else if (viewType.type == TYPE_TRAN) {
                VideoTranView videoTranView = (VideoTranView) holder.itemView;
                videoTranView.updateTransaction((AVTransaction) mInfoList.get(position).component, mThumbSize);
                videoTranView.setAddCallback(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Map<String, Object> config = new HashMap<>();
                        config.put("tran_type", AVTransaction.TRAN_ALPHA);
                        config.put("tran_duration", 2000000L);
                        config.put("tran_visible", !mInfoList.get(position).component.isVisible());
                        AVEngine.getVideoEngine().changeComponent(mInfoList.get(position).component, config, new AVEngine.EngineCallback() {
                            @Override
                            public void onCallback(Object... args1) {
                                videoTranView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        updateData(AVEngine.getVideoEngine().getVideoState());
                                    }
                                });
                            }
                        });
                        AVEngine.getVideoEngine().seek(AVEngine.getVideoEngine().getMainClock());
                    }
                });
            } else {
                holder.itemView.getLayoutParams().width = mLayoutSize.getWidth() / 2;
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
