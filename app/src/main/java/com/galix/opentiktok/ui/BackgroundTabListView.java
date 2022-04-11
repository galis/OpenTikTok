package com.galix.opentiktok.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.galix.opentiktok.R;

/**
 * 背景颜色列表View
 *
 * @Author: galis
 */
public class BackgroundTabListView extends LinearLayout {

    private final int TEXT_SIZE = 16;
    private final int TEXT_COLOR = Color.WHITE;
    private final int TARGET_SIZE = R.dimen.ratio_height;
    private final int BACK_COLOR = R.color.theme_black48;
    private OnClickListener mCallback;

    public static class Background {
        public int color;
        public int width;
        public int height;

        public Background(int color, int width, int height) {
            this.color = color;
            this.width = width;
            this.height = height;
        }
    }

    public BackgroundTabListView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);


    }

    private View genTextView(Background ratioInfo) {
        View textView = new View(getContext());
        textView.setBackgroundColor(ratioInfo.color);
        LayoutParams layoutParams = new LayoutParams(ratioInfo.width, ratioInfo.height);
        layoutParams.setMargins(10, 0, 10, 0);
        layoutParams.gravity = Gravity.CENTER_VERTICAL;
        textView.setLayoutParams(layoutParams);
        textView.setTag(ratioInfo.color);
        textView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCallback != null) mCallback.onClick(v);
            }
        });
        return textView;
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

    public void buildView(OnClickListener onClickListener) {
        mCallback = onClickListener;
        removeAllViews();
        int targetSize = getResources().getDimensionPixelSize(TARGET_SIZE);
        addView(genBackView(targetSize));
        addView(genTextView(new Background(Color.RED, targetSize, targetSize)));
        addView(genTextView(new Background(Color.YELLOW, targetSize, targetSize)));
        addView(genTextView(new Background(Color.BLUE, targetSize, targetSize)));
    }

}
