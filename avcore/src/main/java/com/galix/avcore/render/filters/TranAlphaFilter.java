package com.galix.avcore.render.filters;

import com.galix.avcore.R;

import java.util.Map;

public class TranAlphaFilter extends BaseFilter {
    public TranAlphaFilter() {
        super(R.raw.alpha_transaction_vs, R.raw.alpha_transaction_fs);
    }

    @Override
    public void onRenderPre() {
        bindTexture("video0");
        bindTexture("video1");
        bindFloat("alpha");
    }

    @Override
    public void onWrite(Map<String, Object> config) {

    }
}
