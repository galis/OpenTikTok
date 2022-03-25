package com.galix.avcore.render;

import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.opengl.GLUtils;

import com.galix.avcore.avcore.AVFrame;

import java.nio.IntBuffer;

/**
 * LUT render
 */
public class LutRender extends GLRender {

    private IntBuffer mLutTextureId;
    private final LutConfig mLutConfig = new LutConfig();

    public static class LutConfig {
        Bitmap lut;
        float alpha = 0;
    }

    public LutRender(String vs, String fs) {
        super(vs, fs);
    }

    @Override
    public void onRenderPre(AVFrame avFrame) {
        bindTexture("inputImageTexture", avFrame.getTexture(), false);
        bindTexture("lutTexture", mLutTextureId.get(), false);
        bindFloat("alpha", mLutConfig.alpha);
    }

    @Override
    public void onWrite(Object config) {
        if (config instanceof LutConfig) {
            if (mLutConfig.lut != ((LutConfig) config).lut) {
                setTask(() -> {
                    if (mLutTextureId == null) {
                        mLutTextureId = IntBuffer.allocate(1);
                        GLES30.glGenTextures(mLutTextureId.limit(), mLutTextureId);
                        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mLutTextureId.get());
                        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, mLutConfig.lut, 0);
                    } else {
                        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mLutTextureId.get());
                        GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, mLutConfig.lut);
                    }
                });
            }
            mLutConfig.alpha = ((LutConfig) config).alpha;
        }
    }
}
