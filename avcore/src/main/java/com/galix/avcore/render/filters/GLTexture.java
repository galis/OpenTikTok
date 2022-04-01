package com.galix.avcore.render.filters;

import android.graphics.Bitmap;
import android.util.Size;

import java.nio.IntBuffer;

public class GLTexture {
    private IntBuffer textureIdBuf;
    private boolean oes = false;
    private Size mSize = new Size(0, 0);
    private String path;
    private Bitmap bitmap;
    private Object dirty;

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

    public boolean isOes() {
        return oes;
    }

    public Size size() {
        return mSize;
    }

    public void setSize(int w, int h) {
        mSize = new Size(w, h);
    }

}
