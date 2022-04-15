package com.galix.avcore.render.filters;

import com.galix.avcore.R;

import java.util.Map;

public class OesFilter extends BaseFilter {


    public OesFilter() {
        super(R.raw.oes_vs, R.raw.oes_fs);
    }

    @Override
    public void onRenderPre() {
        bindTexture("inputImageTexture");
    }

    @Override
    public void onWrite(Map<String, Object> config) {
    }
}
