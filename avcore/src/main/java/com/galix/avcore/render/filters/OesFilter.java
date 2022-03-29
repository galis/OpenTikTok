package com.galix.avcore.render.filters;

import com.galix.avcore.R;

import java.util.Map;

public class OesFilter extends BaseFilter {

    private GLTexture mInputImage;

    public OesFilter() {
        super(R.raw.oes_vs, R.raw.oes_fs);
    }

    @Override
    public void onRenderPre() {
        bindTexture("inputImageTexture", mInputImage);
    }

    @Override
    public void onWrite(Map<String, Object> config) {
        if (config.containsKey("oes_input")) {
            mInputImage = (GLTexture) config.get("oes_input");
        }
    }
}
