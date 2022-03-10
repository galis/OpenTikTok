package com.galix.avcore.render;

import com.galix.avcore.avcore.AVFrame;

public class OESRender implements IRender {
    public static class OesRenderConfig {
        int width;
        int height;

        public OesRenderConfig(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private long mNativeObj = -1;

    public OESRender() {

    }

    @Override
    public void open() {
        if(mNativeObj!=-1) return;
        mNativeObj = nativeOpen();
    }

    @Override
    public void close() {
        if (mNativeObj == -1) {
            return;
        }
        nativeClose(mNativeObj);
        mNativeObj = -1;
    }

    @Override
    public void write(Object config) {
        if (config == null) return;
        nativeWrite(mNativeObj, ((OesRenderConfig) config).width, ((OesRenderConfig) config).height);
    }

    @Override
    public void render(AVFrame avFrame) {
        avFrame.getSurfaceTexture().updateTexImage();
        nativeRender(mNativeObj, avFrame.getTexture(), avFrame.getRoi().width(), avFrame.getRoi().height(),avFrame.getTextColor());
    }

    private native long nativeOpen();

    private native int nativeWrite(long nativeObj, int surfaceWidth, int surfaceHeight);

    private native int nativeRender(long nativeObj, int textureId, int textureWidth, int textureHeight,int color);

    private native int nativeClose(long nativeObj);
}
