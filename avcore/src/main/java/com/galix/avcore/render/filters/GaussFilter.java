package com.galix.avcore.render.filters;

import android.util.Size;
import android.util.SizeF;

import com.galix.avcore.R;

import java.util.HashMap;
import java.util.Map;

import static com.galix.avcore.render.filters.BaseFilter.INPUT_IMAGE;

/**
 * 高斯模糊滤镜,默认启动fbo,接收一下参数
 * USE_FBO:Boolean
 * FBO_SIZE:Size
 * INPUT_IMAGE:GLTexture
 *
 * @Author: Galis
 * @Date:2022.04.01
 */
public class GaussFilter extends BaseFilterGroup {

    public static final String INPUT_IMAGE = "gauss_input";
    private static final float SKIN_RADIUS = 0.1f;//磨皮范围
    private Map<String, Object> mConfig = new HashMap<>();
    private SizeF mOffset1;
    private SizeF mOffset2;

    public GaussFilter() {
        addFilter("childX", ChildFilter.class);
        addFilter("childY", ChildFilter.class);
    }


    @Override
    public void onRender() {

        Size fboSize = (Size) getConfig().get(BaseFilter.FBO_SIZE);
        GLTexture gaussInput = (GLTexture) getConfig().get(INPUT_IMAGE);

        if (mOffset1 == null) {
            mOffset1 = new SizeF(SKIN_RADIUS /   fboSize.getWidth(), 0);
            mOffset2 = new SizeF(0, SKIN_RADIUS / fboSize.getHeight());
        }

        //x方向滤波
        mConfig.clear();
        mConfig.put(USE_FBO, true);
        mConfig.put(FBO_SIZE, fboSize);
        mConfig.put(ChildFilter.INPUT_IMAGE, gaussInput);
        mConfig.put(ChildFilter.TEX_WIDTH_OFFSET, mOffset1.getWidth());
        mConfig.put(ChildFilter.TEX_HEIGHT_OFFSET, mOffset1.getHeight());
        getFilter("childX").write(mConfig);
        getFilter("childX").render();

        //y方向滤波
        mConfig.clear();
        mConfig.put(USE_FBO, true);
        mConfig.put(FBO_SIZE, fboSize);
        mConfig.put(ChildFilter.INPUT_IMAGE, getFilter("childX").getOutputTexture());
        mConfig.put(ChildFilter.TEX_WIDTH_OFFSET, mOffset2.getWidth());
        mConfig.put(ChildFilter.TEX_HEIGHT_OFFSET, mOffset2.getHeight());
        getFilter("childY").write(mConfig);
        getFilter("childY").render();
    }

    private static class ChildFilter extends BaseFilter {

        public static final String TEX_WIDTH_OFFSET = "texelWidthOffset";
        public static final String TEX_HEIGHT_OFFSET = "texelHeightOffset";

        public ChildFilter() {
            super(R.raw.gauss_vs, R.raw.gauss_fs);
        }

        @Override
        public void onRenderPre() {
            bindTexture(INPUT_IMAGE);
            bindFloat(TEX_WIDTH_OFFSET);
            bindFloat(TEX_HEIGHT_OFFSET);
        }

    }
}
