package com.galix.avcore.render;

import com.galix.avcore.avcore.AVFrame;
import com.galix.avcore.render.filters.GLTexture;
import com.galix.avcore.render.filters.OesFilter;

import java.util.HashMap;
import java.util.Map;

public class OESRender implements IVideoRender {

    private OesFilter mOesFilter;
    private Map<String, Object> mConfigs = new HashMap<>();

    @Override
    public GLTexture getOutTexture() {
        return mOesFilter.getOutputTexture();
    }

    @Override
    public boolean isOpen() {
        return mOesFilter != null;
    }

    @Override
    public void open() {
        if (isOpen()) return;
        mOesFilter = new OesFilter();
        mOesFilter.open();
    }

    @Override
    public void close() {
        if (!isOpen()) return;
        mOesFilter.close();
        mOesFilter = null;
    }

    @Override
    public void write(Map<String, Object> config) {

    }

    @Override
    public void render(AVFrame avFrame) {
        mConfigs.clear();
        mConfigs.put("use_fbo", true);
        mConfigs.put("fbo_size", avFrame.getTexture().size());
        mConfigs.put("inputImageTexture", avFrame.getTexture());
        mOesFilter.write(mConfigs);
        mOesFilter.render();
    }
}
