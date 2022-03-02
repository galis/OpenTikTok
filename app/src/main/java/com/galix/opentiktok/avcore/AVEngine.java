package com.galix.opentiktok.avcore;

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.galix.opentiktok.render.AudioRender;
import com.galix.opentiktok.render.OESRender;

import java.util.LinkedList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static com.galix.opentiktok.avcore.AVEngine.VideoState.VideoStatus.INIT;
import static com.galix.opentiktok.avcore.AVEngine.VideoState.VideoStatus.START;
import static com.galix.opentiktok.avcore.AVEngine.VideoState.VideoStatus.PAUSE;
import static com.galix.opentiktok.avcore.AVEngine.VideoState.VideoStatus.SEEK;
import static com.galix.opentiktok.avcore.AVEngine.VideoState.VideoStatus.RELEASE;

/**
 * 视频引擎
 */
public class AVEngine implements GLSurfaceView.Renderer {

    private static final String TAG = AVEngine.class.getSimpleName();
    private static final int PLAY_GAP = 100;//MS
    private static final int MAX_TEXTURE = 30;
    private static AVEngine gAVEngine = null;
    private VideoState mVideoState;
    private HandlerThread mAudioThread;
    private Handler mAudioHandler;
    private GLSurfaceView mGLSurfaceView;
    private OnFrameUpdateCallback mOnFrameUpdateCallback;
    private LinkedList<AVComponent> mComponents;
    private AudioRender mAudioRender;
    private OESRender mOesRender;
    private int[] mTextures;
    private int mValidTexture;//下一个有效纹理ID
    private final LinkedList<Command> mCmdList;

    public interface OnFrameUpdateCallback {
        void onFrameUpdate();
    }

    private AVEngine() {
        mVideoState = new VideoState();
        mComponents = new LinkedList<>();
        mCmdList = new LinkedList<>();
    }

    private static class Command {
        public enum Cmd {
            PLAY,
            PAUSE,
            SEEK,
            RELEASE,
            ADD_COM,
            REMOVE_COM
        }

        public Cmd cmd;
        public Object args;
    }

    public static class Clock {
        public float speed = 1.0f;
        public long lastUpdate = -1;
        public long delta = -1;

        @Override
        public String toString() {
            return "Clock{" +
                    "speed=" + speed +
                    ", lastUpdate=" + lastUpdate +
                    '}';
        }
    }

    //视频核心信息类
    public static class VideoState {

        public enum VideoStatus {
            INIT,
            START,
            PAUSE,
            SEEK,
            RELEASE
        }

        public boolean isInputEOF = false;
        public boolean isOutputEOF = false;
        public boolean isLastVideoDisplay = false;

        public long seekPositionUS = Long.MAX_VALUE;
        public long durationUS = Long.MIN_VALUE;//视频总时长 us
        public Clock videoClock;
        public Clock audioClock;
        public VideoStatus status = VideoStatus.INIT;//播放状态

        public VideoState() {
            videoClock = new Clock();
            audioClock = new Clock();
        }

        @Override
        public String toString() {
            return "VideoState{" +
                    "isInputEOF=" + isInputEOF +
                    ", isOutputEOF=" + isOutputEOF +
                    ", isLastVideoDisplay=" + isLastVideoDisplay +
                    ", seekPositionUS=" + seekPositionUS +
                    ", durationUS=" + durationUS +
                    ", videoClock=" + videoClock +
                    ", audioClock=" + audioClock +
                    ", status=" + status +
                    '}';
        }
    }

    public long getCurrentTimeUs() {
        return System.nanoTime() / 1000;
    }

    public void setClock(Clock clock, long time) {
        clock.lastUpdate = time;
        clock.delta = clock.lastUpdate - getCurrentTimeUs();
    }

    public long getClock(Clock clock) {
        if (clock.lastUpdate == -1) {
            return 0;
        }
        if (mVideoState.status == START) {
            return getCurrentTimeUs() + clock.delta;
        }
        return clock.lastUpdate;
    }

    //获取主时钟
    public long getMainClock() {
        return getClock(mVideoState.videoClock);
    }

    public static AVEngine getVideoEngine() {
        if (gAVEngine == null) {
            synchronized (AVEngine.class) {
                if (gAVEngine == null) {
                    gAVEngine = new AVEngine();
                }
            }
        }
        return gAVEngine;
    }

    public void configure(GLSurfaceView glSurfaceView) {
        mGLSurfaceView = glSurfaceView;
        mGLSurfaceView.setEGLContextClientVersion(3);
        mGLSurfaceView.setRenderer(this);
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        initInternal();
        mOesRender = new OESRender();
        mOesRender.open();
        mTextures = new int[MAX_TEXTURE];
        GLES30.glGenTextures(MAX_TEXTURE, mTextures, 0);
        mValidTexture = 0;
        createAudioDaemon();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mOesRender.write(new OESRender.OesRenderConfig(width, height));
    }

