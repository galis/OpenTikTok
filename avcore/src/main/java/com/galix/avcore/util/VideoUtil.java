package com.galix.avcore.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.opengl.EGLExt;
import android.opengl.GLES30;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

import static android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC;


/**
 * 视频工具类
 *
 * @Author:Galis
 * @Date:2022.01.16
 */
public class VideoUtil {

    static {
        System.loadLibrary("opencv_java3");
    }

    private static final String TAG = VideoUtil.class.getSimpleName();
    private static ThreadPoolExecutor mThreadPool;
    public static LinkedList<FileEntry> mTargetFiles;

    public static class FileEntry {
        public String path;
        public String adjustPath;
        public long duration;
        public long frameRate;
        public int width;
        public int height;
        public Bitmap thumb;
    }

    static {
        mThreadPool = new ThreadPoolExecutor(3, 3,
                10, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(10000)
        );
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
        File file = new File(videoPath);
        long modifyTime = 0;
        if (file.exists()) {
            modifyTime = file.lastModified();
        }
        return FileUtils.getCacheDir(context) + File.separator + md5(videoPath + "_gop_" + modifyTime) + ".mp4";
    }

    public static void saveBitmapFile(Bitmap bitmap, String path) {
        try {
            File file = new File(path);//将要保存图片的路径
            if (file.exists()) {
                file.delete();
            }
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class ThumbTask implements Runnable {
        public String path;
        public long start;
        public long end;
        public Handler.Callback callback;
        public Context context;

        public ThumbTask(Context context, String path, long start, long end, Handler.Callback callback) {
            this.context = context;
            this.path = path;
            this.start = start;
            this.end = end;
            this.callback = callback;
        }

        @Override
        public void run() {
            long nowPts = start;
            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(path);
            while (nowPts < end) {
                String dstJpg = VideoUtil.getThumbJpg(context, path, nowPts);
                Bitmap thumb = mediaMetadataRetriever.getFrameAtTime(nowPts, OPTION_CLOSEST_SYNC);
                thumb = Bitmap.createScaledBitmap(thumb, 160, 160, true);
                nowPts += 1000000L;
                saveBitmapFile(thumb, dstJpg);
            }
            mediaMetadataRetriever.close();
            if (callback != null) {
                callback.handleMessage(null);
            }
        }
    }

    /**
     * 获取视频每一秒的缩略图和调整关键帧GOP
     * 新的视频文件保存在context.getCacheDir()缓存目录下
     *
     * @param context  context
     * @param videos   视频集合
     * @param callback 回调
     */
    public static void processVideo(Context context, final LinkedList<FileEntry> videos, Handler.Callback callback) {
        if (videos.isEmpty()) return;
        List<ThumbTask> tasks = new LinkedList<>();
        for (FileEntry video : videos) {
            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(video.path);
            long duration = Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;
            long curPts = 0;
            while (curPts < duration) {
                tasks.add(new ThumbTask(context, video.path, curPts, Math.min(curPts + 6000000, duration), null));
                curPts += 6000000;
            }
            mediaMetadataRetriever.close();
        }
        tasks.get(0).callback = callback;
        VideoUtil.mTargetFiles = videos;
        for (ThumbTask thumbTask : tasks) {
            execute(thumbTask);
        }
//        Thread thread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                for (ThumbTask thumbTask : tasks) {
//                    thumbTask.run();
//                }
//            }
//        });
//        thread.start();
    }

}
