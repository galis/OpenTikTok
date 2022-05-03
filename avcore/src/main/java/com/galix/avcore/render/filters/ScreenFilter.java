package com.galix.avcore.render.filters;

import com.galix.avcore.R;

import java.util.Map;

/**
 * 屏幕渲染
 */
public class ScreenFilter extends BaseFilter {

    public ScreenFilter() {
        super(R.raw.screenvs, R.raw.screenfs);
    }

    @Override
    public void onRenderPre() {
        bindTexture("inputImageTexture");
        bindTexture("oesImageTexture");
        bindBool("isOes");
    }


    @Override
    public void onWrite(Map<String, Object> config) {
    }
}
