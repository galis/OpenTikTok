package com.galix.opentiktok.avcore;

import android.graphics.Rect;

import com.galix.opentiktok.render.IRender;

/**
 * 文字特效组件
 */
public class AVWord extends AVComponent {

    private String text = "HelloWorld!";
    private int textSize;
    private int textColor;
    private Rect roi;

    public AVWord(long srcStartTime, long srcEndTime, IRender render) {
        super(srcStartTime, srcEndTime, AVComponentType.WORD, render);
    }

    @Override
    public int open() {
        markOpen(true);
        return RESULT_OK;
    }

    @Override
    public int close() {
        return RESULT_OK;
    }

    @Override
    public int readFrame() {
        peekFrame().setValid(true);
        peekFrame().setText(text);
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        return readFrame();
    }
}
