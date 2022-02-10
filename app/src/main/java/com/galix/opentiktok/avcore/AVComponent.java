package com.galix.opentiktok.avcore;

import com.galix.opentiktok.render.IRender;

import java.util.List;

/**
 * AV组件，视频，音频，文字，特效，转场等
 * 1.有时间属性的
 * 2.提供open,close,read,seek等方法
 * 滤镜，背景这些就不属于了。
 * <p>
 * srcStartTime <= curStartTime <= curEndTime <= srcEndTime
 * </p>
 *
 * @Author: galis
 * @Date: 2022.01.22
 */
public abstract class AVComponent {

    public static final int RESULT_FAILED = -1;
    public static final int RESULT_OK = 0;

    public enum AVComponentType {
        VIDEO,          //视频
        AUDIO,          //音频
        WORD,           //文字
        STICKER,         //贴纸
        TRANSACTION,    //转场
        PIP,             //画中画
        ALL
    }

    private long srcStartTime;
    private long srcEndTime;
    private long duration;//原始数据
    private long position;
    private boolean isOpen;
    private IRender render;
    private AVFrame cache;
    private AVComponentType type;

    public AVComponent(long srcStartTime, long srcEndTime, AVComponentType type, IRender render) {
        this.srcStartTime = srcStartTime;
        this.srcEndTime = srcEndTime;
        this.type = type;
        this.position = -1;
        this.isOpen = false;
        this.cache = new AVFrame();
        this.render = render;
        this.duration = srcEndTime - srcStartTime;
    }

    public long getSrcStartTime() {
        return srcStartTime;
    }

    public void setSrcStartTime(long srcStartTime) {
        this.srcStartTime = srcStartTime;
    }

    public long getSrcEndTime() {
        return srcEndTime;
    }

    public void setSrcEndTime(long srcEndTime) {
        this.srcEndTime = srcEndTime;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public AVComponentType getType() {
        return type;
    }

    public void setType(AVComponentType type) {
        this.type = type;
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void markOpen(boolean open) {
        isOpen = open;
    }

    public boolean isValid(long position) {
        return position >= srcStartTime && position <= srcEndTime;
    }

    public abstract int open();

    public abstract int close();

    public abstract int readFrame();//block模式 读取一帧数据，放到AVFrame

    public abstract int seekFrame(long position);////block模式 定位到特定位置，然后读一帧数据

    public AVFrame peekFrame() {//获取AVFrame
        return cache;
    }

    public int markRead() {//消费一帧数据，将AVFrame set valid = false
        if (!isOpen()) return RESULT_FAILED;
        cache.setValid(false);
        return RESULT_OK;
    }

    public IRender getRender() {
        return render;
    }

    public void setRender(IRender render) {
        this.render = render;
    }

    @Override
    public String toString() {
        return "AVComponent{" +
                "srcStartTime=" + srcStartTime +
                ", srcEndTime=" + srcEndTime +
                ", duration=" + duration +
                ", position=" + position +
                ", type=" + type +
                ", isOpen=" + isOpen +
                '}';
    }
}
