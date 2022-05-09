package com.galix.avcore.render;

import android.util.Size;

import com.galix.avcore.avcore.AVFrame;
import com.galix.avcore.render.filters.GLTexture;
import com.galix.avcore.render.filters.IFilter;
import com.galix.avcore.render.filters.TranAlphaFilter;

import java.util.HashMap;
import java.util.Map;

import static com.galix.avcore.avcore.AVTransaction.TRAN_ALPHA;

/**
 * 转场渲染器
 *
 * @Author: Galis
 * @Date:2022.03.22
 */
public class TransactionRender implements IVideoRender {

    private Map<Integer, IFilter> mFilters;
    private Map<String, Object> mConfig;

    @Override
    public boolean isOpen() {
        return mFilters != null;
    }

    @Override
    public void open() {
        if (isOpen()) return;
        if (mFilters == null) {
            mFilters = new HashMap<>();
        }
        if (mConfig == null) {
            mConfig = new HashMap<>();
        }
        mFilters.put(TRAN_ALPHA, new TranAlphaFilter());
        mFilters.get(TRAN_ALPHA).open();
    }

    @Override
    public void close() {
        if (!isOpen()) return;
        for (Integer key : mFilters.keySet()) {
            mFilters.get(key).close();
        }
        mFilters.clear();
    }

    @Override
    public void write(Map<String, Object> config) {
    }

    @Override
    public void render(AVFrame avFrame) {
        if (!isOpen()) return;
        mConfig.put("use_fbo", true);
        mConfig.put("fbo_size", avFrame.getTexture().size());
        mConfig.put("video0", avFrame.getTexture());
        mConfig.put("video1", avFrame.getTextureExt());
        mConfig.put("alpha", avFrame.getDelta());
        mFilters.get(TRAN_ALPHA).write(mConfig);
        mFilters.get(TRAN_ALPHA).render();
    }

    @Override
    public GLTexture getOutTexture() {
        return mFilters.get(TRAN_ALPHA).getOutputTexture();
    }
}
