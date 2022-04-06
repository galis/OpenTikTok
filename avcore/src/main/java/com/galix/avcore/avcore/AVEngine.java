package com.galix.avcore.avcore;

import android.graphics.Rect;
import android.opengl.GLES30;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import com.galix.avcore.render.AudioRender;
import com.galix.avcore.render.OESRender;
import com.galix.avcore.util.EglHelper;
import com.galix.avcore.util.Mp4Composite;
import com.galix.avcore.util.OtherUtils;

import org.libpag.PAGComposition;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;
import static com.galix.avcore.avcore.AVEngine.VideoState.VideoStatus.INIT;
import static com.galix.avcore.avcore.AVEngine.VideoState.VideoStatus.PAUSE;
import static com.galix.avcore.avcore.AVEngine.VideoState.VideoStatus.RELEASE;
import static com.galix.avcore.avcore.AVEngine.VideoState.VideoStatus.SEEK;
import static com.galix.avcore.avcore.AVEngine.VideoState.VideoStatus.START;

/**
 * 视频引擎
 */
public class AVEngine {

    static {
        System.loadLibrary("arcore");
    }

    private static final String TAG = "AVEngine_normal";
    private static final int PLAY_GAP = 10;//MS
    private static AVEngine gAVEngine = null;
    private VideoState mVideoState;
    private HandlerThread mAudioThread;
    private Handler mAudioHandler;
    private HandlerThread mEngineThread;
    private Handler mEngineHandler;
    private EglHelper mEglHelper;
    private SurfaceView mSurfaceView;
    private EngineCallback mUpdateCallback, mCompositeCallback;
    private final LinkedList<AVComponent> mVideoComponents;//非音频类
    private final LinkedList<AVComponent> mAudioComponents;//音频 Audio
    private AVComponent mLastVideoComponent;
    private AVComponent mLastAudioComponent;
    private AudioRender mAudioRender;
    private OESRender mOesRender;
    private BlockingQueue<Command> mCmdQueue;

    public interface EngineCallback {
        void onCallback(Object... args1);
    }

    private AVEngine() {
        mCmdQueue = new LinkedBlockingQueue<>();
        mVideoState = new VideoState();
        mVideoComponents = new LinkedList<>();
        mAudioComponents = new LinkedList<>();
    }

    private static class Command {
        public enum Cmd {
            INIT,
            PLAY,
            PAUSE,
            SEEK,
            RELEASE,
            ADD_COM,
            REMOVE_COM,
            CHANGE_COM,
            SURFACE_CREATED,
            SURFACE_CHANGED,
            SURFACE_DESTROYED,
            COMPOSITE;
        }

        public Cmd cmd;
        public Object args0;
        public Object args1;
        public Object args2;
        public Object args3;
    }

    private static class Clock {
        public float speed = 1.0f;
        public long lastUpdate = -1;
        public long delta = -1;
        public long lastSeekReq = 0;
        public long seekReq = 0;

