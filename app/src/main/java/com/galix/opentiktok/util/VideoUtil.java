package com.galix.opentiktok.util;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGLExt;
import android.opengl.GLES30;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGL;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodec.CONFIGURE_FLAG_ENCODE;


/**
 * 视频预处理
 *
 * @Author:Galis
 * @Date:2022.01.16
 */
public class VideoUtil {
    private static final String TAG = VideoUtil.class.getSimpleName();
    private static ThreadPoolExecutor mThreadPool;
    private static int GOP = 10;//关键帧序列，用于快速seek
    public static String mTargetPath;

    static {
        mThreadPool = new ThreadPoolExecutor(2, 2,
                10, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(10));
    }

    private VideoUtil() {
    }

    private static void execute(Runnable runnable) {
        mThreadPool.execute(runnable);
    }

    /**
     * 获取视频每一秒的缩略图和调整关键帧GOP
     *
     * @param videos   视频集合
     * @param callback 处理成功回调
     */
    public static void processVideo(Context context, final ArrayList<File> videos, Handler.Callback callback) {
        execute(() -> {
            EGLContext eglContext = null;
            EGLDisplay eglDisplay = null;
            SurfaceTexture surfaceTexture = null;
            Surface surface = null;
            EGL10 egl = null;
            try {
                egl = (EGL10) EGLContext.getEGL();
                eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
                int[] attrs = {
                        EGL10.EGL_RED_SIZE, 8,
                        EGL10.EGL_GREEN_SIZE, 8,
                        EGL10.EGL_BLUE_SIZE, 8,
                        EGL10.EGL_ALPHA_SIZE, 8,
                        EGL10.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                        EGL10.EGL_NONE};
                int[] attrList = {0x3098, 3, EGL10.EGL_NONE};
                EGLConfig[] eglConfig = new EGLConfig[1];
                int[] numConfigs = new int[1];
                if (!egl.eglChooseConfig(eglDisplay, attrs, eglConfig, 1, numConfigs)) {
                    return;
                }
                eglContext = egl.eglCreateContext(eglDisplay, eglConfig[0], EGL10.EGL_NO_CONTEXT, attrList);
                egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, eglContext);
                IntBuffer intBuffer = IntBuffer.allocate(1);
                GLES30.glGenTextures(1, intBuffer);
                surfaceTexture = new SurfaceTexture(intBuffer.get(0));
                surface = new Surface(surfaceTexture);
                Message message = new Message();
                for (File video : videos) {
                    MediaMuxer mp4Muxer = new MediaMuxer(context.getCacheDir() + File.separator + "output.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                    MediaExtractor mediaExtractor = new MediaExtractor();
                    mediaExtractor.setDataSource(video.getAbsolutePath());
                    int videoIdx = -1;
                    for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                        if (mediaExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).contains("video")) {
                            videoIdx = i;
                            break;
                        }
                    }
                    if (videoIdx == -1) {
                        return;
                    }
                    MediaFormat mediaFormat = mediaExtractor.getTrackFormat(videoIdx);
                    mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar);
                    mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mediaFormat.getInteger(MediaFormat.KEY_WIDTH)
                            * mediaFormat.getInteger(MediaFormat.KEY_HEIGHT));
                    mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
                    mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

                    MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
                    String encoder = mediaCodecList.findEncoderForFormat(mediaFormat);
                    MediaCodec mediaCodec = MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));
                    MediaCodec muxerCodec = MediaCodec.createByCodecName(encoder);
                    muxerCodec.configure(mediaFormat, null, null, CONFIGURE_FLAG_ENCODE);
                    Surface surface1 = muxerCodec.createInputSurface();
                    mediaCodec.configure(mediaFormat, surface1, null, 0);
                    mediaExtractor.selectTrack(videoIdx);
                    ByteBuffer byteBuffer = ByteBuffer.allocate(mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    mediaCodec.start();
                    muxerCodec.start();
                    int length = -1;
                    boolean inputEOF = false;
                    boolean outputEOF = false;
                    while (!inputEOF || !outputEOF) {
                        if (!inputEOF) {
                            int bufIdx = mediaCodec.dequeueInputBuffer(10);
                            if (bufIdx >= 0) {
                                length = mediaExtractor.readSampleData(byteBuffer, 0);
                                inputEOF = length < 0;
                                ByteBuffer bBuffer = mediaCodec.getInputBuffer(bufIdx);
                                bBuffer.put(byteBuffer.array(), 0, length);
                                mediaCodec.queueInputBuffer(bufIdx, 0, length, mediaExtractor.getSampleTime(), length < 0 ? BUFFER_FLAG_END_OF_STREAM : 0);
                            }
                        }

                        if (!outputEOF) {
                            int bufIdx = mediaCodec.dequeueOutputBuffer(bufferInfo, 10);
                            if (bufIdx >= 0) {
                                if (bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM) {
                                    outputEOF = true;
                                    Log.d(TAG, "OUTPUT EOF");
                                }
                                mediaCodec.releaseOutputBuffer(bufIdx, true);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                callback.handleMessage(null);
                return;
            } finally {
                if (eglContext != null) {
                    egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                    egl.eglDestroyContext(eglDisplay, eglContext);
                }
                if (surfaceTexture != null) {
                    surfaceTexture.release();
                }
                if (surface != null) {
                    surface.release();
                }
            }
            callback.handleMessage(null);
        });
    }


}
