package com.galix.avcore.render.filters;

import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.util.Size;

import org.opencv.core.Mat;

import java.nio.IntBuffer;

public class GLTexture {
    private IntBuffer textureIdBuf;
    private boolean oes = false;
    private Size mSize = new Size(0, 0);
    private String path;
    private Bitmap bitmap;
    private Object dirty;
    private Mat matrix;

    public GLTexture() {
    }

    public GLTexture(int textureId, boolean oes) {
        if (textureIdBuf == null) {
            textureIdBuf = IntBuffer.allocate(1);
        }
        textureIdBuf.position(0);
        textureIdBuf.put(textureId);
        textureIdBuf.position(0);
        this.oes = oes;
    }

    public int id() {
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

    public void release() {
        if (id() != 0) {
            GLES30.glDeleteTextures(1, idAsBuf());
            idAsBuf().put(0);
        }
        setSize(0, 0);
    }

}
