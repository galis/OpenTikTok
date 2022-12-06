package com.galix.avcore.render.filters;

import com.galix.avcore.R;

/**
 * 屏幕渲染
 */
public class TextureFilter extends BaseFilter {

    public TextureFilter() {
        super(R.raw.texture_vs, R.raw.texture_fs);
    }

    @Override
    public void onRenderPre() {
        bindTexture("inputImageTexture");
        bindTexture("oesImageTexture");
        bindBool("isOes");
        bindMat3("textureMat");
        bindVec4("bgColor");
        bindBool("isFlipVertical");
        bindBool("isHalfAlpha");
    }

}
