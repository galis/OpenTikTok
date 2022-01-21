package com.galix.opentiktok;

import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import com.galix.opentiktok.util.VideoUtil;

import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;

/**
 * 视频引擎
 */
public class VideoEngine implements GLSurfaceView.Renderer {

    private static final String TAG = VideoEngine.class.getSimpleName();
    private static VideoEngine gVideoEngine = null;
    private VideoState mVideoState;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private GLSurfaceView mGLSurfaceView;
    private ARContext mARContext;
    private OnFrameUpdateCallback mOnFrameUpdateCallback;

    public interface OnFrameUpdateCallback {
        void onFrameUpdate();
    }

    private VideoEngine() {
        mVideoState = new VideoState();
    }

    //视频核心信息类
    public static class VideoState {
        public static final int INIT = -1;
        public static final int PLAY = 0;
        public static final int PAUSE = 1;
        public static final int SEEK = 2;
        public static final int DESTROY = 3;

        public boolean isFirstOpen = true;
        public boolean isSeekDone = true;
        public boolean isInputEOF = false;
        public boolean isOutputEOF = false;
        public boolean isExit = false;

        //贴纸状态
        public long stickerStartTime = -1;
        public long stickerEndTime = -1;
        public long stickerCount;
        public int stickerRes = -1;
        public Rect stickerRoi;

        //文字状态
        public int wordStartTime = -1;
        public int wordEndTime = -1;
        public String word = null;
        public Rect wordRoi;

        //视频信息
        //TODO 多段视频
        public long position = 0;//当前视频位置 ms
        public long seekPostition = -1;
        public long duration = 0;//视频总时长 ms
        public long videoTime = Long.MIN_VALUE;//视频播放时间戳
        public long audioTime = Long.MIN_VALUE;//音频播放时间戳
        public int status = INIT;//播放状态

        //音频信息
        //TODO 多段音频

        //纹理
        public int surfaceTextureId;
        public Surface surface;
        public SurfaceTexture surfaceTexture;
    }

    public static VideoEngine getVideoEngine() {
        if (gVideoEngine == null) {
            synchronized (VideoEngine.class) {
                if (gVideoEngine == null) {
                    gVideoEngine = new VideoEngine();
                }
            }
        }
        return gVideoEngine;
    }

    public void configure(GLSurfaceView glSurfaceView) {
        mGLSurfaceView = glSurfaceView;
        mGLSurfaceView.setEGLContextClientVersion(3);
        mGLSurfaceView.setRenderer(this);
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        mARContext = new ARContext();
        mARContext.create();
        createDaemon();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mARContext.onSurfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        mVideoState.surfaceTexture.updateTexImage();
        mARContext.draw(mVideoState.surfaceTextureId);
    }

    public void setOnFrameUpdateCallback(OnFrameUpdateCallback callback) {
        mOnFrameUpdateCallback = callback;
    }

