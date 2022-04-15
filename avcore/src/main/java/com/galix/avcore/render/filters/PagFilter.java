package com.galix.avcore.render.filters;

import com.galix.avcore.R;

import java.util.Map;

public class PagFilter extends BaseFilter {
    public PagFilter() {
        super(R.raw.pag_vs, R.raw.pag_fs);
    }

    @Override
    public void onRenderPre() {
        bindTexture("inputImageTexture");
        bindTexture("pagTexture");
        bindMat3("pagMat");
    }

    @Override
    public void onWrite(Map<String, Object> config) {

    }
}
