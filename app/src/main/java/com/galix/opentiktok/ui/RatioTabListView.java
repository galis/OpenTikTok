package com.galix.opentiktok.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.galix.opentiktok.R;

/**
 * 底部比例列表View
 *
 * @Author: galis
 */
public class RatioTabListView extends LinearLayout {

    private final int TEXT_SIZE = 16;
    private final int TEXT_COLOR = Color.WHITE;
    private final int TARGET_SIZE = R.dimen.ratio_height;
    private final int BACK_COLOR = R.color.theme_black48;
    private OnClickListener mCallback;

    public static class RatioInfo {
        public String text;
        public int textColor;
        public int textSize;
        public int width;
        public int height;

        public RatioInfo(String text, int textColor, int textSize, int width, int height) {
            this.text = text;
            this.textColor = textColor;
            this.textSize = textSize;
            this.width = width;
            this.height = height;
        }
    }

    public RatioTabListView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    private View genBackView(int targetSize) {
        ImageView imageView = new ImageView(getContext());
        imageView.setBackgroundColor(getResources().getColor(BACK_COLOR));
        LayoutParams layoutParams = new LayoutParams((int) (targetSize * 9.f / 16.f), targetSize);
        layoutParams.setMargins(10, 0, 10, 0);
        layoutParams.gravity = Gravity.CENTER_VERTICAL;
        imageView.setLayoutParams(layoutParams);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(R.drawable.icon_back);
        imageView.setTag(null);
        imageView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCallback != null) mCallback.onClick(v);
            }
        });
        return imageView;
    }

    private TextView genTextView(RatioInfo ratioInfo) {
        TextView textView = new TextView(getContext());
        textView.setText(ratioInfo.text);
        textView.setTextColor(ratioInfo.textColor);
        textView.setTextSize(ratioInfo.textSize);
        textView.setGravity(Gravity.CENTER);
        textView.setBackgroundColor(getResources().getColor(BACK_COLOR));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ratioInfo.width, ratioInfo.height);
        layoutParams.setMargins(10, 0, 10, 0);
        layoutParams.gravity = Gravity.CENTER_VERTICAL;
        textView.setLayoutParams(layoutParams);
        textView.setTag(ratioInfo);
        textView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCallback != null) mCallback.onClick(v);
            }
        });
        return textView;
    }

    /**
     * 目标视频宽高
     *
     * @param width
     * @param height
     */
    public void buildView(int width, int height, OnClickListener onClickListener) {
        mCallback = onClickListener;
        removeAllViews();
        int targetSize = getResources().getDimensionPixelSize(TARGET_SIZE);
        addView(genBackView(targetSize));
        if (width > height) {
            addView(genTextView(new RatioInfo("原始", TEXT_COLOR, TEXT_SIZE, targetSize, (int) (targetSize * (height * 1.0f / width)))));
        } else {
            addView(genTextView(new RatioInfo("原始", TEXT_COLOR, TEXT_SIZE, (int) (targetSize * (height * 1.0f / width)), targetSize)));
        }
        addView(genTextView(new RatioInfo("4:3", TEXT_COLOR, TEXT_SIZE, targetSize, (int) (targetSize * (3.f / 4.f)))));
        addView(genTextView(new RatioInfo("3:4", TEXT_COLOR, TEXT_SIZE, (int) (targetSize * (3.f / 4.f)), targetSize)));
        addView(genTextView(new RatioInfo("1:1", TEXT_COLOR, TEXT_SIZE, targetSize, targetSize)));
        addView(genTextView(new RatioInfo("16:9", TEXT_COLOR, TEXT_SIZE, targetSize, (int) (targetSize * (9.f / 16.f)))));
        addView(genTextView(new RatioInfo("9:16", TEXT_COLOR, TEXT_SIZE, (int) (targetSize * (9.f / 16.f)), targetSize)));
    }

}
