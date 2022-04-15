package com.galix.avcore.render.filters;

import android.util.Size;
import android.util.SizeF;

import com.galix.avcore.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 高斯模糊滤镜,默认启动fbo,接收一下参数
 * gauss_input:GLTexture
 * fbo_size:Size
 *
 * @Author: Galis
 * @Date:2022.04.01
 */
public class GaussFilter extends BaseFilterGroup {

    private static final float SKIN_RADIUS = 4.5f;//磨皮范围
    private Map<String, Object> mConfig = new HashMap<>();
    private ChildFilter mChildFilterX;
    private ChildFilter mChildFilterY;
    private SizeF mOffset1;
    private SizeF mOffset2;
    private GLTexture mGaussInput;
    private Size mFboSize;

    public GaussFilter() {
        mChildFilterX = new ChildFilter();
        mChildFilterY = new ChildFilter();
        addFilter(mChildFilterX);
        addFilter(mChildFilterY);
    }

    @Override
    public void onWrite(Map<String, Object> config) {
        if (config.containsKey("gauss_input")) {
            mGaussInput = (GLTexture) config.get("gauss_input");
        }
        if (config.containsKey("fbo_size")) {
            mFboSize = (Size) config.get("fbo_size");
        }
    }

    @Override
    public void onRender() {

        if (mFboSize == null) {
            mFboSize = mGaussInput.size();
        }
        if (mOffset1 == null) {
            mOffset1 = new SizeF(SKIN_RADIUS * (mFboSize.getWidth() / 1920.f) / mFboSize.getWidth(), 0);
            mOffset2 = new SizeF(0, SKIN_RADIUS * (mFboSize.getHeight() / 1080.f) / mFboSize.getHeight());
        }

        //x方向滤波
        mConfig.clear();
        mConfig.put("inputImageTexture", mGaussInput);
        mConfig.put("texelWidthOffset", mOffset1.getWidth());
        mConfig.put("texelHeightOffset", mOffset1.getHeight());
        mChildFilterX.write(mConfig);
        mChildFilterX.render();

        //y方向滤波
        mConfig.clear();
        mConfig.put("inputImageTexture", mChildFilterX.getOutputTexture());
        mConfig.put("texelWidthOffset", mOffset2.getWidth());
        mConfig.put("texelHeightOffset", mOffset2.getHeight());
        mChildFilterY.write(mConfig);
        mChildFilterY.render();
    }

    @Override
    public void write(Object... config) {

    }

    private static class ChildFilter extends BaseFilter {

        public ChildFilter() {
            super(R.raw.gauss_vs, R.raw.gauss_fs);
        }

        @Override
        public void onRenderPre() {
            bindFloat("texelWidthOffset");
            bindFloat("texelHeightOffset");
            bindTexture("inputImageTexture");
        }

        @Override
        public void onWrite(Map<String, Object> config) {
        }
    }
}
