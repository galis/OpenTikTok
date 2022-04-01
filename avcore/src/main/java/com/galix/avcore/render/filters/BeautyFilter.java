package com.galix.avcore.render.filters;

import android.graphics.Bitmap;

import java.util.HashMap;
import java.util.Map;

/**
 * 美颜滤镜
 * 接收:
 * beauty_input:GLTexture
 * beauty_alpha:float
 * beauty_lut:Bitmap
 *
 * @Author: Galis
 * @Date:2022.03.31
 */
public class BeautyFilter extends BaseFilterGroup {

    private GLTexture mBeautyInput;
    private SkinFilter mSkinFilter = new SkinFilter();
    private LutFilter mWhiteFilter = new LutFilter();
    private Map<String, Object> mConfig = new HashMap<>();
    private Bitmap mLut;
    //参数
    private float mBeautyAlpha = 1.0f;//美颜程度

    public BeautyFilter() {
        addFilter(mSkinFilter);
        addFilter(mWhiteFilter);
    }

    @Override
    public void onWrite(Map<String, Object> config) {
        if (config.containsKey("beauty_alpha")) {
            mBeautyAlpha = (float) config.get("beauty_alpha");
        }
        if (config.containsKey("beauty_input")) {
            mBeautyInput = (GLTexture) config.get("beauty_input");
        }
        if (config.containsKey("beauty_lut")) {
            mLut = (Bitmap) config.get("beauty_lut");
        }
    }

    @Override
    public void onRender() {

        //磨皮
        mConfig.clear();
        mConfig.put("use_fbo", true);
        mConfig.put("fbo_size", mBeautyInput.size());
        mConfig.put("skin_input", mBeautyInput);
        mConfig.put("skin_alpha", mBeautyAlpha);
        mSkinFilter.write(mConfig);
        mSkinFilter.render();

        //美白
        mConfig.clear();
        mConfig.put("lut_input", mSkinFilter.getOutputTexture());
        mConfig.put("lut_src", mLut);
        mConfig.put("lut_alpha", mBeautyAlpha);
        mWhiteFilter.write(mConfig);
        mWhiteFilter.render();

    }


}
