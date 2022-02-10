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

import static com.galix.opentiktok.avcore.AVEngine.VideoState.VideoStatus.DESTROY;
import static com.galix.opentiktok.avcore.AVEngine.VideoState.VideoStatus.PAUSE;
import static com.galix.opentiktok.avcore.AVEngine.VideoState.VideoStatus.PLAY;
import static com.galix.opentiktok.avcore.AVEngine.VideoState.VideoStatus.SEEK;

/**
 * 视频引擎
 */
public class AVEngine implements GLSurfaceView.Renderer {

    private static final String TAG = AVEngine.class.getSimpleName();
    private static final int PLAY_GAP = 10;//MS
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

    public interface OnFrameUpdateCallback {
        void onFrameUpdate();
    }

    private AVEngine() {
        mVideoState = new VideoState();
        mComponents = new LinkedList<>();
    }

    //视频核心信息类
    public static class VideoState {

        public enum VideoStatus {
            INIT,
            PLAY,
            PAUSE,
            SEEK,
            DESTROY
        }

        public boolean isInputEOF = false;
        public boolean isOutputEOF = false;
        public boolean isExit = false;

        public long positionUS = Long.MAX_VALUE;//当前视频位置 us
        public long seekPositionUS = Long.MAX_VALUE;
        public long durationUS = Long.MAX_VALUE;//视频总时长 us
        public long videoTimeUS = Long.MAX_VALUE;//视频播放时间戳
        public long audioTimeUS = Long.MAX_VALUE;//音频播放时间戳
        public VideoStatus status = VideoStatus.INIT;//播放状态

