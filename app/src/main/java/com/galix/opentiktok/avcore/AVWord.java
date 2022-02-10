package com.galix.opentiktok.avcore;

import android.graphics.Rect;

import com.galix.opentiktok.render.IRender;

/**
 * 文字特效组件
 */
public class AVWord extends AVComponent {

    private String text;
    private int textSize;
    private int textColor;
    private Rect roi;

    public AVWord(long srcStartTime, long srcEndTime, AVComponentType type, IRender render) {
        super(srcStartTime, srcEndTime, type, render);
    }

    @Override
    public int open() {
        return 0;
    }

    @Override
    public int close() {
        return 0;
    }

    @Override
    public int readFrame() {
        return 0;
    }

    @Override
    public int seekFrame(long position) {
        return 0;
    }
}
