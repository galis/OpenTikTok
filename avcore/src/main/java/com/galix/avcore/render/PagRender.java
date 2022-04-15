package com.galix.avcore.render;

import android.util.Size;

import com.galix.avcore.avcore.AVFrame;
import com.galix.avcore.render.filters.GLTexture;
import com.galix.avcore.render.filters.PagFilter;

import java.util.HashMap;
import java.util.Map;

public class PagRender implements IVideoRender {

    private PagFilter mPagFilter;
    private GLTexture mLastTexture;
    private Map<String, Object> mConfig = new HashMap<>();
    private Size mSurfaceSize = new Size(1920, 1080);

    @Override
    public GLTexture getOutTexture() {
        return mPagFilter.getOutputTexture();
    }

    @Override
    public boolean isOpen() {
        return mPagFilter != null;
    }

    @Override
    public void open() {
        if (mPagFilter != null) return;
        mPagFilter = new PagFilter();
        mPagFilter.open();
    }

    @Override
    public void close() {
        if (mPagFilter != null) {
            mPagFilter.close();
        }
    }

    @Override
    public void write(Map<String, Object> config) {
//        mConfig.clear();
//        mConfig.putAll(config);
    }

    @Override
    public void render(AVFrame avFrame) {
        GLTexture lastTexture = avFrame.getTextureExt();
        mConfig.clear();
        mConfig.put("use_fbo", false);
//        mConfig.put("fbo_size", lastTexture.size());
//        mConfig.put("inputImageTexture", lastTexture);
        mConfig.put("pagTexture", avFrame.getTexture());
        mConfig.put("pagMat", avFrame.getTexture().getMatrix());
        mPagFilter.write(mConfig);
        mPagFilter.render();
    }
}
