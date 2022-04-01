package com.galix.avcore.render.filters;

import com.galix.avcore.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 磨皮滤镜
 *
 * @Author: Galis
 * @Date:2022.04.01
 */
public class SkinFilter extends BaseFilterGroup {

    private GaussFilter mSrcBlurFilter = new GaussFilter();
    private GaussFilter mDiffBlurFilter = new GaussFilter();
    private SkinFilter.DiffFilter mDiffFilter = new SkinFilter.DiffFilter();
    private SkinCompositeFilter mSkinCompositeFilter = new SkinCompositeFilter();
    private Map<String, Object> mConfig = new HashMap<>();
    //参数
    private GLTexture mSkinInput;
    private float mSkinAlpha = 1.0f;

    public SkinFilter() {
        addFilter(mSrcBlurFilter);
        addFilter(mDiffBlurFilter);
        addFilter(mDiffFilter);
        addFilter(mSkinCompositeFilter);
    }

    @Override
    public void onWrite(Map<String, Object> config) {
        if (config.containsKey("skin_input")) {
            mSkinInput = (GLTexture) config.get("skin_input");
        }
        if (config.containsKey("skin_alpha")) {
            mSkinAlpha = (float) config.get("skin_alpha");
        }
    }

    @Override
    public void onRender() {
        //原图模糊
        mConfig.clear();
        mConfig.put("gauss_input", mSkinInput);
        mSrcBlurFilter.write(mConfig);
        mSrcBlurFilter.render();

        //高反差diff
        mConfig.clear();
        mConfig.put("inputImageTexture", mSkinInput);
        mConfig.put("blurImageTexture", mSrcBlurFilter.getOutputTexture());
        mDiffFilter.write(mConfig);
        mDiffFilter.render();

        //高反差模糊diff_blur
        mConfig.clear();
        mConfig.put("gauss_input", mDiffFilter.getOutputTexture());
        mDiffBlurFilter.write(mConfig);
        mDiffBlurFilter.render();

        //磨皮合成
        mConfig.clear();
        mConfig.put("inputImageTexture", mSkinInput);
        mConfig.put("srcBlurImageTexture", mSrcBlurFilter.getOutputTexture());
        mConfig.put("diffBlurImageTexture", mDiffBlurFilter.getOutputTexture());
        mConfig.put("skin_alpha", mSkinAlpha);
        mSkinCompositeFilter.write(mConfig);
        mSkinCompositeFilter.render();
    }

    private static class DiffFilter extends BaseFilter {

        public DiffFilter() {
            super(R.raw.diff_vs, R.raw.diff_fs);
        }

        @Override
        public void onRenderPre() {
            bindTexture("inputImageTexture");
            bindTexture("blurImageTexture");
        }

        @Override
        public void onWrite(Map<String, Object> config) {
        }
    }

    private class SkinCompositeFilter extends BaseFilter {

        public SkinCompositeFilter() {
            super(R.raw.skin_vs, R.raw.skin_fs);
        }

        @Override
        public void onRenderPre() {
            bindTexture("inputImageTexture");
            bindTexture("srcBlurImageTexture");
            bindTexture("diffBlurImageTexture");
            bindFloat("skin_alpha");
        }

        @Override
        public void onWrite(Map<String, Object> config) {

        }
    }
}
