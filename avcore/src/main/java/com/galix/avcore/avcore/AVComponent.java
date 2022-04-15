package com.galix.avcore.avcore;

import com.galix.avcore.render.IRender;

import org.opencv.core.Mat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.opencv.core.CvType.CV_32F;

/**
 * AV组件，视频，音频，文字，特效，转场等
 * 1.有时间属性的
 * 2.提供open,close,read,seek等方法
 * 滤镜，背景这些就不属于了。
 * <p>
 * du1:duration of file1            20s
 * cst1:clipStartTime of file1      第7s
 * cet1:clipEndTime of file1        第10s
 * est1:engineStartTime of file1    第0s
 * eet1:engineEndTime of file1      第3s
 * <p>
 * du2:duration of file2            30s
 * cst2:clipStartTime of file2      第10s
 * cet2:clipEndTime of file2        第20s
 * est2:engineStartTime of file1    第3s
 * eet2:engineEndTime of file2      第13s
 * <p>
 * File1:
 * 0|====================|du1
 * cst1|===|cet1
 * File2:
 * 0|+++++++++++++++++++++++++++++++|du2
 * cst2|++++++++++|cet2
 * <p>
 * Engine：
 * est1 est2
 * |===||++++++++++|
 * eet1         eet2
 *
 * @Author: galis
 * @Date: 2022.01.22
 */
public abstract class AVComponent {

    private static final String TAG = AVComponent.class.getSimpleName();
    public static final int RESULT_FAILED = -1;
    public static final int RESULT_OK = 0;
    private static final Mat mIdentityMat = Mat.eye(3, 3, CV_32F);

    public enum AVComponentType {
        VIDEO,          //视频
        AUDIO,          //音频
        WORD,           //文字
        STICKER,         //贴纸
        PAG,         //PAG
        TRANSACTION,    //转场
        PIP,             //画中画
        ALL
    }

    private long engineStartTime;//引擎相关
    private long engineEndTime;//引擎相关
    private long clipStartTime;//文件相关的
    private long clipEndTime;//文件相关
    private long duration;//组件本身duration,不可改变
    private long position;//
    private boolean isOpen;
    private boolean isLoop;//是否循环播放
    private boolean isVisible;//是否可见
    private IRender render;
    private AVFrame cache;
    private AVComponentType type;
    private AtomicBoolean lockLock;
    private Mat matrix;

    public AVComponent(long engineStartTime, AVComponentType type, IRender render) {
        this.engineStartTime = engineStartTime;
        this.engineEndTime = -1;
        this.clipStartTime = -1;
        this.clipEndTime = -1;
        this.type = type;
        this.position = -1;
        this.isOpen = false;
        this.cache = new AVFrame();
        this.render = render;
        this.lockLock = new AtomicBoolean(false);
        this.isLoop = false;
        this.isVisible = true;
        this.matrix = mIdentityMat;
    }

    public AVComponent() {
        this.engineStartTime = -1;
        this.engineEndTime = -1;
        this.clipStartTime = -1;
        this.clipEndTime = -1;
        this.type = type;
        this.position = -1;
        this.isOpen = false;
        this.cache = new AVFrame();
        this.render = null;
        this.lockLock = new AtomicBoolean(false);
        this.isLoop = false;
        this.isVisible = true;
        this.matrix = mIdentityMat;
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

    public long getClipStartTime() {
        return clipStartTime;
    }

    public void setClipStartTime(long clipStartTime) {
        this.clipStartTime = clipStartTime;
    }

    public long getClipEndTime() {
        return clipEndTime;
    }

    public void setClipEndTime(long clipEndTime) {
        this.clipEndTime = clipEndTime;
    }

    public long getEngineDuration() {
        return engineEndTime - engineStartTime;
    }

    public long getClipDuration() {
        return clipEndTime - clipStartTime;
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
        return position >= engineStartTime && position < engineEndTime;
    }

    public boolean isLoop() {
        return isLoop;
    }

    public void setLoop(boolean loop) {
        isLoop = loop;
    }

    public Mat getMatrix() {
        return matrix;
    }

    public void setMatrix(Mat matrix) {
        this.matrix = matrix;
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void setVisible(boolean visible) {
        isVisible = visible;
    }

    public abstract int open();

    public abstract int close();

    /**
     * 读取一帧数据，通过peekFrame可以获取
     *
     * @return RESULT_SUCCESS/RESULT_FAILED
     */
    public abstract int readFrame();//block模式 读取一帧数据，放到AVFrame

    /**
     * 针对Engine Time
     *
     * @param position time of engine
     * @return RESULT_SUCCESS/RESULT_FAILED
     */
    public abstract int seekFrame(long position);////block模式 定位到特定位置，然后读一帧数据

    /**
     * 默认空操作
     *
     * @return RESULT_SUCCESS/RESULT_FAILED
     */
    public int write(Map<String, Object> configs) {
        return RESULT_OK;
    }

    /**
     * 获取一帧数据
     *
     * @return AVFrame
     */
    public AVFrame peekFrame() {
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
                ", fileStartTime=" + clipStartTime +
                ", fileEndTime=" + clipEndTime +
                ", duration=" + duration +
                ", position=" + position +
                ", isOpen=" + isOpen +
                ", render=" + render +
                ", cache=" + cache +
                ", type=" + type +
                ", lockLock=" + lockLock +
                '}';
    }
}