    @Override
    public void onDrawFrame(GL10 gl) {

        if (mVideoState.status == RELEASE) return;

        //计算正确position
        long videoClk = getMainClock();
        if (mVideoState.status == SEEK) {
            videoClk = mVideoState.seekPositionUS;
        }
        Log.d(TAG, "CurPos#" + videoClk);

        //处理视频片段
        long delay = 0;
        List<AVComponent> components = findComponents(AVComponent.AVComponentType.VIDEO, videoClk);
        for (
                AVComponent component : components) {
            if (mVideoState.status == START) {
                component.readFrame();
            } else if (mVideoState.status == SEEK) {
                component.seekFrame(videoClk);
            } else {
                while (!component.peekFrame().isValid()) {
                    component.readFrame();
                }
            }
            AVFrame avFrame = component.peekFrame();
            if (avFrame != null) {
                //画面同步.
                boolean needRender = true;
                long correctPts = avFrame.getPts();
                if (mVideoState.status == START) {
                    needRender = correctPts >= videoClk || (videoClk - correctPts) < 33000;
                    delay = Math.max(correctPts - videoClk, 0);
                }
                if (needRender) {
                    if (delay > 0) {
                        try {
                            Log.d(TAG, "delay#" + delay / 1000);
                            Thread.sleep(delay / 1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if (component.getRender() != null) {
                        component.getRender().render(avFrame);
                    } else {
                        mOesRender.render(avFrame);
                    }
                    mVideoState.isLastVideoDisplay = true;
                }
                setClock(mVideoState.videoClock, correctPts);
            }
        }

        //处理贴纸片段
        List<AVComponent> stickComponents = findComponents(AVComponent.AVComponentType.STICKER, -1);
        for (
                AVComponent component : stickComponents) {
            if (component.getRender() != null) {
                if (component.isValid(videoClk)) {
                    if (mVideoState.status == START) {
                        component.readFrame();
                        component.getRender().render(component.peekFrame());
                    } else if (mVideoState.status == SEEK) {
                        component.seekFrame(videoClk);
                        component.getRender().render(component.peekFrame());
                    } else {
                        while (!component.peekFrame().isValid()) {
                            component.readFrame();
                        }
                        component.getRender().render(component.peekFrame());
                    }
                } else {
                    component.peekFrame().setValid(false);
                    component.getRender().render(component.peekFrame());
                }
            }
        }

        //处理文字特效
        List<AVComponent> wordsComponents = findComponents(AVComponent.AVComponentType.WORD, -1);
        for (
                AVComponent component : wordsComponents) {
            if (component.getRender() != null) {
                AVFrame wordFrame = component.peekFrame();
                if (component.isValid(videoClk)) {
                    if (wordFrame.isValid()) {
                        component.getRender().render(wordFrame);
                    } else {
                        component.readFrame();
                    }
                } else {
                    wordFrame.setValid(false);
                    component.getRender().render(wordFrame);
                }
            }
        }

        //更新UI
        onFrameUpdate();

    }

    public void setOnFrameUpdateCallback(OnFrameUpdateCallback callback) {
        mOnFrameUpdateCallback = callback;
    }

    private void createAudioDaemon() {
        mAudioThread = new HandlerThread("audioThread");
        mAudioThread.start();
        mAudioHandler = new Handler(mAudioThread.getLooper());
        mAudioHandler.post(() -> {
            mAudioRender = new AudioRender();
            mAudioRender.open();
            while (mVideoState.status != RELEASE) {

                //处理命令
                handleCmd();

                if (mVideoState.status == START || mVideoState.status == SEEK) {
                    long targetPosition = getMainClock();
                    List<AVComponent> components = findComponents(AVComponent.AVComponentType.AUDIO, targetPosition);
                    for (AVComponent avComponent : components) {
                        if (mVideoState.status == START) {
                            if (!avComponent.peekFrame().isValid()) {
                                avComponent.readFrame();
                            }
                        } else {
                            avComponent.seekFrame(targetPosition);
                        }
                        if (avComponent.peekFrame().isValid()) {
                            AVAudio audio = (AVAudio) components.get(0);
                            AVFrame audioFrame = audio.peekFrame();
                            if (Math.abs(audioFrame.getPts() - getMainClock()) > 10000) {
                                Log.d(TAG, "Drop Audio Frame#" + audioFrame.toString());
                                continue;
                            }
                            if (mVideoState.status == START) {
                                if (audio.getRender() != null) {
                                    audio.getRender().render(audioFrame);
                                } else {
                                    mAudioRender.render(audioFrame);
                                }
                            }
                            setClock(mVideoState.audioClock, audioFrame.getPts());
                            audioFrame.markRead();
                        }
                    }
                } else {
                    try {
                        Thread.sleep(PLAY_GAP);//TODO
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            mAudioRender.close();
        });
    }

    public void compositeMp4(String mp4) {
//        pause();
//        reCalculate();//确保所有东西正确。
//        final int fps = 30;
//        final int WIDTH = 1920;
//        final int HEIGHT = 1080;
//        long curPos = 0;
//        while (curPos < mVideoState.durationUS) {
//            List<AVComponent> components = findComponents(AVComponent.AVComponentType.VIDEO, curPos);
//            for (components)
//        }
    }

    /**
     * 根据类型，时间组合查找组件
     *
     * @param type     ALL默认通过
     * @param position -1默认通过
     * @return 目标组件
     */
    public List<AVComponent> findComponents(AVComponent.AVComponentType type, long position) {
        List<AVComponent> components = new LinkedList<>();
        for (AVComponent component : mComponents) {
            if (type == AVComponent.AVComponentType.ALL || (component.getType() == type &&
                    (position == -1 || component.isValid(position)))) {
                components.add(component);
            }
        }
        return components;
    }

    /**
     * 添加组件
     *
     * @param avComponent
     */
    public void addComponent(AVComponent avComponent) {
        synchronized (mCmdList) {
            Command command = new Command();
            command.cmd = Command.Cmd.ADD_COM;
            command.args = avComponent;
            mCmdList.add(command);
        }
    }

    /**
     * 删除组件
     *
     * @param avComponent
     */
    public void removeComponent(AVComponent avComponent) {
        synchronized (mCmdList) {
            Command command = new Command();
            command.cmd = Command.Cmd.REMOVE_COM;
            command.args = avComponent;
            mCmdList.add(command);
        }
    }

    /**
     * 计算总时长US.
     */
    private void reCalculate() {
        if (mComponents.isEmpty()) {
            mVideoState.durationUS = 0;
            return;
        }
        for (AVComponent component : mComponents) {
            mVideoState.durationUS = Math.max(component.getSrcStartTime() + component.getDuration(), mVideoState.durationUS);
        }
    }

    public void start() {
        synchronized (mCmdList) {
            Command command = new Command();
            command.cmd = Command.Cmd.PLAY;
            mCmdList.add(command);
        }
    }

    public void pause() {
        synchronized (mCmdList) {
            Command command = new Command();
            command.cmd = Command.Cmd.PAUSE;
            mCmdList.add(command);
        }
    }

    public void startPause() {
        if (mVideoState.status == START) {
            pause();
        } else {
            start();
        }
    }

    public void seek(long position) {
        synchronized (mCmdList) {
            Command command = new Command();
            command.cmd = Command.Cmd.SEEK;
            command.args = position;
            mCmdList.add(command);
        }
    }

    public void release() {

        Log.d(TAG, "release()");

        synchronized (mCmdList) {
            Command command = new Command();
            command.cmd = Command.Cmd.RELEASE;
            mCmdList.add(command);
        }

        if (mAudioHandler != null && mAudioThread != null) {
            mAudioHandler.getLooper().quit();
            try {
                mAudioThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "mAudioThread quit");
        }

        for (AVComponent avComponent : mComponents) {
            avComponent.close();
        }
        mComponents.clear();

    }

    private void initInternal() {
        mVideoState.isInputEOF = false;
        mVideoState.isOutputEOF = false;
        mVideoState.status = INIT;
        mVideoState.isLastVideoDisplay = false;
        mVideoState.seekPositionUS = Long.MAX_VALUE;
    }

    private void handleCmd() {
        Log.d(TAG, "CMDS#" + mCmdList.size());
        if (mCmdList.isEmpty()) return;
        synchronized (mCmdList) {
            for (Command command : mCmdList) {
                if (command.cmd == Command.Cmd.PLAY) {
                    mVideoState.status = START;
                    setClock(mVideoState.videoClock, getClock(mVideoState.videoClock));
                } else if (command.cmd == Command.Cmd.PAUSE) {
                    mVideoState.status = PAUSE;
                } else if (command.cmd == Command.Cmd.RELEASE) {
                    mVideoState.status = RELEASE;
                    mOesRender.close();
//                    destroyInternal();
                } else if (command.cmd == Command.Cmd.SEEK) {
                    mVideoState.status = SEEK;
                    mVideoState.isInputEOF = false;
                    mVideoState.isOutputEOF = false;
                    mVideoState.seekPositionUS = (long) command.args;
                } else if (command.cmd == Command.Cmd.ADD_COM) {
                    AVComponent component = (AVComponent) command.args;
                    component.open();
                    mComponents.add(component);
                    reCalculate();
                } else if (command.cmd == Command.Cmd.REMOVE_COM) {
                    AVComponent component = (AVComponent) command.args;
                    component.close();
                    mComponents.remove(component);
                    reCalculate();
                } else {
                    Log.d(TAG, "Seek cmd error!");
                }
            }

            mCmdList.clear();
        }
    }

    public VideoState getVideoState() {
        return mVideoState;
    }

    public int nextValidTexture() {
        int targetTexture = -1;
        if (mValidTexture < MAX_TEXTURE) {
            targetTexture = mValidTexture;
            mValidTexture++;
        }
        return targetTexture;
    }

    private void onFrameUpdate() {
        if (mOnFrameUpdateCallback != null) {
            mOnFrameUpdateCallback.onFrameUpdate();
        }
//        dumpVideoState();
    }


    private void dumpVideoState() {
        Log.d(TAG, mVideoState.toString());
        for (AVComponent avComponent : mComponents) {
            Log.d(TAG, avComponent.toString());
        }
    }

}
