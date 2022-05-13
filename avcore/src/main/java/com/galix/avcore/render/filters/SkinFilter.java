package com.galix.avcore.render.filters;

import com.galix.avcore.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 磨皮滤镜
 * USE_FBO:boolean
 * FBO_SIZE:Size
 * INPUT_IMAGE:GLTexture
 * SKIN_ALPHA:float
 *
 * @Author: Galis
 * @Date:2022.04.01
 */
public class SkinFilter extends BaseFilterGroup {

    public static final String INPUT_IMAGE = "skin_input";
    public static final String SKIN_ALPHA = "skin_alpha";

    private Map<String, Object> mTempConfig = new HashMap<>();

    public SkinFilter() {
        addFilter("srcBlur", GaussFilter.class);
        addFilter("diffBlur", GaussFilter.class);
        addFilter("diff", DiffFilter.class);
        addFilter("skinComposite", SkinCompositeFilter.class);
    }


    @Override
    public void onRender() {
        //原图模糊
        mTempConfig.clear();
        mTempConfig.put(GaussFilter.USE_FBO, true);
        mTempConfig.put(GaussFilter.FBO_SIZE, getConfig(SkinFilter.FBO_SIZE));
        mTempConfig.put(GaussFilter.INPUT_IMAGE, getConfig(SkinFilter.INPUT_IMAGE));
        getFilter("srcBlur").write(mTempConfig);
        getFilter("srcBlur").render();

        //高反差diff
        mTempConfig.clear();
        mTempConfig.put(DiffFilter.USE_FBO, true);
        mTempConfig.put(DiffFilter.FBO_SIZE, getConfig(SkinFilter.FBO_SIZE));
        mTempConfig.put(DiffFilter.INPUT_IMAGE, getConfig(SkinFilter.INPUT_IMAGE));
        mTempConfig.put(DiffFilter.BLUR_IMAGE, getFilter("srcBlur").getOutputTexture());
        getFilter("diff").write(mTempConfig);
        getFilter("diff").render();
        //高反差模糊diff_blur
        mTempConfig.clear();
        mTempConfig.put(GaussFilter.USE_FBO, true);
        mTempConfig.put(GaussFilter.FBO_SIZE, getConfig(SkinFilter.FBO_SIZE));
        mTempConfig.put(GaussFilter.INPUT_IMAGE, getFilter("diff").getOutputTexture());
        getFilter("diffBlur").write(mTempConfig);
        getFilter("diffBlur").render();
        //磨皮合成
        mTempConfig.clear();
        mTempConfig.put(SkinCompositeFilter.USE_FBO, true);
        mTempConfig.put(SkinCompositeFilter.FBO_SIZE, getConfig(SkinFilter.FBO_SIZE));
        mTempConfig.put(SkinCompositeFilter.INPUT_IMAGE, getConfig(SkinFilter.INPUT_IMAGE));
        mTempConfig.put(SkinCompositeFilter.SRC_BLUR_IMAGE, getFilter("srcBlur").getOutputTexture());
        mTempConfig.put(SkinCompositeFilter.DIFF_BLUR_IMAGE, getFilter("diffBlur").getOutputTexture());
        mTempConfig.put(SkinCompositeFilter.SKIN_ALPHA, getConfig(SkinFilter.SKIN_ALPHA));
        getFilter("skinComposite").write(mTempConfig);
        getFilter("skinComposite").render();
    }

    /**
     * 接收参数：
     * USE_FBO:Boolean
     * FBO_SIZE:Size
     * INPUT_IMAGE:GLTexture
     * BLUR_IMAGE:GLTexture
     */
    private static class DiffFilter extends BaseFilter {

        public static final String BLUR_IMAGE = "blurImageTexture";

        public DiffFilter() {
            super(R.raw.diff_vs, R.raw.diff_fs);
        }

        @Override
        public void onRenderPre() {
            bindTexture(DiffFilter.INPUT_IMAGE);
            bindTexture(DiffFilter.BLUR_IMAGE);
        }
    }

    /**
     * 接收参数：
     * USE_FBO:Boolean
     * FBO_SIZE:Size
     * INPUT_IMAGE:GLTexture
     * SRC_BLUR_IMAGE:GLTexture
     * DIFF_BLUR_IMAGE:GLTexture
     * SKIN_ALPHA:float
     */
    private static class SkinCompositeFilter extends BaseFilter {

        public static final String SRC_BLUR_IMAGE = "srcBlurImageTexture";
        public static final String DIFF_BLUR_IMAGE = "diffBlurImageTexture";
        public static final String SKIN_ALPHA = "skin_alpha";

        public SkinCompositeFilter() {
            super(R.raw.skin_vs, R.raw.skin_fs);
        }

        @Override
        public void onRenderPre() {
            bindTexture(SkinCompositeFilter.INPUT_IMAGE);
            bindTexture(SkinCompositeFilter.SRC_BLUR_IMAGE);
            bindTexture(SkinCompositeFilter.DIFF_BLUR_IMAGE);
            bindFloat(SkinCompositeFilter.SKIN_ALPHA);
        }
    }
}
