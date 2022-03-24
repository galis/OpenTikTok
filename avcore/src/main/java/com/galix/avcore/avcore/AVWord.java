package com.galix.avcore.avcore;

import android.graphics.Rect;

import com.galix.avcore.render.IRender;

/**
 * 文字特效组件
 */
public class AVWord extends AVComponent {

    private String text = "HelloWorld!";
    private int textSize;
    private int textColor;
    private Rect roi;

    public AVWord(long srcStartTime, IRender render) {
        super(srcStartTime, AVComponentType.WORD, render);
    }

    @Override
    public int open() {
        setDuration(50);//TODO
        setEngineEndTime(getEngineStartTime() + getDuration());
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
