package com.galix.opentiktok.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.galix.opentiktok.avcore.AVComponent;
import com.galix.opentiktok.avcore.AVVideo;
import com.galix.opentiktok.util.VideoUtil;

import java.util.LinkedList;

/**
 * 组件View，继承RecyclerView.具有选中/不选中，缩小，扩大功能
 */
public class ComponentView extends RelativeLayout {
    private static final int DRAG_HEAD = 0;
    private static final int DRAG_FOOT = 1;
    private static final int DRAG_IMG = 2;
    private static final int DRAG_ADD = 3;
    private static final int DRAG_MUTE = 4;
    private static final int DRAG_SPLIT = 5;
    private static final int THUMB_SLOT_WIDTH = 80;

    private int mTileSize = 0;
    private int mPaddingTopBottom = 0;
    private int mPaddingLeftRight = 0;
    private int mPaddingColor = 0;
    private Callback mCallback;
    private boolean mStatus;
    private AVComponent mAVComponent;
    private LinkedList<ThumbInfo> mThumbsList;
    private LinearLayout mVideoLayout;
    private ClipView mClipLayout;

    private ComponentView(@NonNull Context context) {
        super(context);
        mVideoLayout = new LinearLayout(context);
        mVideoLayout.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mClipLayout = new ClipView(context);
        mClipLayout.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(mVideoLayout);
        addView(mClipLayout);
        mClipLayout.setVisibility(INVISIBLE);
        mClipLayout.setClipCallback(new ClipView.ClipCallback() {
            @Override
            public void onClip(Rect src, Rect dst) {
                //TODO
            }
        });
    }

    private class ThumbInfo {
        public int type;
        public String imgPath;
        public long duration;
    }

    public void freshData() {
        if (mThumbsList == null) mThumbsList = new LinkedList<>();
        mThumbsList.clear();
        if (mAVComponent.getType() == AVComponent.AVComponentType.VIDEO) {
            AVVideo video = (AVVideo) mAVComponent;
            long pts = 0;
            while (pts < video.getDuration()) {
                ThumbInfo img = new ThumbInfo();
                img.type = DRAG_IMG;
                img.imgPath = VideoUtil.getThumbJpg(getContext(), video.getPath(), pts - video.getSrcStartTime());
                img.duration = 1000000;
                pts += 1000000;
                mThumbsList.add(img);
            }
            mThumbsList.get(mThumbsList.size() - 1).duration = video.getDuration() % 1000000;
        }
    }

    public void buildViews() {
        mVideoLayout.removeAllViews();
        for (ThumbInfo thumbInfo : mThumbsList) {
            if (mStatus && (thumbInfo.type == DRAG_HEAD || thumbInfo.type == DRAG_FOOT)) {
                View view = new View(getContext());
                view.setLayoutParams(new LinearLayout.LayoutParams(30, mTileSize));
                view.setBackgroundColor(Color.RED);
                view.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                break;
                            case MotionEvent.ACTION_MOVE:
                                break;
                            case MotionEvent.ACTION_UP:
                                break;
                        }
                        return true;
                    }
                });
                mVideoLayout.addView(view);
            } else if (thumbInfo.type == DRAG_IMG) {
                ImageView imageView = new ImageView(getContext());
                imageView.setLayoutParams(new LinearLayout.LayoutParams((int) ((thumbInfo.duration / 1000000.f) * (mTileSize - mPaddingTopBottom * 2)), mTileSize - mPaddingTopBottom * 2));
                imageView.setScaleType(ImageView.ScaleType.FIT_XY);
                imageView.setBackgroundColor(Color.BLUE);
                Glide.with(getContext())
                        .load(thumbInfo.imgPath)
                        .into(imageView);
                mVideoLayout.addView(imageView);
            }
        }
    }


    public interface Callback {
        void onDurationChange(long duration);
    }

    public void setStatus(boolean status) {
        mStatus = status;
        if (mStatus) {
            setPadding(mPaddingLeftRight, mPaddingTopBottom,
                    mPaddingLeftRight, mPaddingTopBottom);
            mClipLayout.setVisibility(VISIBLE);
        } else {
            setPadding(0, 0, 0, 0);
            mClipLayout.setVisibility(INVISIBLE);
        }
    }

    public void toggleStatus() {
        setStatus(!mStatus);
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public static class Builder {
        private final ComponentView componentView;

        public Builder(Context context) {
            componentView = new ComponentView(context);
        }

        public Builder setTileSize(int size) {
            componentView.mTileSize = size;
            return this;
        }

        public Builder setPaddingTopBottom(int size) {
            componentView.mPaddingTopBottom = size;
            return this;
        }

        public Builder setPaddingLeftRight(int size) {
            componentView.mPaddingLeftRight = size;
            return this;
        }

        public Builder setPaddingColor(int color) {
            componentView.mPaddingColor = color;
            return this;
        }

        public Builder setComponent(AVComponent avComponent) {
            componentView.mAVComponent = avComponent;
            return this;
        }

        public ComponentView build() {
            componentView.setPadding(componentView.mPaddingLeftRight, componentView.mPaddingTopBottom,
                    componentView.mPaddingLeftRight, componentView.mPaddingTopBottom);
            componentView.setBackgroundColor(componentView.mPaddingColor);
            if (componentView.mAVComponent == null) return null;
            if (!componentView.mAVComponent.isOpen()) {
                componentView.mAVComponent.open();
            }
            componentView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            componentView.freshData();
            componentView.buildViews();
            componentView.setStatus(false);
            componentView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    componentView.toggleStatus();
                }
            });
            return componentView;
        }

    }
}