    private void createDaemon() {
        mHandlerThread = new HandlerThread("VideoEngine");
        mHandlerThread.start();
        int[] surface = new int[1];
        GLES30.glGenTextures(1, surface, 0);
        if (surface[0] == -1) return;//申请不到texture,出错
        mVideoState.surfaceTexture = new SurfaceTexture(surface[0]);
        mVideoState.surfaceTextureId = surface[0];
        mVideoState.surface = new Surface(mVideoState.surfaceTexture);
        mHandler = new Handler(mHandlerThread.getLooper());
        mHandler.post(() -> {
            MediaExtractor mediaExtractor = new MediaExtractor();
            int videoTrackIndex = -1;
            try {
                mediaExtractor.setDataSource(VideoUtil.mTargetPath);
                for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                    if (mediaExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).contains("video")) {
                        videoTrackIndex = i;
                        break;
                    }
                    Log.d("TAG", mediaExtractor.getTrackFormat(i).toString());
                }
                if (videoTrackIndex != -1) {
                    mediaExtractor.selectTrack(videoTrackIndex);
                    MediaFormat videoFormat = mediaExtractor.getTrackFormat(videoTrackIndex);
                    MediaCodec mediaCodec = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
                    if (mediaCodec == null) return;
                    mediaCodec.configure(videoFormat, mVideoState.surface, null, 0);
                    mediaCodec.start();
                    ByteBuffer sampleBuffer = ByteBuffer.allocate(videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
                    mVideoState.duration = mediaExtractor.getTrackFormat(videoTrackIndex).getLong(MediaFormat.KEY_DURATION);
                    while (!mVideoState.isExit) {
                        if (!(mVideoState.isFirstOpen || mVideoState.status == VideoState.SEEK || !mVideoState.isOutputEOF || !mVideoState.isInputEOF)) {
                            Thread.sleep(20);
                            continue;
                        }
                        if (mVideoState.isFirstOpen || mVideoState.status == VideoState.SEEK) {
                            mediaExtractor.seekTo(mVideoState.position, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            mediaCodec.flush();
                            if (!mVideoState.isInputEOF) {
                                int inputBufIdx = mediaCodec.dequeueInputBuffer(0);
                                if (inputBufIdx >= 0) {
                                    int sampleSize = mediaExtractor.readSampleData(sampleBuffer, 0);
                                    if (sampleSize < 0) {
                                        sampleSize = 0;
                                        mVideoState.isInputEOF = true;
                                        Log.d(TAG, "mVideoState.isInputEOF");
                                    }
                                    mediaCodec.getInputBuffer(inputBufIdx).put(sampleBuffer);
                                    mediaCodec.queueInputBuffer(inputBufIdx, 0,
                                            sampleSize,
                                            mediaExtractor.getSampleTime(),
                                            mVideoState.isInputEOF ? BUFFER_FLAG_END_OF_STREAM : 0);
                                }
                            }
                            mVideoState.isSeekDone = false;
                            while (!mVideoState.isSeekDone && !mVideoState.isOutputEOF) {
                                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                                int outputBufIdx = mediaCodec.dequeueOutputBuffer(bufferInfo, -1);
                                if (outputBufIdx >= 0) {
                                    if (bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM) {
                                        mVideoState.isOutputEOF = true;
                                        Log.d(TAG, "BUFFER_FLAG_END_OF_STREAM:" + bufferInfo.presentationTimeUs);
                                        continue;
                                    }
                                    mediaCodec.releaseOutputBuffer(outputBufIdx, true);
                                    mGLSurfaceView.requestRender();
                                    Log.d(TAG, "isSeekDone");
                                    mVideoState.isSeekDone = true;
                                } else if (outputBufIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                    Log.d(TAG, "INFO_TRY_AGAIN_LATER:" + bufferInfo.presentationTimeUs);
                                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED:" + bufferInfo.presentationTimeUs);
                                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                    Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED:" + bufferInfo.presentationTimeUs);
                                }
                            }

                            if (mVideoState.isFirstOpen) {
                                mVideoState.isFirstOpen = false;
                            }
                            onFrameUpdate();
                        } else {
                            if (mVideoState.status != VideoState.PLAY) {
                                continue;
                            }
                            if (!mVideoState.isInputEOF) {
                                int inputBufIdx = mediaCodec.dequeueInputBuffer(0);
                                if (inputBufIdx >= 0) {
                                    int sampleSize = mediaExtractor.readSampleData(sampleBuffer, 0);
                                    if (sampleSize < 0) {
                                        sampleSize = 0;
                                        mVideoState.isInputEOF = true;
                                        Log.d(TAG, "mVideoState.isInputEOF");
                                    }
                                    mediaCodec.getInputBuffer(inputBufIdx).put(sampleBuffer);
                                    mediaCodec.queueInputBuffer(inputBufIdx, 0,
                                            sampleSize,
                                            mediaExtractor.getSampleTime(),
                                            mVideoState.isInputEOF ? BUFFER_FLAG_END_OF_STREAM : 0);
                                    mediaExtractor.advance();
                                }
                            }
                            if (!mVideoState.isOutputEOF) {
                                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                                int outputBufIdx = mediaCodec.dequeueOutputBuffer(bufferInfo, -1);
                                if (outputBufIdx >= 0) {
                                    if (bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM) {
                                        mVideoState.isOutputEOF = true;
                                    }
                                    if (bufferInfo.presentationTimeUs > 0) {
                                        //音视频同步逻辑
                                        long now = System.nanoTime() / 1000;
                                        long delta = bufferInfo.presentationTimeUs - (mVideoState.videoTime + now);
                                        boolean needShown = mVideoState.videoTime == Long.MIN_VALUE || (delta > 0 && delta < 100000);
                                        if (needShown && mVideoState.videoTime != Long.MIN_VALUE) {
                                            Thread.sleep(delta / 1000, (int) (delta % 1000));
                                        }
                                        mediaCodec.releaseOutputBuffer(outputBufIdx, needShown);
                                        mVideoState.videoTime = bufferInfo.presentationTimeUs - System.nanoTime() / 1000;
                                        mVideoState.position = bufferInfo.presentationTimeUs;
                                    }
                                    mGLSurfaceView.requestRender();
                                    onFrameUpdate();
                                } else if (outputBufIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                    Log.d(TAG, "INFO_TRY_AGAIN_LATER:" + bufferInfo.presentationTimeUs);
                                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED:" + bufferInfo.presentationTimeUs);
                                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                    Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED:" + bufferInfo.presentationTimeUs);
                                }
                            }
                        }
                    }
                    mediaExtractor.release();
                    mediaCodec.release();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void start() {
        mVideoState.status = VideoState.PLAY;
    }

    public void pause() {
        mVideoState.status = VideoState.PAUSE;
    }

    public void playPause() {
        if (mVideoState.status == VideoState.PLAY) {
            pause();
        } else {
            start();
        }
    }

    public void seek(long position) {
        mVideoState.status = VideoState.SEEK;
        if (position != -1) {
            mVideoState.position = position;
            mVideoState.isInputEOF = false;
            mVideoState.isOutputEOF = false;
        }
    }

    public void release() {
        mVideoState.isExit = true;
        mVideoState.status = VideoState.DESTROY;
        if (mHandler != null && mHandlerThread != null) {
            mHandler.getLooper().quit();
            try {
                mHandlerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public VideoState getVideoState() {
        return mVideoState;
    }

    private void onFrameUpdate() {
        if (mOnFrameUpdateCallback != null) {
            mOnFrameUpdateCallback.onFrameUpdate();
        }
        dumpVideoState();
    }

    private void dumpVideoState() {
        Log.d(TAG, "VideoState#Duration#" + mVideoState.duration + "\n" +
                "Position#" + mVideoState.position + "\n" +
                "Sticker#startTime" + mVideoState.stickerStartTime + "\n" +
                "Sticker#endTime" + mVideoState.stickerEndTime + "\n"
        );
    }

}
