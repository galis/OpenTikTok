package com.galix.opentiktok.avcore;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;

import java.nio.ByteBuffer;

public class AVFrame {
    private long pts = -1;
    private int texture = -1;
    private boolean eof = false;
    private boolean isValid = false;
    private long duration = 0;
    private String text;
    private int textSize;
    private int textColor;
    private Rect roi;
    private Bitmap bitmap;
    private ByteBuffer byteBuffer;
    private SurfaceTexture surfaceTexture;

    public long getPts() {
        return pts;
    }

    public void setPts(long pts) {
        this.pts = pts;
    }

    public int getTexture() {
        return texture;
    }

    public void setTexture(int texture) {
        this.texture = texture;
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

    public Rect getRoi() {
        return roi;
    }

    public void setRoi(Rect roi) {
        this.roi = roi;
    }

    public void markRead() {
        isValid = false;
    }
}
