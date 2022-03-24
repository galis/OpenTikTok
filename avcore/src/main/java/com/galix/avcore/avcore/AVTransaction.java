package com.galix.avcore.avcore;

import com.galix.avcore.render.IRender;

/**
 * 转场组件
 *
 * @Author: Galis
 * @Date:2022.01.24
 */
public class AVTransaction extends AVComponent {
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
        return RESULT_OK;
    }

    @Override
    public int close() {
        return RESULT_OK;
    }

    @Override
    public int readFrame() {
        avVideo1.readFrame();
        avVideo2.readFrame();
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        avVideo1.seekFrame(position);
        avVideo2.seekFrame(position);
        return RESULT_OK;
    }
}
