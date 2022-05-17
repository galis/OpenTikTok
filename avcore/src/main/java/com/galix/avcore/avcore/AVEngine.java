package com.galix.avcore.avcore;

import android.content.Context;
import android.graphics.Rect;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewTreeObserver;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

import com.galix.avcore.render.AudioRender;
import com.galix.avcore.render.IVideoRender;
import com.galix.avcore.render.OESRender;
import com.galix.avcore.render.PagRender;
import com.galix.avcore.render.ScreenRender;
import com.galix.avcore.render.TransactionRender;
import com.galix.avcore.render.filters.GLTexture;
import com.galix.avcore.util.EglHelper;
import com.galix.avcore.util.LogUtil;
import com.galix.avcore.util.MathUtils;
import com.galix.avcore.util.Mp4Composite;
import com.galix.avcore.util.TimeUtils;

import org.libpag.PAGComposition;
import org.libpag.PAGLayer;
import org.libpag.PAGPlayer;
import org.libpag.PAGSurface;
import org.opencv.core.Mat;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import static android.opengl.GLES20.GL_BLEND;
import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_FUNC_ADD;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.GL_SRC_ALPHA;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;
import static android.opengl.GLES20.glTexParameterf;
import static android.opengl.GLES20.glTexParameteri;
import static com.galix.avcore.avcore.AVEngine.VideoState.VideoStatus.INIT;
import static com.galix.avcore.avcore.AVEngine.VideoState.VideoStatus.PAUSE;
import static com.galix.avcore.avcore.AVEngine.VideoState.VideoStatus.RELEASE;
import static com.galix.avcore.avcore.AVEngine.VideoState.VideoStatus.SEEK;
import static com.galix.avcore.avcore.AVEngine.VideoState.VideoStatus.START;
import static com.galix.avcore.util.MathUtils.calMat;

/**
 * 视频引擎
 */
public class AVEngine {

    static {
        System.loadLibrary("arcore");
    }

    private static final int PLAY_GAP = 10;//MS
    private static AVEngine gAVEngine;
    private VideoState mVideoState;
    private EglHelper mEglHelper;
    private SurfaceView mSurfaceView;
    private EngineCallback mUpdateCallback, mCompositeCallback;
    private AVComponent mLastVideoComponent;
    private AVComponent mLastAudioComponent;
    private AudioRender mAudioRender;
    private IVideoRender mOesRender;
    private BlockingQueue<Command> mCmdQueue;
    private GLTexture lastTexture = null;
    private IVideoRender screenRender;
    private IVideoRender pagRender;
    private AVFrame screenFrame;
    private Size mSurfaceViewSize = new Size(1080, 350);

    //Pag
    private GLTexture mPagTexture = new GLTexture(0, false);
    private final Object mPagDecodeSync = new Object();
    private PAGPlayer mPagPlayer;
    private PAGComposition mPagComposition;
    private AVFrame mPagFrame;

    public interface EngineCallback {
        void onCallback(Object... args1);
    }

