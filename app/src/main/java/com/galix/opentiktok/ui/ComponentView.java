package com.galix.opentiktok.ui;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.galix.avcore.avcore.AVComponent;
import com.galix.avcore.avcore.AVVideo;
import com.galix.avcore.util.VideoUtil;

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
    private ClipCallback mClipCallback;
    private boolean mStatus;
    private LinkedList<AVComponent> mAVComponents;
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
                if (mClipCallback != null) {
                    mClipCallback.onClip(ComponentView.this, mAVComponents, src, dst);
                }
            }
        });
    }

    private class ThumbInfo {
        public int type;
        public String imgPath;
        public long duration;
    }

    private void freshData() {
        if (mThumbsList == null) mThumbsList = new LinkedList<>();
        mThumbsList.clear();
        for (AVComponent avComponent : mAVComponents) {
            if (avComponent.getType() == AVComponent.AVComponentType.VIDEO) {
                AVVideo video = (AVVideo) avComponent;
                long pts = video.getClipStartTime();
                while (pts < video.getClipEndTime()) {
                    ThumbInfo img = new ThumbInfo();
                    img.type = DRAG_IMG;
                    img.imgPath = VideoUtil.getThumbJpg(getContext(), video.getPath(), pts);
                    img.duration = Math.min(video.getClipEndTime() - pts, 1000000 - pts % 1000000);
                    pts = (pts / 1000000 + 1) * 1000000;
                    mThumbsList.add(img);
                }
            }
        }
    }

    public void buildViews() {
        setStatus(false);
        freshData();
        mVideoLayout.removeAllViews();
        for (ThumbInfo thumbInfo : mThumbsList) {
            if (thumbInfo.type == DRAG_IMG) {
                ImageView imageView = new ImageView(getContext());
                imageView.setLayoutParams(new LinearLayout.LayoutParams((int) ((thumbInfo.duration / 1000000.f) * (mTileSize - mPaddingTopBottom * 2)), mTileSize - mPaddingTopBottom * 2));
                imageView.setScaleType(ImageView.ScaleType.FIT_XY);
                Glide.with(getContext())
                        .load(thumbInfo.imgPath)
                        .into(imageView);
                mVideoLayout.addView(imageView);
            }
        }
    }

    public void setStatus(boolean status) {
        mStatus = status;
        if (mStatus) {
            mClipLayout.setVisibility(VISIBLE);
        } else {
            mClipLayout.setVisibility(INVISIBLE);
        }
    }

    public void toggleStatus() {
        setStatus(!mStatus);
    }

    public interface ClipCallback {
        void onClip(ComponentView view, LinkedList<AVComponent> avComponents, Rect src, Rect dst);
    }

    public interface UpdateCallback {
        void onUpdate();
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

        public Builder setClipCallback(ClipCallback clipCallback) {
            componentView.mClipCallback = clipCallback;
            return this;
        }

        public Builder setComponents(LinkedList<AVComponent> avComponents) {
            componentView.mAVComponents = avComponents;
            return this;
        }

        public ComponentView build() {
            componentView.setPadding(componentView.mPaddingLeftRight, componentView.mPaddingTopBottom,
                    componentView.mPaddingLeftRight, componentView.mPaddingTopBottom);
            componentView.setBackgroundColor(componentView.mPaddingColor);
            if (componentView.mAVComponents == null) return null;
            componentView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            componentView.buildViews();
            componentView.setStatus(false);
            componentView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    componentView.setStatus(true);
                }
            });
            return componentView;
        }

    }
}
