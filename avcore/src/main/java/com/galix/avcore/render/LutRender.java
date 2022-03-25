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

    private Bitmap mLut;
    private IntBuffer mLutTextureId;

    public static class LutConfig {
        Bitmap lut;
    }

    public LutRender(String vs, String fs) {
        super(vs, fs);
    }

    @Override
    public void onRender(AVFrame avFrame) {
        bindTexture("inputImageTexture", avFrame.getTexture(), true);
        bindTexture("lutTexture", avFrame.getTextureExt(), true);
        bindFloat("alpha", avFrame.getDelta());
    }

    @Override
    public void onWrite(Object config) {
        if (config instanceof LutConfig) {
            mLut = ((LutConfig) config).lut;
            setTask(() -> {
                if (mLutTextureId == null) {
                    mLutTextureId = IntBuffer.allocate(1);
                    GLES30.glGenTextures(mLutTextureId.limit(), mLutTextureId);
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mLutTextureId.get());
                    GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, mLut, 0);
                } else {
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mLutTextureId.get());
                    GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, mLut);
                }
            });
        }
    }
}