    private AVEngine() {
        mCmdQueue = new LinkedBlockingQueue<>();
        mVideoState = new VideoState();
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
            COMPOSITE,
            RECORD;
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
            final StringBuilder sb = new StringBuilder("{");
            sb.append("\"speed\":")
                    .append(speed);
            sb.append(",\"lastUpdate\":")
                    .append(lastUpdate);
            sb.append(",\"delta\":")
                    .append(delta);
            sb.append(",\"lastSeekReq\":")
                    .append(lastSeekReq);
            sb.append(",\"seekReq\":")
                    .append(seekReq);
            sb.append('}');
            return sb.toString();
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
        public long displaySwapCount;
        public long seekPositionUS;
        public long videoDuration;
        public long audioDuration;
        public long durationUS;//视频总时长 us
        public int bgColor;
        public int compositeGop;
        public int compositeFrameRate;
        public int compositeAb;
        public int compositeVb;
        public boolean hasAudio;
        public boolean hasVideo;
        public boolean readyAudio;//合成视频用
        public boolean readyVideo;//合成视频用
        public boolean isSurfaceReady;
        public boolean isEdit;//编辑组件状态
        public long drawClock;//需要绘制video
        public int compositeHeight;//默认1080p 可以选择720 360
        public String compositePath;//合成视频路径
        public Size compositeSize;//合成视频目标宽高
        public Size canvasSize;//surface size
        public VideoStatus status;//播放状态
        public String canvasType;
        public Clock videoClock;
        public Clock extClock;
        public Clock audioClock;
        public Mat canvasMat;
        public Mat compositeMat;
        public AVComponent editComponent;
        public final ReentrantLock stateLock = new ReentrantLock();
        public List<AVComponent> tranComponents = new LinkedList<>();//转场
        public List<AVComponent> videoComponents = new LinkedList<>();//视频轨道
        public List<AVComponent> audioComponents = new LinkedList<>();//音频轨道
        //onDrawFrame方法用到的
        public List<AVComponent> drawPagComponents = new LinkedList<>();//需要绘制pag
        public List<AVComponent> drawVideoComponents = new LinkedList<>();//需要绘制video

        public VideoState() {
            reset();
        }

        public void reset() {
            videoClock = new Clock();
            audioClock = new Clock();
            extClock = new Clock();
            drawClock = 0;
            durationUS = 0;
            videoDuration = 0;
            audioDuration = 0;
            isInputEOF = false;
            isOutputEOF = false;
            compositeHeight = 1080;
            displaySwapCount = 0;
            isEdit = false;
            seekPositionUS = Long.MAX_VALUE;
            status = VideoStatus.INIT;
            isSurfaceReady = false;
            drawPagComponents.clear();
            drawVideoComponents.clear();
            tranComponents.clear();
            videoComponents.clear();
            audioComponents.clear();
        }

        public void lock() {
            stateLock.lock();
        }

        public void unlock() {
            stateLock.unlock();
        }

        public List<AVComponent> findComponents(AVComponent.AVComponentType type, long position) {
            List<AVComponent> mTargetComponents = new LinkedList<>();
            mTargetComponents.clear();
            if (type == AVComponent.AVComponentType.AUDIO) {
                lock();
                for (AVComponent component : audioComponents) {
                    if (type == AVComponent.AVComponentType.ALL || (component.getType() == type &&
                            (position == -1 || component.isValid(position)))) {
                        mTargetComponents.add(component);
                    }
                }
                unlock();
            } else {
                lock();
                for (AVComponent component : type == AVComponent.AVComponentType.TRANSACTION ? tranComponents : videoComponents) {
                    if (type == AVComponent.AVComponentType.ALL || (component.getType() == type &&
                            (position == -1 || component.isValid(position)))) {
                        mTargetComponents.add(component);
                    }
                }
                unlock();
            }
            return mTargetComponents;
        }

        @Override
        public String toString() {
            return "VideoState{\n" +
                    "\tisInputEOF=" + isInputEOF + ",\n" +
                    "\tisOutputEOF=" + isOutputEOF + ",\n" +
                    "\tdisplaySwapCount=" + displaySwapCount + ",\n" +
                    "\tseekPositionUS=" + seekPositionUS + ",\n" +
                    "\tvideoDuration=" + videoDuration + ",\n" +
                    "\taudioDuration=" + audioDuration + ",\n" +
                    "\tdurationUS=" + durationUS + ",\n" +
                    "\tbgColor=" + bgColor + ",\n" +
                    "\tcompositeGop=" + compositeGop + ",\n" +
                    "\tcompositeFrameRate=" + compositeFrameRate + ",\n" +
                    "\tcompositeAb=" + compositeAb + ",\n" +
                    "\tcompositeVb=" + compositeVb + ",\n" +
                    "\thasAudio=" + hasAudio + ",\n" +
                    "\thasVideo=" + hasVideo + ",\n" +
                    "\treadyAudio=" + readyAudio + ",\n" +
                    "\treadyVideo=" + readyVideo + ",\n" +
                    "\tisSurfaceReady=" + isSurfaceReady + ",\n" +
                    "\tisEdit=" + isEdit + ",\n" +
                    "\tdrawClock=" + drawClock + ",\n" +
                    "\tcompositePath='" + compositePath + '\'' + ",\n" +
                    "\tcompositeSize=" + compositeSize + ",\n" +
                    "\tcanvasSize=" + canvasSize + ",\n" +
                    "\tstatus=" + status + ",\n" +
                    "\tcanvasType='" + canvasType + '\'' + ",\n" +
                    "\tvideoClock=" + videoClock + ",\n" +
                    "\textClock=" + extClock + ",\n" +
                    "\taudioClock=" + audioClock + ",\n" +
                    "\tcanvasMat=" + canvasMat.dump() + ",\n" +
                    "\tcompositeMat=" + compositeMat.dump() + ",\n" +
                    "\teditComponent=" + editComponent + ",\n" +
                    "\tstateLock=" + stateLock + ",\n" +
                    "\ttranComponents=" + tranComponents + ",\n" +
                    "\tvideoComponents=" + videoComponents + ",\n" +
                    "\taudioComponents=" + audioComponents + ",\n" +
                    "\tdrawPagComponents=" + drawPagComponents + ",\n" +
                    "\tdrawVideoComponents=" + drawVideoComponents + ",\n" +
                    '}' + "\n";
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
            setMainClock(mVideoState.durationUS);
            return mVideoState.durationUS;
        }
        return currentClk;
    }

