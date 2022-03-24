package com.galix.avcore.avcore;

import com.galix.avcore.render.IRender;

/**
 * 转场组件
 * 暂时按照30fps计算pts
 *
 * @Author: Galis
 * @Date:2022.01.24
 */
public class AVTransaction extends AVComponent {
    private final int FPS = 30;
    private final int TEST_DURATION = 2000000;
    private final long mFrameDelta = 33333;
    private int transactionType;//默认透明变换
    private AVVideo avVideo1;
    private AVVideo avVideo2;

    public AVTransaction(long engineStartTime, int transactionType, AVVideo video1, AVVideo video2, IRender render) {
        super(engineStartTime, AVComponentType.TRANSACTION, render);
        this.avVideo1 = video1;
        this.avVideo2 = video2;
        this.transactionType = transactionType;
    }

    @Override
    public int open() {
        if (!avVideo1.isOpen()) avVideo1.open();
        if (!avVideo2.isOpen()) avVideo2.open();
        setDuration(TEST_DURATION);
        setEngineEndTime(getEngineStartTime() + TEST_DURATION);
        return RESULT_OK;
    }

    @Override
    public int close() {
        if (avVideo1.isOpen()) avVideo1.close();
        if (avVideo2.isOpen()) avVideo2.close();
        return RESULT_OK;
    }

    @Override
    public int readFrame() {
        avVideo1.readFrame();
        avVideo2.readFrame();
        peekFrame().setPts(peekFrame().getPts() + mFrameDelta);
        freshFrame();
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        avVideo1.seekFrame(position);
        avVideo2.seekFrame(position);
        peekFrame().setPts(position);
        freshFrame();
        return RESULT_OK;
    }

    private void freshFrame() {
        peekFrame().setRoi(avVideo1.peekFrame().getRoi());
        peekFrame().setEof(peekFrame().getPts() >= getEngineEndTime());
        peekFrame().setDelta((peekFrame().getPts() - getEngineStartTime()) * 1.0f / getDuration());
        peekFrame().setDuration(mFrameDelta);
        peekFrame().setTexture(avVideo1.peekFrame().getTexture());
        peekFrame().setSurfaceTexture(avVideo1.peekFrame().getSurfaceTexture());
        peekFrame().setTextureExt(avVideo2.peekFrame().getTexture());
        peekFrame().setSurfaceTextureExt(avVideo2.peekFrame().getSurfaceTexture());
        peekFrame().setValid(true);
    }
}
