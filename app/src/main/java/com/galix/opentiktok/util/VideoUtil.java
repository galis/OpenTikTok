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
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodec.CONFIGURE_FLAG_ENCODE;
import static android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED;


/**
 * 视频工具类
 *
 * @Author:Galis
 * @Date:2022.01.16
 */
public class VideoUtil {
    private static final String TAG = VideoUtil.class.getSimpleName();
    private static ThreadPoolExecutor mThreadPool;
    public static String mTargetPath = "/sdcard/coach.mp4";
    public static long mDuration = 0;

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

    private static void testEGL() {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String md5(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
            byte[] bytes = md5.digest(string.getBytes());
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) {
                String temp = Integer.toHexString(b & 0xff);
                if (temp.length() == 1) {
                    temp = "0" + temp;
                }
                result.append(temp);
            }
            return result.toString().toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 获取视频某pts缩略图时间戳
     * 时间单位US 时间取整数秒
     *
     * @param context
     * @param video
     * @param pts
     * @return
     */
    public static String getThumbJpg(Context context, String video, long pts) {
        return FileUtils.getCacheDir(context) + File.separator + md5(video + "_" + pts / 1000000 * 1000000) + ".jpg";
    }

    /**
     * 获取缓存的video文件路径
     *
     * @param context   context
     * @param videoPath 源文件路径
     * @return 缓存的video文件路径
     */
    public static String getAdjustGopVideoPath(Context context, String videoPath) {
        if (videoPath == null) return null;
        return FileUtils.getCacheDir(context) + File.separator + md5(videoPath + "_gop") + ".mp4";
    }

    /**
     * 获取视频每一秒的缩略图和调整关键帧GOP
     * 新的视频文件保存在context.getCacheDir()缓存目录下
     *
     * @param context  context
     * @param videos   视频集合
     * @param gop      gop大小
     * @param bitrate  比特率
     * @param callback 回调
     */
    public static void processVideo(Context context, final ArrayList<File> videos, int gop, int bitrate, Handler.Callback callback) {
        execute(() -> {
            try {
                for (File video : videos) {
                    String gopMp4Path = getAdjustGopVideoPath(context, video.getPath());
                    MediaMuxer mp4Muxer = new MediaMuxer(gopMp4Path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
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
                    //设置目标mp4格式
                    MediaFormat mediaFormat = mediaExtractor.getTrackFormat(videoIdx);
                    mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar);
                    mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                    mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, gop);
                    mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

                    //初始化相关编解码器
                    MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
                    String encoder = mediaCodecList.findEncoderForFormat(mediaFormat);
                    MediaCodec mediaCodec = MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));
                    MediaCodec muxerCodec = MediaCodec.createByCodecName(encoder);
                    muxerCodec.configure(mediaFormat, null, null, CONFIGURE_FLAG_ENCODE);
                    mediaCodec.configure(mediaFormat, null, null, 0);
                    mediaExtractor.selectTrack(videoIdx);
                    ByteBuffer byteBuffer = ByteBuffer.allocate(mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    mediaCodec.start();
                    muxerCodec.start();

                    //start 合成
                    boolean inputEOF = false;
                    boolean outputEOF = false;
                    boolean muxerEOF = false;
                    int srcW = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
                    int srcH = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
                    int frameIndex = 0;
                    int colorFormat = mediaCodec.getOutputFormat().getInteger(MediaFormat.KEY_COLOR_FORMAT);
                    long targetThumbSampleTime = 0;
                    while (!inputEOF || !outputEOF || !muxerEOF) {
                        if (!inputEOF) {
                            int bufIdx = mediaCodec.dequeueInputBuffer(0);
                            if (bufIdx >= 0) {
                                int sampleSize = mediaExtractor.readSampleData(byteBuffer, 0);
                                if (sampleSize < 0) {
                                    sampleSize = 0;
                                    inputEOF = true;
                                }
                                mediaCodec.getInputBuffer(bufIdx).put(byteBuffer);
                                Log.d(TAG, "mediaExtractor.getSampleTime()#time#" + mediaExtractor.getSampleTime());
                                mediaCodec.queueInputBuffer(bufIdx, 0, sampleSize, mediaExtractor.getSampleTime(), inputEOF ? BUFFER_FLAG_END_OF_STREAM : 0);
                                mediaExtractor.advance();
                            }
                        }

                        if (!outputEOF) {
                            int bufIdx = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                            if (bufIdx >= 0) {
                                ByteBuffer buffer = mediaCodec.getOutputBuffer(bufIdx);

                                //获取每一秒图片缩略图
                                if (bufferInfo.size > 0 && bufferInfo.presentationTimeUs > targetThumbSampleTime) {
                                    long now = System.currentTimeMillis();
                                    YuvUtils.scaleAndSaveYuvAsJPEG(buffer, colorFormat, srcW, srcH, srcW / 5, srcH / 5, getThumbJpg(context, gopMp4Path, targetThumbSampleTime));
                                    long now2 = System.currentTimeMillis();
                                    Log.d(TAG, "scaleAndSaveYuvAsJPEG#" + (now2 - now));
                                    targetThumbSampleTime += 1000000;
                                }

                                //输入到Muxer
                                boolean isSearch = false;
                                while (!isSearch) {//保证输入..
                                    int muxerInputStatus = muxerCodec.dequeueInputBuffer(0);
                                    if (muxerInputStatus >= 0) {
                                        muxerCodec.getInputBuffer(muxerInputStatus).put(buffer);
                                        Log.d(TAG, "muxerCodec.queueInputBuffer#time#" + bufferInfo.presentationTimeUs +
                                                "#end#" + ((bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM) ? "true" : "false" +
                                                "#buffer.limit#" + buffer.limit()));
                                        muxerCodec.queueInputBuffer(muxerInputStatus, 0, buffer.limit(), bufferInfo.presentationTimeUs,
                                                bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM ? BUFFER_FLAG_END_OF_STREAM : 0);
                                        isSearch = true;
                                    }
                                }
                                mediaCodec.releaseOutputBuffer(bufIdx, false);
                                if (bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM) {
                                    outputEOF = true;
                                    Log.d(TAG, "Output BUFFER_FLAG_END_OF_STREAM");
                                }
                                Log.d(TAG, "dequeueOutputBuffer#status:" + bufIdx + "#" + frameIndex + "#frameTime#" + bufferInfo.presentationTimeUs);
                                frameIndex++;
                            } else {
                                Log.d(TAG, "dequeueOutputBuffer#status:" + bufIdx);
                            }
                        }

                        if (!muxerEOF) {
                            int status = muxerCodec.dequeueOutputBuffer(bufferInfo, 0);
                            if (status >= 0) {
                                Log.d(TAG, "mp4Muxer.writeSampleData#status:" + bufferInfo.presentationTimeUs);
                                if (bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM) {
                                    muxerEOF = true;
                                    Log.d(TAG, "OUTPUT muxerEOF");
                                }
                                ByteBuffer sBuffer = muxerCodec.getOutputBuffer(status);
                                mp4Muxer.writeSampleData(0, sBuffer, bufferInfo);
                                muxerCodec.releaseOutputBuffer(status, false);
                            } else if (status == INFO_OUTPUT_FORMAT_CHANGED) {
                                mp4Muxer.addTrack(mediaFormat);
                                mp4Muxer.start();
                            }
                        }
                    }
                    mp4Muxer.stop();
                    mp4Muxer.release();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                callback.handleMessage(null);
            }
        });
    }


}
