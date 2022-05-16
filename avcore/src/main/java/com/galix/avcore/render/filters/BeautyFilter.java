package com.galix.avcore.render.filters;

import java.util.HashMap;
import java.util.Map;

/**
 * 美颜滤镜
 * 接收:
 * INPUT_IMAGE:GLTexture
 * BEAUTY_ALPHA:float
 * BEAUTY_LUT:Bitmap
 *
 * @Author: Galis
 * @Date:2022.03.31
 */
public class BeautyFilter extends BaseFilterGroup {

    public static final String INPUT_IMAGE = "beauty_input";
    public static final String BEAUTY_ALPHA = "beauty_alpha";
    public static final String BEAUTY_LUT = "beauty_lut";

    private Map<String, Object> mConfig = new HashMap<>();

    public BeautyFilter() {
        addFilter(SkinFilter.class);
//        addFilter(LutFilter.class);
    }

    @Override
    public void onRender() {

        //磨皮
        mConfig.clear();
        mConfig.put(SkinFilter.USE_FBO, true);
        mConfig.put(SkinFilter.FBO_SIZE, getConfig(FBO_SIZE));
        mConfig.put(SkinFilter.INPUT_IMAGE, getConfig(INPUT_IMAGE));
        mConfig.put(SkinFilter.SKIN_ALPHA, getConfig(BEAUTY_ALPHA));
        getFilter(SkinFilter.class).write(mConfig);
        getFilter(SkinFilter.class).render();

//        //美白
//        mConfig.clear();
//        mConfig.put(LutFilter.USE_FBO, true);
//        mConfig.put(LutFilter.FBO_SIZE, getConfig(FBO_SIZE));
//        mConfig.put(LutFilter.INPUT_IMAGE, getFilter(SkinFilter.class).getOutputTexture());
//        mConfig.put(LutFilter.LUT_BITMAP, getConfig(BEAUTY_LUT));
//        mConfig.put(LutFilter.LUT_ALPHA, getConfig(BEAUTY_ALPHA));
//        getFilter(LutFilter.class).write(mConfig);
//        getFilter(LutFilter.class).render();

    }

}
