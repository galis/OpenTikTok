package com.galix.opentiktok.avcore;

import android.graphics.Rect;

import com.galix.opentiktok.render.IRender;
import com.galix.opentiktok.util.GifDecoder;

import java.io.InputStream;

/**
 * 特效贴纸
 *
 * @Author: Galis
 * @Date:2022.02.09
 */
public class AVSticker extends AVComponent {

    private String effectGifPath;
    private GifDecoder gifDecoder;
    private Rect roi;
    private InputStream inputStream;
    private int frameCount = 0;
    private int frameIdx = -1;

    public AVSticker(long srcStartTime, long srcEndTime, InputStream inputStream, IRender render) {
        super(srcStartTime, srcEndTime, AVComponentType.STICKER, render);
        this.inputStream = inputStream;
    }

    private long getEffectDuration() {
        long delayUS = 0;
        for (int i = 0; i < frameCount; i++) {
            delayUS += gifDecoder.getDelay(i) * 1000;
        }
        return delayUS;
    }

    @Override
    public int open() {
        gifDecoder = new GifDecoder();
        gifDecoder.read(inputStream);
        frameCount = gifDecoder.getFrameCount();
        markOpen(true);
        return RESULT_OK;
    }

    @Override
    public int close() {
        gifDecoder = null;
        frameCount = 0;
        frameIdx = 0;
        markOpen(false);
        return 0;
    }

    @Override
    public int readFrame() {
        if (!isOpen()) return RESULT_FAILED;
        frameIdx++;
        frameIdx = frameIdx % frameCount;
        peekFrame().setBitmap(gifDecoder.getFrame(frameIdx));
        peekFrame().setValid(true);
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        if (!isOpen()) return RESULT_FAILED;
        if (position < getEngineStartTime() || (position - getEngineStartTime() > getDuration())) {
            peekFrame().setBitmap(null);
            peekFrame().setValid(true);
            return RESULT_FAILED;
        }
        long correctPos = (position - getEngineStartTime()) % getEffectDuration();
        if (correctPos < 0) {
            peekFrame().setBitmap(null);
            peekFrame().setValid(true);
            return RESULT_FAILED;
        }
        long delayUS = 0;
        for (int i = 0; i < frameCount; i++) {
            delayUS += gifDecoder.getDelay(i) * 1000;
            if (delayUS >= correctPos) {
                frameIdx = i;
                peekFrame().setBitmap(gifDecoder.getFrame(frameIdx));
                peekFrame().setValid(true);
                return RESULT_OK;
            }
        }
        return RESULT_OK;
    }

    @Override
    public String toString() {
        return "AVEffect{" +
                "effectGifPath='" + effectGifPath + '\'' +
                ", gifDecoder=" + gifDecoder +
                ", roi=" + roi +
                ", inputStream=" + inputStream +
                ", frameCount=" + frameCount +
                ", frameIdx=" + frameIdx +
                "} " + super.toString();
    }
}
