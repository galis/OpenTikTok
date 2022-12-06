package com.galix.avcore.render.filters;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.util.Size;

import com.galix.avcore.gl.ResourceManager;
import com.galix.avcore.util.FileUtils;
import com.galix.avcore.util.GLUtil;

import org.opencv.core.Mat;

import java.nio.IntBuffer;

import static org.opencv.core.CvType.CV_32F;

public class GLTexture {

    static {
        System.loadLibrary("opencv_java3");
    }

    public static final GLTexture GL_EMPTY_TEXTURE = new GLTexture();
    private static final Mat mIdentityMat = Mat.eye(3, 3, CV_32F);
    private IntBuffer textureIdBuf = IntBuffer.allocate(1);
    private boolean oes = false;
    private boolean mute;//mute 无论如何都是0
    private Size mSize = new Size(0, 0);
    private Object mData;
    private Mat matrix = mIdentityMat;

    public GLTexture() {
        textureIdBuf.position(0);
        textureIdBuf.put(0);
        textureIdBuf.position(0);
        oes = false;
        mData = null;
    }

    public GLTexture(int textureId, boolean oes) {
        textureIdBuf.position(0);
        textureIdBuf.put(textureId);
        textureIdBuf.position(0);
        this.oes = oes;
        mData = null;
    }

    public GLTexture generateMipmap() {
        GLUtil.checkGlError("glGenerateMipmap pre");
//        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id());
        GLES30.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES30.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAX_LEVEL, 4);
        GLES30.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        GLUtil.checkGlError("glGenerateMipmap");
        return this;
    }

    public int id() {
        if (isMute()) {
            return 0;
        }
        textureIdBuf.position(0);
        return textureIdBuf.get();
    }

    public IntBuffer idAsBuf() {
        textureIdBuf.position(0);
        return textureIdBuf;
    }

    public void setOes(boolean oes) {
        this.oes = oes;
    }

    public boolean isOes() {
        return oes;
    }

    public Size size() {
        return mSize;
    }

    public void setSize(int w, int h) {
        mSize = new Size(w, h);
    }

    public Mat getMatrix() {
        return matrix;
    }

    public void setMatrix(Mat matrix) {
        this.matrix = matrix;
    }

    public boolean isMute() {
        return mute;
    }

    public void setMute(boolean mute) {
        this.mute = mute;
    }

    public void release() {
        if (id() != 0) {
            GLES30.glDeleteTextures(1, idAsBuf());
            idAsBuf().put(0);
        }
        setSize(0, 0);
    }

    public Bitmap asBitmap() {
        return GLUtil.dumpTexture(this);
    }

    public void save() {
        FileUtils.Save(ResourceManager.getManager().getCacheDir() + "/" + "dump.png", asBitmap());
    }

    public void save(String path) {
        FileUtils.Save(path, asBitmap());
    }

    public Object data() {
        return mData;
    }

    public void setData(Object obj) {
        mData = obj;
    }

}