        @Override
        public String toString() {
            return "Clock{" +
                    "speed=" + speed +
                    ", lastUpdate=" + lastUpdate +
                    ", delta=" + delta +
                    ", lastSeekReq=" + lastSeekReq +
                    ", seekReq=" + seekReq +
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

        public boolean isInputEOF;
        public boolean isOutputEOF;
        public boolean isLastVideoDisplay;
        public long seekPositionUS;
        public long durationUS;//视频总时长 us
        public int mBgColor;
        public int mTargetGop;
        public int mTargetAb;
        public int mTargetVb;
        public boolean hasAudio;
        public boolean hasVideo;
        public boolean readyAudio;//合成视频用
        public boolean readyVideo;//合成视频用
        public Size mTargetSize;//合成视频目标宽高
        public String mTargetPath;//合成视频路径
        public Clock videoClock;
        public Clock extClock;
        public Clock audioClock;
        public VideoStatus status;//播放状态

        public VideoState() {
            reset();
        }

        public void reset() {
            videoClock = new Clock();
            audioClock = new Clock();
            extClock = new Clock();
            durationUS = Long.MIN_VALUE;
            isInputEOF = false;
            isOutputEOF = false;
            isLastVideoDisplay = false;
            seekPositionUS = Long.MAX_VALUE;
            status = VideoStatus.INIT;
        }

        @Override
        public String toString() {
            return "VideoState{" +
                    "isInputEOF=" + isInputEOF +
                    ", isOutputEOF=" + isOutputEOF +
                    ", isLastVideoDisplay=" + isLastVideoDisplay +
                    ", seekPositionUS=" + seekPositionUS +
                    ", durationUS=" + durationUS +
                    ", videoClock=" + videoClock.toString() +
                    ", extClock=" + extClock.toString() +
                    ", audioClock=" + audioClock.toString() +
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
        long currentClk = getClock(mVideoState.extClock);
        if (currentClk > mVideoState.durationUS) {
            return mVideoState.durationUS;
        }
        return currentClk;
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

    public void configure(SurfaceView glSurfaceView) {
        mSurfaceView = glSurfaceView;
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback2() {
            @Override
            public void surfaceRedrawNeeded(@NonNull SurfaceHolder holder) {

            }

            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                Log.d(TAG, "surfaceCreated");
                AVEngine.this.surfaceCreated(holder.getSurface());
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                Log.d(TAG, "surfaceChanged");
                AVEngine.this.surfaceChanged(holder.getSurface(), width, height);
                seek(true);
                seek(0);
                seek(false);
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                Log.d(TAG, "surfaceDestroyed");
                AVEngine.this.surfaceDestroyed();
            }
        });
    }

    /**
     * 设置画布大小
     *
     * @param size 画布大小
     */
    public void setCanvasSize(Size size) {
        if (size == null) return;
        mSurfaceView.getLayoutParams().width = size.getWidth();
        mSurfaceView.getLayoutParams().height = size.getHeight();
        mSurfaceView.requestLayout();
        mVideoState.mTargetSize = size;
    }

    /**
     * 设置背景颜色
     *
     * @param color
     */
    public void setBgColor(int color) {
        mVideoState.mBgColor = color;
    }

