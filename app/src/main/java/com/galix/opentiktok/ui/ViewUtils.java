package com.galix.opentiktok.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.libpag.PAGView;

import static android.widget.RelativeLayout.CENTER_IN_PARENT;

public class ViewUtils {
    public static ImageView createImageView(Context context) {
        ImageView imageView = new ImageView(context);
        RelativeLayout.LayoutParams layoutParams =
                new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.addRule(CENTER_IN_PARENT);
        imageView.setLayoutParams(layoutParams);
        return imageView;
    }

    public static PAGView createPagView(Context context) {
        PAGView pagView = new PAGView(context);
        pagView.setBackgroundColor(Color.RED);
        RelativeLayout.LayoutParams layoutParams =
                new RelativeLayout.LayoutParams(1280, 720);
        layoutParams.addRule(CENTER_IN_PARENT);
        pagView.setLayoutParams(layoutParams);
        return pagView;
    }

    public static EditText createEditText(Context context) {
        EditText editText = new EditText(context);
        RelativeLayout.LayoutParams layoutParams =
                new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.addRule(CENTER_IN_PARENT);
        editText.setPadding(10, 10, 10, 10);
        editText.setTextColor(Color.RED);
        editText.setLayoutParams(layoutParams);
        editText.setText("OpenTitok");
        return editText;
    }
}
