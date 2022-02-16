package com.galix.opentiktok.util;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGLExt;
import android.opengl.GLES30;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Surface;

import java.io.File;
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
                    Mp4Adjust mp4Adjust = new Mp4Adjust(5, (int) (2.5 * 1024 * 1024), 44100, video.getAbsolutePath(), gopMp4Path);
                    mp4Adjust.process();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                callback.handleMessage(null);
            }
        });
    }

}