        @Override
        public String toString() {
            return "VideoState{" +
                    "isInputEOF=" + isInputEOF +
                    ", isOutputEOF=" + isOutputEOF +
                    ", isExit=" + isExit +
                    ", positionUS=" + positionUS +
                    ", seekPositionUS=" + seekPositionUS +
                    ", durationUS=" + durationUS +
                    ", videoTimeUS=" + videoTimeUS +
                    ", audioTimeUS=" + audioTimeUS +
                    ", status=" + status +
                    '}';
        }
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
        boolean needSwap = false;
        while (!needSwap && !mVideoState.isExit) {
            //计算正确position
            long targetPosition = mVideoState.positionUS == Long.MAX_VALUE ? 0 : mVideoState.positionUS;
            if (mVideoState.status == SEEK) {
                targetPosition = mVideoState.seekPositionUS;
            }

            //处理贴纸片段
            List<AVComponent> effectComponents = findComponents(AVComponent.AVComponentType.STICKER, targetPosition);
            for (AVComponent component : effectComponents) {
                if (component.getRender() != null) {
                    if (mVideoState.status == PLAY) {
                        component.readFrame();
                    } else if (mVideoState.status == SEEK) {
                        component.seekFrame(targetPosition);
                    } else {
                        while (!component.peekFrame().isValid()) {
                            component.readFrame();
                        }
                    }
                    AVFrame avFrame = component.peekFrame();
                    if (avFrame.isValid()) {
                        component.getRender().render(avFrame);
                    }
                }
            }

            //处理视频片段
            long delay = 0;
            List<AVComponent> components = findComponents(AVComponent.AVComponentType.VIDEO, targetPosition);
            for (AVComponent component : components) {
                if (mVideoState.status == PLAY) {
                    component.readFrame();
                } else if (mVideoState.status == SEEK) {
                    component.seekFrame(targetPosition);
                } else {
                    while (!component.peekFrame().isValid()) {
                        component.readFrame();
                    }
                }
                AVFrame avFrame = component.peekFrame();
                if (avFrame != null) {
                    needSwap = true;
                    //音视频同步.
                    boolean needRender = true;
                    if (mVideoState.status == PLAY) {
                        needRender = avFrame.getPts() > mVideoState.audioTimeUS || (mVideoState.audioTimeUS - avFrame.getPts()) < 100000;
                        delay = Math.max(avFrame.getPts() - mVideoState.audioTimeUS, 0);
                    }
                    if (needRender) {
                        if (delay > 0) {
                            Log.d(TAG, "delay#" + delay / 10000);//TODO /10000?
                            try {
                                Thread.sleep(delay / 10000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        if (component.getRender() != null) {
                            component.getRender().render(avFrame);
                        } else {
                            mOesRender.render(avFrame);
                        }
                    }
                    mVideoState.positionUS = avFrame.getPts();
                }
            }

            //更新UI
            onFrameUpdate();

            //让出CPU？
            if (delay == 0) {
                try {
                    Thread.sleep(PLAY_GAP);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }

    }

    public void setOnFrameUpdateCallback(OnFrameUpdateCallback callback) {
        mOnFrameUpdateCallback = callback;
    }

    private void createAudioDaemon() {
        mAudioRender = new AudioRender();
        mAudioRender.open();
        mAudioThread = new HandlerThread("audioThread");
        mAudioThread.start();
        mAudioHandler = new Handler(mAudioThread.getLooper());
        mAudioHandler.post(() -> {
            while (!mVideoState.isExit) {
                if (mVideoState.status == PLAY || mVideoState.status == SEEK) {
                    long targetPosition = mVideoState.status == PLAY ? mVideoState.positionUS : mVideoState.seekPositionUS;
                    List<AVComponent> components = findComponents(AVComponent.AVComponentType.AUDIO, targetPosition);
                    for (AVComponent avComponent : components) {
                        if (mVideoState.status == PLAY) {
                            if (!avComponent.peekFrame().isValid()) {
                                avComponent.readFrame();
                            }
                        } else {
                            avComponent.seekFrame(targetPosition);
                        }
                        if (avComponent.peekFrame().isValid()) {
                            AVAudio audio = (AVAudio) components.get(0);
                            AVFrame audioFrame = audio.peekFrame();
                            if (mVideoState.status == PLAY) {
                                if (audio.getRender() != null) {
                                    audio.getRender().render(audioFrame);
                                } else {
                                    mAudioRender.render(audioFrame);
                                }
                            }
                            mVideoState.audioTimeUS = audioFrame.getPts();
                            audioFrame.markRead();
                        }
                    }
                } else {
                    try {
                        Thread.sleep(PLAY_GAP);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    /**
     * 根据类型查找组件
     *
     * @param type
     * @param position
     * @return
     */
    public List<AVComponent> findComponents(AVComponent.AVComponentType type, long position) {
        List<AVComponent> components = new LinkedList<>();
        for (AVComponent component : mComponents) {
            if (component.getType() == type && component.isValid(position)) {
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
        mComponents.add(avComponent);
        avComponent.open();
        reCalculate();
    }

    /**
     * 删除组件
     *
     * @param avComponent
     */
    public void removeComponent(AVComponent avComponent) {
        avComponent.close();
        mComponents.remove(avComponent);
        reCalculate();
    }

    /**
     * 计算总时长US.
     */
    private void reCalculate() {
        for (AVComponent component : mComponents) {
            mVideoState.durationUS = Math.max(component.getSrcStartTime() + component.getDuration(), mVideoState.durationUS);
        }
    }

    public void start() {
        mVideoState.isExit = false;
        mVideoState.status = PLAY;
    }

    public void pause() {
        mVideoState.status = PAUSE;
    }

    public void playPause() {
        if (mVideoState.status == PLAY) {
            pause();
        } else {
            start();
        }
    }

    public void seek(long position) {
        mVideoState.status = SEEK;
        if (position != -1) {
            mVideoState.seekPositionUS = position;
            mVideoState.isInputEOF = false;
            mVideoState.isOutputEOF = false;
        }
    }

    public void release() {
        mVideoState.isExit = true;
        mVideoState.status = DESTROY;
        mVideoState.positionUS = 0;//当前视频位置 us
        mVideoState.seekPositionUS = -1;
        mVideoState.durationUS = 0;//视频总时长 us
        mVideoState.videoTimeUS = Long.MAX_VALUE;//视频播放时间戳
        mVideoState.audioTimeUS = Long.MAX_VALUE;//音频播放时间戳

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

        mGLSurfaceView = null;
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
        dumpVideoState();
    }

    private void dumpVideoState() {
        Log.d(TAG, mVideoState.toString());
        for (AVComponent avComponent : mComponents) {
            Log.d(TAG, avComponent.toString());
        }
    }

}
