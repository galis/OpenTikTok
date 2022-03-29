package com.galix.avcore.render.filters;

import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.opengl.GLUtils;

import com.galix.avcore.R;

import java.nio.IntBuffer;
import java.util.Map;

/**
 * LUT Filter
 *
 * 接收参数:
 * lut_input:GLTexture
 * lut_src:GLTexture
 * lut_alpha:float
 */
public class LutFilter extends BaseFilter {

    private final LutConfig mLutConfig = new LutConfig();

    public static class LutConfig {
        Bitmap lut;
        GLTexture lutTexture;
        GLTexture inputImage;
        float alpha = 0;
    }

    public LutFilter() {
        super(R.raw.lut_vs, R.raw.lut_fs);
    }

    @Override
    public void onRenderPre() {
        bindTexture("inputImageTexture", mLutConfig.inputImage);
        bindTexture("lutTexture", mLutConfig.lutTexture);
        bindFloat("alpha", mLutConfig.alpha);
    }

    @Override
    public void onWrite(Map<String, Object> config) {
        if (config.isEmpty()) return;
        for (String key : config.keySet()) {
            if (key.equalsIgnoreCase("lut_src")) {
                if (mLutConfig.lut != config.get("lut_src")) {
                    mLutConfig.lut = (Bitmap) config.get("lut_src");
                    if (mLutConfig.lutTexture == null) {
                        IntBuffer lutTextureIntBuf = IntBuffer.allocate(1);
                        GLES30.glGenTextures(lutTextureIntBuf.limit(), lutTextureIntBuf);
                        mLutConfig.lutTexture = new GLTexture(lutTextureIntBuf.get(), false);
                        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mLutConfig.lutTexture.id());
                        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, mLutConfig.lut, 0);
                    } else {
                        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mLutConfig.lutTexture.id());
                        GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, mLutConfig.lut);
                    }
                }
            } else if (key.equalsIgnoreCase("lut_input")) {
                mLutConfig.inputImage = (GLTexture) config.get("lut_input");
            } else if (key.equalsIgnoreCase("lut_alpha")) {
                mLutConfig.alpha = (float) config.get("lut_alpha");
            }
        }
    }
}
