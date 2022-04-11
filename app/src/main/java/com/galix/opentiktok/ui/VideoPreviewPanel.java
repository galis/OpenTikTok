package com.galix.opentiktok.ui;

import android.content.Context;
import android.graphics.Canvas;
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
import com.galix.avcore.avcore.AVComponent;
import com.galix.avcore.avcore.AVEngine;
import com.galix.avcore.avcore.AVVideo;
import com.galix.avcore.util.VideoUtil;

import java.util.LinkedList;
import java.util.List;

/**
 * 视频预览面板
 *
 * @Author: Galis
 * @Date:2022.04.11
 */
public class VideoPreviewPanel extends RelativeLayout {

    private RecyclerView mThumbPreview;//缩略图预览
    private View mClipView;//裁剪
    private View mEffectPreview;//特效提示
    private Button mTrans;//转场按钮
    private Button mAddBtn;//添加视频按钮
    private Size mCurrentViewSize = new Size(0, 0);

    //数据
    private AVEngine.VideoState mVideoState;
    private List<ViewType> mInfoList = new LinkedList<>();
    private int mThumbSize = 50;

    public VideoPreviewPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        initChildes();
        bindViewTreeCallback();
        initOther();
    }

    private void initOther() {
        mThumbSize = (int) (getContext().getResources().getDisplayMetrics().density * mThumbSize);
    }

    private void initChildes() {
        mThumbPreview = new RecyclerView(getContext());
        mThumbPreview.setLayoutManager(new LinearLayoutManager(getContext()));
        mThumbPreview.setAdapter(new ThumbAdapter());
        mClipView = new ClipView(getContext());
        mEffectPreview = new ClipView(getContext());
        mTrans = new Button(getContext());
        mAddBtn = new Button(getContext());

        addView(mThumbPreview, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(mEffectPreview, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(mClipView, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(mTrans, new RelativeLayout.LayoutParams(30, 30));
        addView(mAddBtn, new RelativeLayout.LayoutParams(30, 30));

    }

    private void bindViewTreeCallback() {
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mCurrentViewSize = new Size(getMeasuredWidth(), getMeasuredHeight());
                getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    public void freshData(AVEngine.VideoState videoState) {
        mInfoList.clear();
        mInfoList.add(new ViewType(TYPE_HEAD_FOOT));
        long currentPts = 0;
        while (currentPts < videoState.durationUS) {
            AVVideo avVideo = (AVVideo) mVideoState.findComponents(AVComponent.AVComponentType.VIDEO, currentPts);
            if (avVideo == null) break;
            mInfoList.add(new ViewType(TYPE_THUMB));
            mInfoList.get(mInfoList.size() - 1).imgPath = VideoUtil.getThumbJpg(getContext(),
                    avVideo.getPath(),
                    currentPts - avVideo.getEngineStartTime() + avVideo.getClipStartTime());
            currentPts += 1000000;
        }
        mInfoList.add(new ViewType(TYPE_HEAD_FOOT));

        mThumbPreview.getAdapter().notifyDataSetChanged();
    }

    private static class ViewType {
        public int type;
        public String imgPath;
        public Object arg1;
        public Object arg2;

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
            view.setLayoutParams(new RecyclerView.LayoutParams(viewType == TYPE_HEAD_FOOT ?
                    mCurrentViewSize.getWidth() / 2 : mThumbSize, mThumbSize));
            return new ThumbViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ThumbViewHolder holder, int position) {
            ViewType viewType = mInfoList.get(position);
            if (viewType.type == TYPE_THUMB) {
                Glide.with(getContext())
                        .load(viewType.imgPath)
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