    public void setMainClock(long time) {
        setClock(mVideoState.extClock, time);
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
                AVEngine.this.surfaceCreated(holder.getSurface());
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                AVEngine.this.surfaceChanged(holder, width, height);
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                AVEngine.this.surfaceDestroyed(holder);
            }
        });
        mSurfaceView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mSurfaceViewSize = new Size(mSurfaceView.getMeasuredWidth(), mSurfaceView.getMeasuredHeight());
                mSurfaceView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
    }

    private Size calCanvasSize(String text, Size videoSize) {
        int width = mSurfaceViewSize.getWidth();
        int height = mSurfaceViewSize.getHeight();
        if (text.equalsIgnoreCase("原始")) {
            height = (int) (width * videoSize.getHeight() * 1.0f /
                    videoSize.getWidth());
            if (height > mSurfaceViewSize.getHeight()) {
                height = mSurfaceViewSize.getHeight();
                width = (int) (height * videoSize.getWidth() * 1.0f / videoSize.getHeight());
            }
        } else if (text.equalsIgnoreCase("4:3")) {
            height = (int) (width * 3.0f / 4.0f);
            if (height > mSurfaceViewSize.getHeight()) {
                height = mSurfaceViewSize.getHeight();
                width = (int) (height * 4.f / 3.f);
            }
        } else if (text.equalsIgnoreCase("3:4")) {
            width = (int) (height * 3.f / 4.0f);
        } else if (text.equalsIgnoreCase("1:1")) {
            width = height;
        } else if (text.equalsIgnoreCase("16:9")) {
            height = (int) (width * 9.0f / 16.0f);
            if (height > mSurfaceViewSize.getHeight()) {
                height = mSurfaceViewSize.getHeight();
                width = (int) (height * 16.f / 9.f);
            }
        } else if (text.equalsIgnoreCase("9:16")) {
            width = (int) (height * 9.0f / 16.0f);
        }
        return new Size(width, height);
    }

    /**
     * 设置画布大小
     */
    public void setCanvasType(String type, EngineCallback engineCallback) {
        if (type == null) return;
        if (mVideoState.videoComponents.isEmpty()) {
            return;
        }
        Size video0Size = ((IVideo) mVideoState.videoComponents.get(0)).getVideoSize();
        Size targetSize = calCanvasSize(type, video0Size);
        mSurfaceView.getLayoutParams().width = targetSize.getWidth();
        mSurfaceView.getLayoutParams().height = targetSize.getHeight();
        mSurfaceView.post(new Runnable() {
            @Override
            public void run() {
                mSurfaceView.requestLayout();
            }
        });
        mVideoState.lock();
        mVideoState.canvasType = type;
        mVideoState.canvasSize = targetSize;
        mVideoState.canvasMat = calMat(video0Size, mVideoState.canvasSize);
        mVideoState.compositeSize = MathUtils.calCompositeSize(mVideoState.canvasType, video0Size, mVideoState.compositeHeight);
        mVideoState.compositeMat = calMat(video0Size, mVideoState.compositeSize);
        mVideoState.unlock();
        if (engineCallback != null) {
            engineCallback.onCallback(targetSize);
        }
    }

    /**
     * 设置背景颜色
     *
     * @param color
     */
    public void setBgColor(int color) {
        mVideoState.lock();
        mVideoState.bgColor = color;
        mVideoState.unlock();
        if (mVideoState.status == PAUSE) {
            fastSeek(getMainClock());
        }
    }

    public void updateEdit(AVComponent avComponent, boolean isEdit) {
        mVideoState.lock();
        mVideoState.isEdit = isEdit;
        mVideoState.editComponent = avComponent;
        mVideoState.unlock();
    }

    //return delay;
    //>=0代表延迟，已经渲染.
    //<0代表没有渲染。
    public long onDrawFrame() {
        if (!checkSeekAndReadyForRender()) {
            return 0;
        }
        TimeUtils.RecordStart("renderVideo");
        renderVideo();
        TimeUtils.RecordEnd("renderVideo");
        TimeUtils.RecordStart("renderPag");
        renderPag();//渲染pag
        TimeUtils.RecordEnd("renderPag");
        TimeUtils.RecordStart("renderScreen");
        renderScreen();//渲染到屏幕
        TimeUtils.RecordEnd("renderScreen");
        renderSticker();//贴纸渲染
        renderWord();//文字渲染
        return renderForDelay();
    }

    private long renderForDelay() {
        boolean needRender = true;
        long delay = 0;
        long mainClock = getMainClock();
        long correctPts = getClock(mVideoState.videoClock);
        if (mVideoState.status == START) {
            delay = Math.max(correctPts - mainClock, 0);
            needRender = correctPts >= mainClock || delay < 50000;//这个阈值。。
        }
        LogUtil.logEngine("delay#" + delay);
        if (!needRender) {
            LogUtil.logEngine("needRender#delay#" + delay);
            return -1L;
        }
        if (delay > 0) {
            try {
                Thread.sleep(delay / 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return delay;
    }

    public void setOnFrameUpdateCallback(EngineCallback callback) {
        mUpdateCallback = callback;
    }

    //seek if need 同时返回是否需要render
    private boolean checkSeekAndReadyForRender() {
        if (mVideoState.status == RELEASE ||
                mVideoState.videoComponents.isEmpty()) {
            return false;
        }

        //获取相关组件
        long mainClk = mVideoState.drawClock = getMainClock();
        if (mainClk >= mVideoState.durationUS) {
            pauseInternal();
            return false;
        }

        mVideoState.drawVideoComponents.clear();
        List<AVComponent> trans = findComponents(AVComponent.AVComponentType.TRANSACTION, mainClk);
        for (AVComponent comm : trans) {
            if (comm.isVisible()) {
                mVideoState.drawVideoComponents.add(comm);
            }
        }
        if (mVideoState.drawVideoComponents.isEmpty()) {
            mVideoState.drawVideoComponents = findComponents(AVComponent.AVComponentType.VIDEO, mainClk);
        }
        if (mVideoState.drawVideoComponents.size() != 1) {//video transaction 在某个时间戳只存在一个组件
            LogUtil.logEngine("videoComponents.size() != 1");
            return false;
        }

        boolean needSeek = mVideoState.videoClock.seekReq != mVideoState.videoClock.lastSeekReq;
        //Pag组件
        synchronized (mPagDecodeSync) {
            mVideoState.drawPagComponents.clear();
            mVideoState.drawPagComponents.addAll(findComponents(AVComponent.AVComponentType.PAG, mainClk));
            if (needSeek) {
                for (AVComponent component : mVideoState.drawPagComponents) {
                    component.lock();
                    component.seekFrame(mainClk);
                    component.unlock();
                }
            }
        }

        for (AVComponent component : mVideoState.drawVideoComponents) {
            component.lock();
            component.seekFrame(mainClk);
            component.unlock();
        }
        mVideoState.videoClock.lastSeekReq = mVideoState.videoClock.seekReq;
        return true;
    }

    private void renderVideo() {
        AVComponent mainComponent = mVideoState.drawVideoComponents.get(0);
        AVFrame mainVideoFrame = mainComponent.peekFrame();
        if (!mainVideoFrame.isValid()) {
            LogUtil.log("VIDEO#WTF???Something I don't understand!");
            return;
        }
        long correctPts = mainVideoFrame.getPts();
        if (mainComponent.getRender() != null) {
            if (!mainComponent.getRender().isOpen()) {
                mainComponent.getRender().open();
                mainComponent.getRender().write(buildConfig("surface_size", mVideoState.canvasSize));
            }
            mainComponent.getRender().render(mainVideoFrame);
            lastTexture = ((IVideoRender) mainComponent.getRender()).getOutTexture();
        } else {
            mainVideoFrame.setTextColor(mVideoState.bgColor);
            mOesRender.render(mainVideoFrame);
            lastTexture = mOesRender.getOutTexture();
        }

        //如果是暂停状态，那么就保留，不是就mark read.
        if (mVideoState.status == START && !mainVideoFrame.isEof()) {
            mainVideoFrame.markRead();
        }

        setClock(mVideoState.videoClock, correctPts);
        mLastVideoComponent = mainComponent;
    }

    private void renderPag() {
        if (mVideoState.drawPagComponents.isEmpty()) {
            return;
        }
        //通知解码线程渲染
        synchronized (mPagDecodeSync) {
            mPagDecodeSync.notifyAll();
        }

        if (pagRender == null) {
            pagRender = new PagRender();
            pagRender.open();
        }
        if (mPagFrame == null) {
            mPagFrame = new AVFrame();
            mPagFrame.setTexture(mPagTexture);
            mPagTexture.setMatrix(MathUtils.mIdentityMat);
        }
        GLES30.glEnable(GL_BLEND);
        GLES30.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glBlendEquation(GL_FUNC_ADD);
        pagRender.render(mPagFrame);
        GLES30.glDisable(GL_BLEND);
    }

    private void renderScreen() {
        //渲染到屏幕
        if (screenFrame == null) {
            screenFrame = new AVFrame();
        }
        screenFrame.setTexture(lastTexture);
        screenRender.write(TimeUtils.BuildMap("textureMat", mVideoState.canvasMat));
        screenRender.write(TimeUtils.BuildMap("bgColor", MathUtils.Int2Vec3(mVideoState.bgColor)));
        screenRender.render(screenFrame);
    }

    private void renderSticker() {
        //处理贴纸片段
        long extClk = getMainClock();
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
    }

    private void renderWord() {
        long extClk = getMainClock();
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
    }

    private void createEngineDaemon() {
        ThreadManager.getInstance().createThread("EngineThread", () -> {
            mEglHelper = new EglHelper();
            mEglHelper.create(null, EglHelper.GL_VERSION_3);
            mEglHelper.makeCurrent();
            //提前设置默认textureId 0默认值0x0
            GLES30.glBindTexture(GL_TEXTURE_2D, 0);
            GLES30.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 16, 16, 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
            mVideoState.reset();

            //pag
            createPagDaemon();

            while (mVideoState.status != RELEASE) {
                while (true) {
                    Command command = mCmdQueue.poll();
                    if (command == null) break;
                    if (command.cmd == Command.Cmd.SURFACE_CREATED) {
                        LogUtil.log(LogUtil.ENGINE_TAG + "SURFACE_CREATED!");
                    } else if (command.cmd == Command.Cmd.SURFACE_CHANGED) {
                        handleSurfaceChange(command);
                    } else if (command.cmd == Command.Cmd.SURFACE_DESTROYED) {
                        handleDestroy();
                    } else if (command.cmd == Command.Cmd.INIT) {
                        mVideoState.status = INIT;
                    } else if (command.cmd == Command.Cmd.PLAY) {
                        playInternal();
                    } else if (command.cmd == Command.Cmd.PAUSE) {
                        pauseInternal();
                    } else if (command.cmd == Command.Cmd.RELEASE) {
                        destroyInternal();
                    } else if (command.cmd == Command.Cmd.SEEK) {
                        handleSeek(command);
                    } else if (command.cmd == Command.Cmd.ADD_COM) {
                        handleAddComm(command);
                    } else if (command.cmd == Command.Cmd.REMOVE_COM) {
                        handleRemoveComm(command);
                    } else if (command.cmd == Command.Cmd.CHANGE_COM) {
                        handleChangeComm(command);
                    } else if (command.cmd == Command.Cmd.COMPOSITE) {
                        pauseInternal();
                        handleDestroy();
                        mCompositeCallback = (EngineCallback) command.args1;
                        compositeInternal();
                    } else {
                        LogUtil.log(LogUtil.ENGINE_TAG + "Seek cmd error!");
                    }
                }

                //处理视频
                boolean needRender = mVideoState.isSurfaceReady && (mVideoState.displaySwapCount < 1
                        || mVideoState.status == START);
                LogUtil.logEngine("needRender#" + needRender);
                if (needRender) {
                    TimeUtils.RecordStart("onDrawFrame");
                    long delay = onDrawFrame();
                    TimeUtils.RecordEnd("onDrawFrame");
                    if (delay >= 0) {
                        //swap两次才能在屏幕显示...
                        mEglHelper.swap();
                        mVideoState.displaySwapCount++;
                        if (mVideoState.displaySwapCount == Long.MAX_VALUE) {
                            mVideoState.displaySwapCount = 1;
                        }
                    }
                } else {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                renderPostIfNeed();
            }
            mEglHelper.release();
        });
    }

    private void handleSurfaceChange(Command command) {
        LogUtil.log(LogUtil.ENGINE_TAG + "SURFACE_CHANGED!#" + command.args0.toString());
        if (!mEglHelper.createSurface((Surface) command.args0)) {
            LogUtil.log(LogUtil.ENGINE_TAG + "createSurface()#Error!!");
            return;
        }
        if (!mEglHelper.makeCurrent()) {
            LogUtil.log(LogUtil.ENGINE_TAG + "makeCurrent()#Error!!");
            return;
        }
        if (mOesRender == null) {
            mOesRender = new OESRender();
            mOesRender.open();
            screenRender = new ScreenRender();
            screenRender.open();
        }
        int width = (int) command.args1;
        int height = (int) command.args2;
        mOesRender.write(TimeUtils.BuildMap("surface_size", new Size(width, height)));
        screenRender.write(TimeUtils.BuildMap("surface_size", new Size(width, height)));
        List<AVComponent> components = findComponents(AVComponent.AVComponentType.VIDEO, -1);
        for (AVComponent component : components) {
            if (component.getRender() != null) {
                component.getRender().write(TimeUtils.BuildMap("surface_size", new Size(width, height)));
            }
        }
        mVideoState.isSurfaceReady = true;
    }

    private void handleSeek(Command command) {
        long args = (long) command.args0;
        if (args == SEEK_EXIT && mVideoState.status == SEEK) {
            pauseInternal();
            return;
        }

        if (args == SEEK_ENTER) {//进入Seek模式
            mVideoState.status = SEEK;
            return;
        }

        if (mVideoState.status == SEEK) {
            long seekPositionUS = (long) command.args0;
            if (seekPositionUS < 0 || seekPositionUS > mVideoState.durationUS) {
                return;
            }
            mVideoState.isInputEOF = false;
            mVideoState.isOutputEOF = false;
            mVideoState.seekPositionUS = (long) command.args0;
            mVideoState.extClock.seekReq++;
            mVideoState.audioClock.seekReq = mVideoState.videoClock.seekReq = mVideoState.extClock.seekReq;
            mVideoState.displaySwapCount = 0;
            setMainClock(mVideoState.seekPositionUS);
        }
    }

    private void handleChangeComm(Command command) {
        AVComponent component = (AVComponent) command.args0;
        Map<String, Object> config = (Map<String, Object>) command.args1;
        component.lock();
        if (component instanceof AVTransaction) {//改变转场组件相关信息
            component.write(config);
        } else {
            Rect src = (Rect) config.get("comm_src");
            Rect dst = (Rect) config.get("comm_dst");
            //裁剪操作的是file start/end time
            long duration = component.getClipDuration();
            float scale = duration * 1.0f / src.width();
            component.setClipStartTime(component.getClipStartTime() + (long) ((dst.left - src.left) * scale));
            component.setClipEndTime(component.getClipEndTime() - (long) ((src.right - dst.right) * scale));
            //重新设置EngineTime
            component.setEngineEndTime(component.getEngineStartTime() + component.getClipDuration());
            component.peekFrame().setValid(false);
        }
        component.unlock();
        reCalculate();
        if (command.args2 != null) {
            EngineCallback callback = (EngineCallback) command.args2;
            callback.onCallback("");
        }
    }

    private void handleRemoveComm(Command command) {
        AVComponent component = (AVComponent) command.args0;
        component.lock();
        component.close();
        component.unlock();
        if (component.getType() == AVComponent.AVComponentType.AUDIO) {
            mVideoState.lock();
            mVideoState.audioComponents.remove(component);
            reCalculate();
            mVideoState.unlock();
        } else {
            mVideoState.lock();
            if (component instanceof AVVideo) {
                mVideoState.videoComponents.remove(component);
                reCalculate();
            } else {
                mVideoState.tranComponents.remove(component);
            }
            mVideoState.unlock();
        }
        if (command.args1 != null) {
            EngineCallback callback = (EngineCallback) command.args1;
            callback.onCallback("");
        }
    }

    private void handleAddComm(Command command) {
        AVComponent component = (AVComponent) command.args0;
        component.lock();
        if (component.getEngineStartTime() == -1) {
            if (component instanceof AVVideo) {
                component.setEngineStartTime(mVideoState.videoDuration);
            } else if (component instanceof AVAudio) {
                component.setEngineStartTime(mVideoState.audioDuration);
            } else {
                LogUtil.logEngine("handleAddComm#component.getEngineStartTime() == -1#No Support");//TODO
            }
        }
        component.open();
        component.unlock();

        //简单处理下贴纸和文字组件，不能超过视频duration,后面可以扩展
        if (component instanceof AVSticker || component instanceof AVWord) {
            component.setEngineEndTime(Math.min(component.getEngineEndTime(), getVideoState().durationUS));
        }

        if (component instanceof AVVideo) {
            List<AVComponent> components = findComponents(AVComponent.AVComponentType.VIDEO, -1);
            if (!components.isEmpty()) {
                AVVideo lastVideo = (AVVideo) components.get(components.size() - 1);
                AVTransaction avTransaction = new AVTransaction(
                        TimeUtils.leftTime(
                                TimeUtils.quzheng(lastVideo.getEngineEndTime())),//这里总是期待tran1 duration不小于1s
                        lastVideo,
                        (AVVideo) component,
                        new TransactionRender()
                );
                avTransaction.setVisible(false);
                avTransaction.open();
                mVideoState.tranComponents.add(avTransaction);
            }
        }
        if (component.getType() == AVComponent.AVComponentType.AUDIO) {
            mVideoState.lock();
            mVideoState.audioComponents.add(component);
            reCalculate();
            mVideoState.unlock();
        } else {
            mVideoState.lock();
            mVideoState.videoComponents.add(component);
            reCalculate();
            mVideoState.unlock();
        }
        if (mVideoState.status == PAUSE) {
            fastSeek(getMainClock());
        }
        if (command.args1 != null) {
            EngineCallback callback = (EngineCallback) command.args1;
            callback.onCallback("");
        }
    }

    private void createAudioDaemon() {
        ThreadManager.getInstance().createThread("AudioThread", new Runnable() {
            @Override
            public void run() {
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
                        if (components.isEmpty()) continue;
                        AVComponent audio = components.get(0);
                        if (!audio.isOpen()) continue;
                        audio.lock();
                        audio.seekFrame(extClk);
                        audio.unlock();
                        AVFrame audioFrame = audio.peekFrame();
                        LogUtil.logEngine(audio.toString());
                        if (!audioFrame.isValid()) {
                            LogUtil.log(LogUtil.ENGINE_TAG + "#AudioThread#WTF???Something I don't understand!");
                            continue;
                        }
                        if (Math.abs(audioFrame.getPts() - extClk) > 100000) {
                            audioFrame.markRead();//掉帧
                            LogUtil.log(LogUtil.ENGINE_TAG + "#AudioThread#Drop Audio Frame#" + audioFrame.toString());
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
                    } else {
                        try {
                            Thread.sleep(PLAY_GAP);//TODO
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                mAudioRender.close();
            }
        });
    }

    private void createPagDaemon() {
        EGLContext mCurrentContext = EGL14.eglGetCurrentContext();
        ThreadManager.getInstance().createThread("PagThread", new Runnable() {
            @Override
            public void run() {
                EglHelper eglHelper = new EglHelper();
                eglHelper.create(mCurrentContext, EglHelper.GL_VERSION_3);
                eglHelper.makeCurrent();

                int width = 1280;
                int height = 720;

                //生成共享Texture
                //新建Texture，该Texture可以被解码线程通用.
                GLES30.glGenTextures(1, mPagTexture.idAsBuf());
                GLES30.glBindTexture(GL_TEXTURE_2D, mPagTexture.id());
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
                GLES30.glBindTexture(GL_TEXTURE_2D, 0);

                if (mPagPlayer == null) {
                    mPagPlayer = new PAGPlayer();
                }
                if (mPagComposition == null) {
                    mPagComposition = PAGComposition.Make(width, height);
                }
                mPagPlayer.setComposition(mPagComposition);
                mPagPlayer.setSurface(PAGSurface.FromTexture(mPagTexture.id(), mPagComposition.width(), mPagComposition.height()));
                while (mVideoState.status != RELEASE) {
                    //渲染pag组件
                    synchronized (mPagDecodeSync) {
                        for (int i = 0; i < mPagComposition.numChildren(); i++) {
                            mPagComposition.getLayerAt(i).setVisible(false);
                        }
                        LogUtil.logEngine("decode_pag#size" + mVideoState.drawPagComponents.size());
                        for (AVComponent avPag : mVideoState.drawPagComponents) {
                            if (!avPag.peekFrame().isValid()) {
                                avPag.lock();
                                TimeUtils.RecordStart("decode_pag#avPag.readFrame");
                                avPag.readFrame();
                                TimeUtils.RecordEnd("decode_pag#avPag.readFrame");
                                avPag.unlock();
                            }
                            avPag.peekFrame().markRead();
                            PAGLayer layer = (PAGLayer) avPag.peekFrame().getExt();
                            if (layer != null) {
                                if (!mPagComposition.contains(layer)) {
                                    mPagComposition.addLayer(layer);
                                }
                                layer.setVisible(true);
                            }
                        }
                    }
                    TimeUtils.RecordStart("decode_pag#mPagPlayer.flush");
                    mPagPlayer.flush();
                    TimeUtils.RecordEnd("decode_pag#mPagPlayer.flush");
                    synchronized (mPagDecodeSync) {
                        try {
                            mPagDecodeSync.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                eglHelper.release();
                LogUtil.log(LogUtil.ENGINE_TAG + "AVPag#DecodeThread exit");
            }
        });
    }

    //先暂停，然后合成MP4
    public void compositeMp4(String mp4Path, EngineCallback callback) {
        Command command = new Command();
        command.cmd = Command.Cmd.COMPOSITE;
        command.args0 = mp4Path;
        command.args1 = callback;
        mCmdQueue.add(command);
    }

    public void record(boolean isRecord, EngineCallback callback) {
        Command command = new Command();
        command.cmd = Command.Cmd.RECORD;
        command.args0 = isRecord;
        command.args1 = callback;
        mCmdQueue.add(command);
    }

    private void compositeInternal() {
        Mp4Composite mp4Composite = new Mp4Composite(this);
        mp4Composite.process(new Mp4Composite.CompositeCallback() {
            @Override
            public void handle(int progress) {
                if (mCompositeCallback != null) {
                    mCompositeCallback.onCallback(progress);
                }
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
            mVideoState.lock();
            for (AVComponent component : mVideoState.audioComponents) {
                if (type == AVComponent.AVComponentType.ALL || (component.getType() == type &&
                        (position == -1 || component.isValid(position)))) {
                    mTargetComponents.add(component);
                }
            }
            mVideoState.unlock();
        } else {
            mVideoState.lock();
            for (AVComponent component : type == AVComponent.AVComponentType.TRANSACTION ? mVideoState.tranComponents : mVideoState.videoComponents) {
                if (type == AVComponent.AVComponentType.ALL || (component.getType() == type &&
                        (position == -1 || component.isValid(position)))) {
                    mTargetComponents.add(component);
                }
            }
            mVideoState.unlock();
        }
        return mTargetComponents;
    }

    public AVPag playPag(Context context, String path) {
        AVPag pag = new AVPag(context.getAssets(), path, getMainClock(), new PagRender());
        addComponent(pag, null);
        return pag;
    }

    public AVPag playPag(AVPag avPag) {
        if (!avPag.isOpen()) {
            addComponent(avPag, null);
        } else {
            avPag.lock();
            avPag.setEngineStartTime(getMainClock() + 60000);
            avPag.setEngineEndTime(avPag.getEngineStartTime() + avPag.getClipDuration());
            avPag.seekFrame(avPag.getEngineStartTime());
            avPag.unlock();
        }
        return avPag;
    }

    /**
     * 添加组件
     *
     * @param avComponent
     */
    public void addComponent(AVComponent avComponent, EngineCallback engineCallback) {
        LogUtil.log(LogUtil.ENGINE_TAG + LogUtil.MAIN_TAG + "addComponent()");
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
     * @param config         设置
     * @param engineCallback 回调
     */
    public void changeComponent(AVComponent avComponent, Map<String, Object> config, EngineCallback engineCallback) {
        Command command = new Command();
        command.cmd = Command.Cmd.CHANGE_COM;
        command.args0 = avComponent;
        command.args1 = config;
        command.args2 = engineCallback;
        mCmdQueue.add(command);
    }

    /**
     * 删除组件
     *
     * @param avComponent
     */
    public void removeComponent(AVComponent avComponent) {
        LogUtil.log(LogUtil.ENGINE_TAG + LogUtil.MAIN_TAG + "removeComponent()");
        Command command = new Command();
        command.cmd = Command.Cmd.REMOVE_COM;
        command.args0 = avComponent;
        mCmdQueue.add(command);
    }

    /**
     * 计算总时长US.
     */
    private void reCalculate() {
        LogUtil.log(LogUtil.ENGINE_TAG + LogUtil.MAIN_TAG + "reCalculate()");
        mVideoState.videoDuration = mVideoState.audioDuration = 0;
        for (AVComponent component : mVideoState.videoComponents) {
            mVideoState.videoDuration = Math.max(component.getEngineEndTime(), mVideoState.videoDuration);
        }
        for (AVComponent component : mVideoState.audioComponents) {
            if (component instanceof AVAudio) {
                mVideoState.audioDuration = Math.max(component.getEngineEndTime(), mVideoState.audioDuration);
            }
        }
        mVideoState.durationUS = mVideoState.videoDuration;
        if (getMainClock() > mVideoState.durationUS) {
            setMainClock(mVideoState.durationUS);
        }
    }

    private void surfaceCreated(Surface surface) {
        LogUtil.log(LogUtil.MAIN_TAG + "surfaceCreated(Surface)");
        Command command = new Command();
        command.cmd = Command.Cmd.SURFACE_CREATED;
        mCmdQueue.add(command);
    }

    private void surfaceChanged(SurfaceHolder holder, int width, int height) {
        LogUtil.log(LogUtil.MAIN_TAG + "surfaceChanged(SurfaceHolder,int,int)#"
                + width + "#" + height + "#" + holder.getSurface().toString());
        Command command = new Command();
        command.cmd = Command.Cmd.SURFACE_CHANGED;
        command.args0 = holder.getSurface();
        command.args1 = width;
        command.args2 = height;
        mCmdQueue.add(command);
        //同步等待Engine线程处理完surface
        while (!mVideoState.isSurfaceReady) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        fastSeek(0);
        LogUtil.log(LogUtil.MAIN_TAG + "surfaceChanged(SurfaceHolder,int,int) END");
    }

    private void surfaceDestroyed(SurfaceHolder holder) {
        LogUtil.log(LogUtil.MAIN_TAG + "surfaceDestroyed(SurfaceHolder)" + holder.toString());
        Command command = new Command();
        command.cmd = Command.Cmd.SURFACE_DESTROYED;
        mCmdQueue.add(command);
        //同步等待Engine线程处理完surface
        while (mVideoState.isSurfaceReady) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        LogUtil.log(LogUtil.MAIN_TAG + "surfaceDestroyed(SurfaceHolder) END" + holder.toString());
    }

    public void start() {
        LogUtil.log(LogUtil.MAIN_TAG + "start()");
        Command command = new Command();
        command.cmd = Command.Cmd.PLAY;
        mCmdQueue.add(command);
    }

    private void playInternal() {
        setClock(mVideoState.extClock, getClock(mVideoState.extClock));
        mVideoState.status = START;
    }

    private void handleDestroy() {
        LogUtil.log(LogUtil.ENGINE_TAG + "SURFACE_DESTROYED!");
        mEglHelper.destroySurface();
        mEglHelper.makeCurrent();
        if (mOesRender != null) {
            mOesRender.close();
            mOesRender = null;
        }
        if (pagRender != null) {
            pagRender.close();
            pagRender = null;
        }
        mVideoState.isSurfaceReady = false;
    }

    public void pause() {
        LogUtil.log(LogUtil.MAIN_TAG + "pause()");
        Command command = new Command();
        command.cmd = Command.Cmd.PAUSE;
        mCmdQueue.add(command);
    }

    private void pauseInternal() {
        setMainClock(getMainClock());
        mVideoState.status = PAUSE;
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
        LogUtil.log(LogUtil.MAIN_TAG + "fastSeek(long)");
        seek(true);
        seek(position);
        seek(false);
    }

    public void seek(long position) {
        LogUtil.log(LogUtil.MAIN_TAG + "seek(long)");
        Command command = new Command();
        command.cmd = Command.Cmd.SEEK;
        command.args0 = position;
        mCmdQueue.add(command);
    }

    public void seek(boolean status) {
        LogUtil.log(LogUtil.MAIN_TAG + "seek(boolean)");
        Command command = new Command();
        command.cmd = Command.Cmd.SEEK;
        command.args0 = (long) (status ? SEEK_ENTER : SEEK_EXIT);
        mCmdQueue.add(command);
    }

    public void create() {
        LogUtil.log(LogUtil.MAIN_TAG + "create()");
        createEngineDaemon();
        createAudioDaemon();
    }

    private void releaseInternal() {
        Command command = new Command();
        command.cmd = Command.Cmd.RELEASE;
        mCmdQueue.offer(command);
    }

    public void release() {
        LogUtil.log(LogUtil.MAIN_TAG + "release()");
        releaseInternal();
        ThreadManager.getInstance().destroyThread("AudioThread");
        ThreadManager.getInstance().destroyThread("PagThread", () -> {
            synchronized (mPagDecodeSync) {
                mPagDecodeSync.notify();
            }
        }, () -> {
            mPagComposition.removeAllLayers();
            mPagTexture.release();
            mPagPlayer.release();
            mPagPlayer = null;
            mPagComposition = null;
        });
        ThreadManager.getInstance().destroyThread("EngineThread");
        LogUtil.log(LogUtil.MAIN_TAG + "release END");
//        AVEngine.gAVEngine = null;//...貌似不是很合适。。TODO
    }

    private void destroyInternal() {
        mVideoState.status = RELEASE;
        mVideoState.lock();
        for (AVComponent avComponent : mVideoState.audioComponents) {
            avComponent.lock();
            avComponent.close();
            avComponent.unlock();
        }
        mVideoState.audioComponents.clear();

        for (AVComponent avComponent : mVideoState.videoComponents) {
            avComponent.lock();
            avComponent.close();
            avComponent.unlock();
        }
        mVideoState.videoComponents.clear();
        mVideoState.unlock();
    }

    public VideoState getVideoState() {
        return mVideoState;
    }

    public EglHelper getEglHelper() {
        return mEglHelper;
    }

    private void renderPostIfNeed() {
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
//        LogUtil.logEngine(mVideoState.toString());
    }

}