    public long onDrawFrame() {

        long delay = 0;
        if (mVideoState.status == RELEASE) {
            mOesRender.close();
            return delay;
        }

        //处理视频片段
        if (mVideoComponents.isEmpty()) {
            return delay;
        }
        long extClk = getMainClock();
        List<AVComponent> components;
        components = findComponents(AVComponent.AVComponentType.TRANSACTION, extClk);
        if (components.isEmpty()) {
            components = findComponents(AVComponent.AVComponentType.VIDEO, extClk);
        }
        boolean needSeek = mVideoState.videoClock.seekReq != mVideoState.videoClock.lastSeekReq;
        if (needSeek) {//优先处理seek行为
            for (AVComponent component : components) {
                component.lock();
                component.seekFrame(extClk);
                component.unlock();
            }
        }
        mVideoState.videoClock.lastSeekReq = mVideoState.videoClock.seekReq;

        for (AVComponent component : components) {
            component.lock();
            if (!component.peekFrame().isValid()) {
                component.readFrame();
            }
            component.unlock();
            AVFrame avFrame = component.peekFrame();
            if (!avFrame.isValid()) {
                Log.d(TAG, "VIDEO#WTF???Something I don't understand!");
                continue;
            }
            //画面同步.
            boolean needRender = true;
            long correctPts = avFrame.getPts();
            if (mVideoState.status == START) {
                needRender = correctPts >= extClk || (extClk - correctPts) < 33000;
                delay = Math.max(correctPts - extClk, 0);
            }
            if (needRender) {
                if (delay > 0) {
                    try {
//                        Log.d(TAG, "delay#" + delay / 1000);
                        Thread.sleep(delay / 1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (component.getRender() != null) {
                    if (!component.getRender().isOpen()) {
                        component.getRender().open();
//                        component.getRender().write(buildConfig("surface_size", mVideoState.mTargetSize));//TODO
                    }
                    component.getRender().render(avFrame);
                } else {
                    avFrame.setTextColor(mVideoState.mBgColor);
                    mOesRender.render(avFrame);
                }
                mVideoState.isLastVideoDisplay = true;
                setClock(mVideoState.videoClock, correctPts);
            }

            //如果是最后一帧，那么就保留，不是就mark read.
            if (mVideoState.status == START && !avFrame.isEof()) {
                avFrame.markRead();
            }

            mLastVideoComponent = component;
        }
//
        //处理贴纸片段
        List<AVComponent> stickComponents = findComponents(AVComponent.AVComponentType.STICKER, -1);
        for (AVComponent component : stickComponents) {
            if (component.getRender() != null) {
                if (component.isValid(extClk)) {
                    if (mVideoState.status == START) {
                        component.readFrame();
                        component.getRender().render(component.peekFrame());
                    } else if (mVideoState.status == SEEK) {
                        component.seekFrame(extClk);
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
        for (AVComponent component : wordsComponents) {
            if (component.getRender() != null) {
                AVFrame wordFrame = component.peekFrame();
                if (component.isValid(extClk)) {
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

        return delay;

    }

    public void setOnFrameUpdateCallback(EngineCallback callback) {
        mUpdateCallback = callback;
    }

    private void createEngineDaemon() {
        mEngineThread = new HandlerThread("EngineThread");
        mEngineThread.start();
        mEngineHandler = new Handler(mEngineThread.getLooper());
        mEngineHandler.post(() -> {
            mEglHelper = new EglHelper();
            mEglHelper.create(null, EglHelper.GL_VERSION_3);
            mEglHelper.makeCurrent();
            //提前设置默认textureId 0默认值0x0
            GLES30.glBindTexture(GL_TEXTURE_2D, 0);
            GLES30.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 16, 16, 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
            mVideoState.reset();
            boolean isSurfaceReady = false;
            while (mVideoState.status != RELEASE) {
                Log.d(TAG, "Engine callback");
                dumpVideoState();
                while (!mCmdQueue.isEmpty()) {
                    Command command = mCmdQueue.poll();
                    if (command.cmd == Command.Cmd.SURFACE_CREATED) {
                        Log.d(TAG, "Surface SURFACE_CREATED!");
                    } else if (command.cmd == Command.Cmd.SURFACE_CHANGED) {
                        Log.d(TAG, "Surface SURFACE_CHANGED!");
                        mEglHelper.createSurface((Surface) command.args0);
                        mEglHelper.makeCurrent();
                        if (mOesRender == null) {
                            mOesRender = new OESRender();
                            mOesRender.open();
                        }
                        int width = (int) command.args1;
                        int height = (int) command.args2;
                        mOesRender.write(OtherUtils.buildMap("surface_size", new Size(width, height)));
                        List<AVComponent> components = findComponents(AVComponent.AVComponentType.VIDEO, -1);
                        for (AVComponent component : components) {
                            if (component.getRender() != null) {
                                component.getRender().write(OtherUtils.buildMap("surface_size", new Size(width, height)));
                            }
                        }
                        isSurfaceReady = true;
                    } else if (command.cmd == Command.Cmd.SURFACE_DESTROYED) {
                        Log.d(TAG, "Surface SURFACE_DESTROYED!");
                        mEglHelper.destroySurface();
                        mEglHelper.makeCurrent();
                        mOesRender.close();
                        mOesRender = null;
                        isSurfaceReady = false;
                    } else if (command.cmd == Command.Cmd.INIT) {
                        mVideoState.status = INIT;
                    } else if (command.cmd == Command.Cmd.PLAY) {
                        setClock(mVideoState.extClock, getClock(mVideoState.extClock));
                        mVideoState.status = START;
                    } else if (command.cmd == Command.Cmd.PAUSE) {
                        setClock(mVideoState.extClock, getClock(mVideoState.extClock));
                        mVideoState.status = PAUSE;
                    } else if (command.cmd == Command.Cmd.RELEASE) {
                        destroyInternal();
                    } else if (command.cmd == Command.Cmd.SEEK) {
                        long args = (long) command.args0;
                        if (args == SEEK_EXIT && mVideoState.status == SEEK) {
                            mVideoState.status = PAUSE;
                            continue;
                        }

                        if (args == SEEK_ENTER) {//进入Seek模式
                            mVideoState.status = SEEK;
                            continue;
                        }

                        if (mVideoState.status == SEEK) {
                            long seekPositionUS = (long) command.args0;
                            if (seekPositionUS < 0 || seekPositionUS > mVideoState.durationUS) {
                                continue;
                            }
                            mVideoState.isInputEOF = false;
                            mVideoState.isOutputEOF = false;
                            mVideoState.seekPositionUS = (long) command.args0;
                            mVideoState.extClock.seekReq++;
                            mVideoState.audioClock.seekReq = mVideoState.videoClock.seekReq = mVideoState.extClock.seekReq;
                            setClock(mVideoState.extClock, mVideoState.seekPositionUS);
                        }
                    } else if (command.cmd == Command.Cmd.ADD_COM) {
                        AVComponent component = (AVComponent) command.args0;
                        component.lock();
                        component.open();
                        component.unlock();
                        if (component.getType() == AVComponent.AVComponentType.AUDIO) {
                            synchronized (mAudioComponents) {
                                mAudioComponents.add(component);
                                reCalculate();
                            }
                        } else {
                            synchronized (mVideoComponents) {
                                mVideoComponents.add(component);
                                reCalculate();
                            }
                        }
                        if (command.args1 != null) {
                            EngineCallback callback = (EngineCallback) command.args1;
                            callback.onCallback("");
                        }
                    } else if (command.cmd == Command.Cmd.REMOVE_COM) {
                        AVComponent component = (AVComponent) command.args0;
                        component.lock();
                        component.close();
                        component.unlock();
                        if (component.getType() == AVComponent.AVComponentType.AUDIO) {
                            synchronized (mAudioComponents) {
                                mAudioComponents.remove(component);
                                reCalculate();
                            }
                        } else {
                            synchronized (mVideoComponents) {
                                mVideoComponents.remove(component);
                                reCalculate();
                            }
                        }
                        if (command.args1 != null) {
                            EngineCallback callback = (EngineCallback) command.args1;
                            callback.onCallback("");
                        }
                    } else if (command.cmd == Command.Cmd.CHANGE_COM) {
                        LinkedList<AVComponent> components = (LinkedList<AVComponent>) command.args0;
                        Rect src = (Rect) command.args1;
                        Rect dst = (Rect) command.args2;
                        for (AVComponent component : components) {
                            component.lock();
                            //裁剪操作的是file start/end time
                            long duration = component.getClipDuration();
                            float scale = duration * 1.0f / src.width();
                            component.setClipStartTime(component.getClipStartTime() + (long) ((dst.left - src.left) * scale));
                            component.setClipEndTime(component.getClipEndTime() - (long) ((src.right - dst.right) * scale));
                            //重新设置EngineTime
                            component.setEngineEndTime(component.getEngineStartTime() + component.getClipDuration());
                            component.peekFrame().setValid(false);
                            component.unlock();
                        }
                        reCalculate();
                        if (command.args3 != null) {
                            EngineCallback callback = (EngineCallback) command.args3;
                            callback.onCallback("");
                        }
                    } else if (command.cmd == Command.Cmd.COMPOSITE) {
                        EngineCallback callback = (EngineCallback) command.args1;
                        mCompositeCallback = callback;
//                        mVideoState.mTargetPath = (String) command.args0;
                        compositeMp4Internal();
                    } else {
                        Log.d(TAG, "Seek cmd error!");
                    }
                }

                //处理视频
                if (isSurfaceReady) {
                    long delay = onDrawFrame();
                    mEglHelper.swap();
                    if (delay == 0) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            mEglHelper.release();
        });
    }

    private void createAudioDaemon() {
        mAudioThread = new HandlerThread("audioThread");
        mAudioThread.start();
        mAudioHandler = new Handler(mAudioThread.getLooper());
        mAudioHandler.post(() -> {
            mAudioRender = new AudioRender();
            mAudioRender.open();
            while (mVideoState.status != RELEASE) {

                //只有运行时候才需要播放音频
                if (mVideoState.status == START) {
                    long extClk = getMainClock();
                    if (extClk == mVideoState.durationUS) {
                        pause();
                        continue;
                    }
                    List<AVComponent> components = findComponents(AVComponent.AVComponentType.AUDIO, extClk);
                    for (AVComponent audio : components) {
                        audio.lock();
                        boolean needSeek = mVideoState.audioClock.seekReq != mVideoState.audioClock.lastSeekReq
                                || (mLastAudioComponent != audio);
                        if (needSeek) {
                            audio.seekFrame(extClk);
                            mVideoState.audioClock.lastSeekReq = mVideoState.audioClock.seekReq;
                        } else {
                            audio.readFrame();
                        }
                        audio.unlock();
                        AVFrame audioFrame = audio.peekFrame();
                        if (!audioFrame.isValid()) {
                            Log.d(TAG, "WTF???Something I don't understand!");
                            continue;
                        }
                        if (Math.abs(audioFrame.getPts() - extClk) > 100000) {
                            audioFrame.markRead();//掉帧
                            Log.d(TAG, "Drop Audio Frame#" + audioFrame.toString());
                            continue;
                        }
                        if (audio.getRender() != null) {
                            audio.getRender().render(audioFrame);
                        } else {
                            mAudioRender.render(audioFrame);
                        }
                        setClock(mVideoState.audioClock, audioFrame.getPts());
                        audioFrame.markRead();
                        mLastAudioComponent = audio;
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

    //先暂停，然后合成MP4
    public void compositeMp4(String mp4Path, EngineCallback callback) {
        pause();
        Command command = new Command();
        command.cmd = Command.Cmd.COMPOSITE;
        command.args0 = mp4Path;
        command.args1 = callback;
        mCmdQueue.add(command);
    }

    private void compositeMp4Internal() {
        Mp4Composite mp4Composite = new Mp4Composite(this);
        mp4Composite.process(progress -> {
            if (mCompositeCallback != null) {
                mCompositeCallback.onCallback(progress);
            }
        });
    }

    /**
     * 根据类型，时间组合查找组件
     *
     * @param type     ALL默认通过
     * @param position -1默认通过
     * @return 目标组件
     */

    public List<AVComponent> findComponents(AVComponent.AVComponentType type, long position) {
        List<AVComponent> mTargetComponents = new LinkedList<>();
        mTargetComponents.clear();
        if (type == AVComponent.AVComponentType.AUDIO) {
            synchronized (mAudioComponents) {
                for (AVComponent component : mAudioComponents) {
                    if (type == AVComponent.AVComponentType.ALL || (component.getType() == type &&
                            (position == -1 || component.isValid(position)))) {
                        mTargetComponents.add(component);
                    }
                }
            }
        } else {
            synchronized (mVideoComponents) {
                for (AVComponent component : mVideoComponents) {
                    if (type == AVComponent.AVComponentType.ALL || (component.getType() == type &&
                            (position == -1 || component.isValid(position)))) {
                        mTargetComponents.add(component);
                    }
                }
            }
        }
        return mTargetComponents;
    }

    /**
     * 添加组件
     *
     * @param avComponent
     */
    public void addComponent(AVComponent avComponent, EngineCallback engineCallback) {
        Command command = new Command();
        command.cmd = Command.Cmd.ADD_COM;
        command.args0 = avComponent;
        command.args1 = engineCallback;
        mCmdQueue.add(command);
    }

    /**
     * 修改组件时间信息
     *
     * @param avComponent    目标组件
     * @param src            原始Rect
     * @param dst            目的Rect
     * @param engineCallback 回调
     */
    public void changeComponent(LinkedList<AVComponent> avComponent, Rect src, Rect dst, EngineCallback engineCallback) {
        Command command = new Command();
        command.cmd = Command.Cmd.CHANGE_COM;
        command.args0 = avComponent;
        command.args1 = src;
        command.args2 = dst;
        command.args3 = engineCallback;
        mCmdQueue.add(command);
    }

    /**
     * 删除组件
     *
     * @param avComponent
     */
    public void removeComponent(AVComponent avComponent) {
        Command command = new Command();
        command.cmd = Command.Cmd.REMOVE_COM;
        command.args0 = avComponent;
        mCmdQueue.add(command);
    }

    /**
     * 计算总时长US.
     */
    private void reCalculate() {
        mVideoState.durationUS = 0;
        for (AVComponent component : mVideoComponents) {
            mVideoState.durationUS = Math.max(component.getEngineEndTime(), mVideoState.durationUS);
        }
        for (AVComponent component : mAudioComponents) {
            mVideoState.durationUS = Math.max(component.getEngineEndTime(), mVideoState.durationUS);
        }
    }

    private void surfaceCreated(Surface surface) {
        Command command = new Command();
        command.cmd = Command.Cmd.SURFACE_CREATED;
        mCmdQueue.add(command);
    }

    private void surfaceChanged(Surface surface, int width, int height) {
        Command command = new Command();
        command.cmd = Command.Cmd.SURFACE_CHANGED;
        command.args0 = surface;
        command.args1 = width;
        command.args2 = height;
        mCmdQueue.add(command);
    }

    private void surfaceDestroyed() {
        Command command = new Command();
        command.cmd = Command.Cmd.SURFACE_DESTROYED;
        mCmdQueue.add(command);
    }

    public void start() {
        Command command = new Command();
        command.cmd = Command.Cmd.PLAY;
        mCmdQueue.add(command);
    }

    public void pause() {
        Command command = new Command();
        command.cmd = Command.Cmd.PAUSE;
        mCmdQueue.add(command);
    }

    public void togglePlayPause() {
        if (mVideoState.status == START) {
            pause();
        } else {
            start();
        }
    }

    private static final int SEEK_ENTER = -1;
    private static final int SEEK_EXIT = -2;

    /**
     * SEEK部分
     * fastSeek用于快速seek某个位置，自动进退seek模式
     * seek(true)进入seek模式
     * seek(false)退出seek模式
     */

    public void fastSeek(long position) {
        seek(true);
        seek(position);
        seek(false);
    }

    public void seek(long position) {
        Command command = new Command();
        command.cmd = Command.Cmd.SEEK;
        command.args0 = position;
        mCmdQueue.add(command);
    }

    public void seek(boolean status) {
        Command command = new Command();
        command.cmd = Command.Cmd.SEEK;
        command.args0 = (long) (status ? SEEK_ENTER : SEEK_EXIT);
        mCmdQueue.add(command);
    }

    public void create() {
        createEngineDaemon();
        createAudioDaemon();
    }

    public void release() {

        Log.d(TAG, "release()");

        Command command = new Command();
        command.cmd = Command.Cmd.RELEASE;
        mCmdQueue.offer(command);

        if (mAudioHandler != null && mAudioThread != null) {
            mAudioHandler.getLooper().quit();
            try {
                mAudioThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "mAudioThread quit");
        }

        if (mEngineHandler != null && mEngineThread != null) {
            mEngineHandler.getLooper().quitSafely();
            try {
                mEngineThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "mEngineThread quit");
        }

        AVEngine.gAVEngine = null;//...貌似不是很合适。。

    }

    private void destroyInternal() {
        mVideoState.status = RELEASE;
        synchronized (mAudioComponents) {
            for (AVComponent avComponent : mAudioComponents) {
                avComponent.close();
            }
            mAudioComponents.clear();
        }

        synchronized (mVideoComponents) {
            for (AVComponent avComponent : mVideoComponents) {
                avComponent.close();
            }
            mVideoComponents.clear();
        }
    }

    public VideoState getVideoState() {
        return mVideoState;
    }

    public EglHelper getEglHelper() {
        return mEglHelper;
    }

    private void onFrameUpdate() {
        if (mUpdateCallback != null) {
            mUpdateCallback.onCallback("");
        }
    }

    private Map<String, Object> mConfigs = new HashMap<>();

    private Map<String, Object> buildConfig(Object... args) {
        if (args.length % 2 != 0) {
            return null;
        }
        for (int i = 0; i < args.length / 2; i++) {
            mConfigs.put((String) args[2 * i], args[2 * i + 1]);
        }
        return mConfigs;
    }


    private void dumpVideoState() {
//        Log.d(TAG_VIDEO_STATE, mVideoState.toString());
//        for (AVComponent avComponent : mAudioComponents) {
//            Log.d(TAG_COMPONENT, avComponent.toString());
//        }
    }

}
