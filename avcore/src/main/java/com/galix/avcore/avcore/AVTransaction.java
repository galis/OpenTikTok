package com.galix.avcore.avcore;

import com.galix.avcore.render.IRender;
import com.galix.avcore.util.LogUtil;
import com.galix.avcore.util.TimeUtils;

import java.util.Map;

/**
 * 转场组件
 * 暂时按照30fps计算pts
 * <p>
 * 转场组件clipStart,clipEnd代表效果生效，这里clipEnd-clipStart不一定等于engineEnd-engineStart;
 * clipStart,clipEnd在转场是个特殊时间，区别于AVVideo AVAudio等组件
 * engineStart,engineEnd总是取整秒数
 *
 * @Author: Galis
 * @Date:2022.01.24
 */
public class AVTransaction extends AVComponent {
    public static final Integer TRAN_ALPHA = 1;
    private final int TEST_DURATION = 2000000;
    private final long mFrameDelta = 33333;
    private int mTransactionType;//默认透明变换
    private long mTransactionDuration;
    private AVVideo avVideo1;
    private AVVideo avVideo2;

    public AVTransaction(long engineStartTime, AVVideo video1, AVVideo video2, IRender render) {
        super(engineStartTime, AVComponentType.TRANSACTION, render);
        this.avVideo1 = video1;
        this.avVideo2 = video2;
    }

    @Override
    public int open() {
        if (!avVideo1.isOpen()) avVideo1.open();
        if (!avVideo2.isOpen()) avVideo2.open();
        setDuration(TEST_DURATION);
        setupNormalTime();
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
        freshFrame();
        setPosition(getPosition() + peekFrame().getDuration());
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        avVideo1.seekFrame(position);
        avVideo2.seekFrame(position);
        setPosition(position);
        freshFrame();
        return RESULT_OK;
    }

    @Override
    public int write(Map<String, Object> configs) {
        if (configs.containsKey("tran_type")) {
            mTransactionType = (int) configs.get("tran_type");
        }
        if (configs.containsKey("tran_duration")) {//确保duration不大于video1 video2 duration
            mTransactionDuration = (long) configs.get("tran_duration");
            setDuration(mTransactionDuration);
        }
        if (configs.containsKey("tran_visible")) {
            setVisible((Boolean) configs.get("tran_visible"));
        }
        if (isVisible()) {
            setupValidTime();
        } else {
            setupNormalTime();
        }
        if (getRender() != null) {
            getRender().write(configs);
        }
        return super.write(configs);
    }

    //转场需要做的就是设置好EngineTime ClipTime和engine start and end of video2;
    private void setupValidTime() {
        if (mTransactionType == TRAN_ALPHA) {
            trans2().setEngineStartTime(trans1().getEngineEndTime() - mTransactionDuration);
            trans2().setEngineEndTime(trans2().getEngineStartTime() + trans2().getClipDuration());
            setClipStartTime(trans1().getEngineEndTime() - mTransactionDuration);
            setClipEndTime(trans1().getEngineEndTime());
            setEngineStartTime(TimeUtils.leftWithoutTime(getClipStartTime()));
            setEngineEndTime(TimeUtils.rightWithoutTime(getClipEndTime()));
        } else {
            setupNormalTime();
            LogUtil.logEngine("mTransactionType#" + mTransactionType + "#No support!");
        }
    }

    //设置无转场效果情况下的正常时间.
    private void setupNormalTime() {
        //恢复video2 engine time
        if (avVideo1.getEngineEndTime() != avVideo2.getEngineStartTime()) {
            avVideo2.setEngineStartTime(avVideo1.getEngineEndTime());
            avVideo2.setEngineEndTime(avVideo2.getEngineStartTime() + avVideo2.getClipDuration());
        }

        setEngineStartTime(TimeUtils.leftTime(TimeUtils.leftWithoutTime(avVideo1.getEngineEndTime())));

        if (avVideo2.getClipStartTime() != 0) {//video2 有裁剪的情况，单独处理
            long duration = TimeUtils.rightTime(avVideo2.getEngineStartTime()) - avVideo2.getEngineStartTime();
            if (duration < 1000000) {
                duration += 1000000;
            }
            setEngineEndTime(avVideo2.getEngineStartTime() + duration);
        } else {//没有裁剪，trans2 duration 那么总是默认1s
            setEngineEndTime(avVideo2.getEngineStartTime() + 1000000);
        }

        setClipStartTime(getEngineStartTime());
        setClipEndTime(getEngineEndTime());
    }

    private void freshFrame() {
        avVideo1.peekFrame().getSurfaceTexture().updateTexImage();
        avVideo2.peekFrame().getSurfaceTexture().updateTexImage();
        peekFrame().setPts(getPosition());
        peekFrame().setRoi(avVideo1.peekFrame().getRoi());
        peekFrame().setEof(peekFrame().getPts() >= getEngineEndTime());
        peekFrame().setDelta((peekFrame().getPts() - getClipStartTime()) * 1.0f / getClipDuration());
        peekFrame().setDuration(mFrameDelta);
        peekFrame().setTexture(avVideo1.peekFrame().getTexture());
        peekFrame().setSurfaceTexture(avVideo1.peekFrame().getSurfaceTexture());
        peekFrame().setTextureExt(avVideo2.peekFrame().getTexture());
        peekFrame().setSurfaceTextureExt(avVideo2.peekFrame().getSurfaceTexture());
        peekFrame().setValid(true);
    }

    public AVVideo trans1() {
        return avVideo1;
    }

    public AVVideo trans2() {
        return avVideo2;
    }

    @Override
    public String toString() {
        return "AVTransaction{\n" +
                "\tTEST_DURATION=" + TEST_DURATION + "\n" +
                "\tmFrameDelta=" + mFrameDelta + "\n" +
                "\tmTransactionType=" + mTransactionType + "\n" +
                "\tmTransactionDuration=" + mTransactionDuration + "\n" +
                "\tavVideo1=" + avVideo1 + "\n" +
                "\tavVideo2=" + avVideo2 + "\n" +
                "} " + super.toString() + "\n";
    }
}
