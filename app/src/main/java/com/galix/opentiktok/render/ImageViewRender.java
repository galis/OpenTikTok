package com.galix.opentiktok.render;

import android.widget.ImageView;

import com.galix.opentiktok.avcore.AVFrame;

public class ImageViewRender implements IRender {

    private ImageView imageView;

    public ImageViewRender(ImageView imageView) {
        this.imageView = imageView;
    }

    @Override
    public void open() {

    }

    @Override
    public void close() {

    }

    @Override
    public void write(Object config) {

    }

    @Override
    public void render(AVFrame avFrame) {
        imageView.post(new Runnable() {
            @Override
            public void run() {
                imageView.setImageBitmap(avFrame.getBitmap());
            }
        });
    }
}
