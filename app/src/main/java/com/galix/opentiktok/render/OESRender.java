package com.galix.opentiktok.render;

import com.galix.opentiktok.ARContext;
import com.galix.opentiktok.avcore.AVFrame;

public class OESRender implements IRender {
    public static class OesRenderConfig {
        int width;
        int height;

        public OesRenderConfig(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private ARContext mARContext;
    private OesRenderConfig mOESRenderConfig;

    public OESRender() {

    }

    @Override
    public void open() {
        mARContext = new ARContext();
        mARContext.create();
    }

    @Override
    public void close() {
        mARContext.destroy();
    }

    @Override
    public void write(Object config) {
        if (config == null) return;
        mOESRenderConfig = (OesRenderConfig) config;
        mARContext.onSurfaceChanged(mOESRenderConfig.width, mOESRenderConfig.height);
    }

    @Override
    public void render(AVFrame avFrame) {
        avFrame.getSurfaceTexture().updateTexImage();
        mARContext.draw(avFrame.getTexture());
    }
}
