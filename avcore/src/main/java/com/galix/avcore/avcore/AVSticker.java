package com.galix.avcore.avcore;

import android.graphics.Rect;
import android.opengl.GLUtils;

import com.galix.avcore.render.IRender;
import com.galix.avcore.render.filters.GLTexture;
import com.galix.avcore.util.GLUtil;
import com.galix.avcore.util.GifDecoder;

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

    public AVSticker(long srcStartTime, InputStream inputStream, IRender render) {
        super(srcStartTime, AVComponentType.STICKER, render);
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
        setDuration(5000000);
        setEngineEndTime(getEngineStartTime() + getDuration());
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
        if (peekFrame().getTexture().id() != 0) {
            peekFrame().setTexture(GLUtil.loadTexture(peekFrame().getBitmap()));
        } else {
            GLUtil.loadTexture(peekFrame().getTexture().id(), peekFrame().getBitmap());
        }
        peekFrame().getTexture().setOes(false);
        peekFrame().getTexture().setSize(peekFrame().getBitmap().getWidth(), peekFrame().getBitmap().getHeight());
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        if (!isOpen()) return RESULT_FAILED;
        if (position < getEngineStartTime() || (position - getEngineStartTime() > getDuration())) {
            peekFrame().setBitmap(null);
            peekFrame().setValid(true);
            peekFrame().getTexture().setMute(true);
            return RESULT_FAILED;
        }
        long correctPos = (position - getEngineStartTime()) % getEffectDuration();
        if (correctPos < 0) {
            peekFrame().setBitmap(null);
            peekFrame().setValid(true);
            peekFrame().getTexture().setMute(true);
            return RESULT_FAILED;
        }
        long delayUS = 0;
        for (int i = 0; i < frameCount; i++) {
            delayUS += gifDecoder.getDelay(i) * 1000;
            if (delayUS >= correctPos) {
                frameIdx = i;
                peekFrame().setBitmap(gifDecoder.getFrame(frameIdx));
                peekFrame().setValid(true);
                peekFrame().getTexture().setMute(false);
                peekFrame().getTexture().setOes(false);
                peekFrame().getTexture().setSize(peekFrame().getBitmap().getWidth(),
                        peekFrame().getBitmap().getHeight());
                if (peekFrame().getTexture().id() == 0) {
                    peekFrame().setTexture(GLUtil.loadTexture(peekFrame().getBitmap()));
                } else {
                    GLUtil.loadTexture(peekFrame().getTexture().id(), peekFrame().getBitmap());
                }
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
