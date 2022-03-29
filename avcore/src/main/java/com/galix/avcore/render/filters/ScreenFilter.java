package com.galix.avcore.render.filters;

import com.galix.avcore.R;

import java.util.Map;

/**
 * 屏幕渲染
 */
public class ScreenFilter extends BaseFilter {
    private GLTexture inputTexture;

    public ScreenFilter() {
        super(R.raw.screenvs, R.raw.screenfs);
    }

    @Override
    public void onRenderPre() {
        bindTexture("inputImageTexture", inputTexture);
    }


    @Override
    public void onWrite(Map<String, Object> config) {
        if (config.containsKey("screen_input")) {
            inputTexture = (GLTexture) config.get("screen_input");
        }
    }
}
