package com.galix.avcore.render.filters;

import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.opengl.GLUtils;

import com.galix.avcore.R;
import com.galix.avcore.util.GLUtil;

import java.nio.IntBuffer;
import java.util.Map;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.glTexParameterf;
import static android.opengl.GLES20.glTexParameteri;

/**
 * LUT Filter
 * 接收参数:
 * USE_FBO:Boolean
 * FBO_SIZE:Size
 * INPUT_IMAGE:GLTexture
 * LUT_ALPHA:float
 * LUT_BITMAP:Bitmap
 */
public class LutFilter extends BaseFilter {

    public static final String LUT_BITMAP = "lut_bitmap";
    public static final String LUT_ALPHA = "lut_alpha";
    private static final String IS_OES = "isOes";
    private static final String LUT_TEXTURE = "lutTexture";
    private GLTexture mLutTexture = new GLTexture();

    public LutFilter() {
        super(R.raw.lut_vs, R.raw.lut_fs);
    }

    @Override
    public void onRenderPre() {
        GLTexture inputImage = (GLTexture) getConfig(LutFilter.INPUT_IMAGE);
        bindBool(IS_OES, inputImage.isOes());
        bindFloat(LUT_ALPHA);
        bindTexture(LUT_TEXTURE, mLutTexture);
        if (inputImage.isOes()) {
            bindTexture(LutFilter.INPUT_IMAGE_OES, inputImage);
            bindTexture(LutFilter.INPUT_IMAGE, GLUtil.DEFAULT_TEXTURE);
        } else {
            bindTexture(LutFilter.INPUT_IMAGE_OES, GLUtil.DEFAULT_OES_TEXTURE);
            bindTexture(LutFilter.INPUT_IMAGE, inputImage);
        }
    }

    @Override
    public void onWrite(Map<String, Object> config) {
        if (config.containsKey(LUT_BITMAP) && getConfig(LUT_BITMAP) != config.get(LUT_BITMAP)) {
            if (mLutTexture.id() == 0) {
                GLES30.glGenTextures(1, mLutTexture.idAsBuf());
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mLutTexture.id());
                glTexParameterf(GLES30.GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameterf(GLES30.GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GLES30.GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GLES30.GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, (Bitmap) config.get(LUT_BITMAP), 0);
            } else {
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mLutTexture.id());
                glTexParameterf(GLES30.GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameterf(GLES30.GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GLES30.GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GLES30.GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, (Bitmap) config.get(LUT_BITMAP));
            }
        }
    }
}
