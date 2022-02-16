package com.galix.opentiktok.avcore;

import com.galix.opentiktok.render.IRender;

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

    public AVTransaction(long srcStartTime, long srcEndTime, int transactionType, AVVideo video1, AVVideo video2, IRender render) {
        super(srcStartTime, srcEndTime, AVComponentType.TRANSACTION, render);
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
