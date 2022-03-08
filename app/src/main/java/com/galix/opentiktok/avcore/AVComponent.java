package com.galix.opentiktok.avcore;

import com.galix.opentiktok.render.IRender;

import java.util.concurrent.atomic.AtomicBoolean;

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

    private long engineStartTime;//引擎相关
    private long engineEndTime;//引擎相关
    private long fileStartTime;//文件相关的
    private long fileEndTime;//文件相关
    private long duration;//组件本身duration,不可改变
    private long position;
    private boolean isOpen;
    private IRender render;
    private AVFrame cache;
    private AVComponentType type;
    private AtomicBoolean lockLock;

    public AVComponent(long engineStartTime, long engineEndTime,
                       AVComponentType type, IRender render) {
        this.engineStartTime = engineStartTime;
        this.engineEndTime = engineEndTime;
        this.type = type;
        this.position = -1;
        this.isOpen = false;
        this.cache = new AVFrame();
        this.render = render;
        this.lockLock = new AtomicBoolean(false);
    }

    public AVComponent() {
        this.engineStartTime = -1;
        this.engineEndTime = -1;
        this.fileStartTime = -1;
        this.fileEndTime = -1;
        this.type = type;
        this.position = -1;
        this.isOpen = false;
        this.cache = new AVFrame();
        this.render = null;
        this.lockLock = new AtomicBoolean(false);
    }

    public long getEngineStartTime() {
        return engineStartTime;
    }

    public void setEngineStartTime(long engineStartTime) {
        this.engineStartTime = engineStartTime;
    }

    public long getEngineEndTime() {
        return engineEndTime;
    }

    public void setEngineEndTime(long engineEndTime) {
        this.engineEndTime = engineEndTime;
    }

    public long getFileStartTime() {
        return fileStartTime;
    }

    public void setFileStartTime(long fileStartTime) {
        this.fileStartTime = fileStartTime;
    }

    public long getFileEndTime() {
        return fileEndTime;
    }

    public void setFileEndTime(long fileEndTime) {
        this.fileEndTime = fileEndTime;
    }

    public long getEngineDuration() {
        return engineEndTime - engineStartTime;
    }

    public long getFileDuration() {
        return fileEndTime - fileStartTime;
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
        return position >= engineStartTime && position <= engineEndTime;
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

    public void lock() {
        while (!lockLock.compareAndSet(false, true)) ;
    }

    public void unlock() {
        while (!lockLock.compareAndSet(true, false)) ;
    }


    @Override
    public String toString() {
        return "AVComponent{" +
                "engineStartTime=" + engineStartTime +
                ", engineEndTime=" + engineEndTime +
                ", clipStartTime=" + fileStartTime +
                ", clipEndTime=" + fileEndTime +
                ", duration=" + duration +
                ", position=" + position +
                ", isOpen=" + isOpen +
                ", render=" + render +
                ", cache=" + cache +
                ", type=" + type +
                '}';
    }
}
