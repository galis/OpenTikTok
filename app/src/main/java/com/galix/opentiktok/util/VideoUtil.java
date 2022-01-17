package com.galix.opentiktok.util;

import android.app.usage.UsageStats;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Message;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 视频预处理
 *
 * @Author:Galis
 * @Date:2022.01.16
 */
public class VideoUtil {
    private static ThreadPoolExecutor mThreadPool;
    private static int GOP = 10;//关键帧序列，用于快速seek

    static {
        mThreadPool = new ThreadPoolExecutor(2, 2,
                -1, TimeUnit.MILLISECONDS,
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
    public static void processVideo(final ArrayList<File> videos, Handler.Callback callback) {
        execute(() -> {
            Message message = new Message();
            for (File video : videos) {
                MediaExtractor mediaExtractor = new MediaExtractor();
                try {
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
                } catch (IOException e) {
                    e.printStackTrace();
                    callback.handleMessage(null);
                    return;
                }
            }
            callback.handleMessage(message);
        });
    }


}
