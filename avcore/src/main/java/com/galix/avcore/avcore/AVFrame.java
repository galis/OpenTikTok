package com.galix.avcore.avcore;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;

import com.galix.avcore.render.filters.GLTexture;

import java.nio.ByteBuffer;

public class AVFrame {
    private long pts = -1;
    private GLTexture texture = new GLTexture(0, false);
    private GLTexture textureExt = new GLTexture(0, false);
    private boolean eof = false;
    private boolean isValid = false;
    private long duration = 0;
    private int textSize;
    private int textColor;
    private float delta;
    private String text;
    private Rect roi;
    private Bitmap bitmap;
    private ByteBuffer byteBuffer;
    private SurfaceTexture surfaceTexture;
    private SurfaceTexture surfaceTextureExt;
    private Object ext;//额外的数据

    public long getPts() {
        return pts;
    }

    public void setPts(long pts) {
        this.pts = pts;
    }

    public GLTexture getTexture() {
        return texture;
    }

    public void setTexture(GLTexture texture) {
        this.texture.idAsBuf().put(texture.id());
        this.texture.setSize(texture.size().getWidth(), texture.size().getHeight());
        this.texture.setOes(texture.isOes());
    }

    public void setTexture(int textureId) {
        this.texture.idAsBuf().put(textureId);
    }

    public GLTexture getTextureExt() {
        return textureExt;
    }

    public void setTextureExt(GLTexture textureExt) {
        this.textureExt = textureExt;
    }

    public boolean isEof() {
        return eof;
    }

    public void setEof(boolean eof) {
        this.eof = eof;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    public void setByteBuffer(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    public SurfaceTexture getSurfaceTexture() {
        return surfaceTexture;
    }

    public void setSurfaceTexture(SurfaceTexture surfaceTexture) {
        this.surfaceTexture = surfaceTexture;
    }

    public SurfaceTexture getSurfaceTextureExt() {
        return surfaceTextureExt;
    }

    public void setSurfaceTextureExt(SurfaceTexture surfaceTextureExt) {
        this.surfaceTextureExt = surfaceTextureExt;
    }

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getTextSize() {
        return textSize;
    }

    public void setTextSize(int textSize) {
        this.textSize = textSize;
    }

    public int getTextColor() {
        return textColor;
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
    }

    public float getDelta() {
        return delta;
    }

    public void setDelta(float delta) {
        this.delta = delta;
    }

    public Rect getRoi() {
        return roi;
    }

    public void setRoi(Rect roi) {
        this.roi = roi;
    }

    public void markRead() {
        isValid = false;
    }

    public Object getExt() {
        return ext;
    }

    public void setExt(Object ext) {
        this.ext = ext;
    }

    @Override
    public String toString() {
        return "AVFrame{" +
                "pts=" + pts +
                ", texture=" + texture +
                ", eof=" + eof +
                ", isValid=" + isValid +
                ", duration=" + duration +
                ", text='" + text + '\'' +
                ", textSize=" + textSize +
                ", textColor=" + textColor +
                ", roi=" + roi +
                ", bitmap=" + bitmap +
                ", byteBuffer=" + byteBuffer +
                ", surfaceTexture=" + surfaceTexture +
                '}';
    }
}
