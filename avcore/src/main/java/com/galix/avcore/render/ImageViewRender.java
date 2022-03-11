package com.galix.avcore.render;

import android.widget.ImageView;

import com.galix.avcore.avcore.AVFrame;

public class ImageViewRender implements IRender {

    private ImageView imageView;

    public ImageViewRender(ImageView imageView) {
        this.imageView = imageView;
    }

    @Override
    public boolean isOpen() {
        return false;
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
                if (avFrame.isValid()) {
                    imageView.setImageBitmap(avFrame.getBitmap());
                } else {
                    imageView.setImageBitmap(null);
                }
            }
        });
    }
}
