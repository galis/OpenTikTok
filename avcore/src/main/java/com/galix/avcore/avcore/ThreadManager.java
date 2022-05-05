package com.galix.avcore.avcore;

import android.os.Handler;
import android.os.HandlerThread;

import com.galix.avcore.util.LogUtil;

import java.util.HashMap;
import java.util.Map;

//线程管理类
public class ThreadManager {
    private static class ThreadInfo {
        public HandlerThread handlerThread;
        public Handler handler;
    }

    private static ThreadManager mThreadManager;
    private Map<String, ThreadInfo> mMap = new HashMap<>();

    public static ThreadManager getInstance() {
        if (mThreadManager == null) {
            synchronized (ThreadManager.class) {
                mThreadManager = new ThreadManager();
            }
        }
        return mThreadManager;
    }

    public void createThread(String threadName, Runnable runnable) {
        if (mMap.containsKey(threadName)) {
            return;
        }
        ThreadInfo threadInfo = new ThreadInfo();
        threadInfo.handlerThread = new HandlerThread(threadName);
        threadInfo.handlerThread.start();
        threadInfo.handler = new Handler(threadInfo.handlerThread.getLooper());
        threadInfo.handler.post(runnable);
        mMap.put(threadName, threadInfo);
        LogUtil.logEngine(threadName + "#start!");
    }

    public void destroyThread(String threadName) {
        if (!mMap.containsKey(threadName)) {
            return;
        }
        LogUtil.logEngine(threadName + "#start to finish!");
        mMap.get(threadName).handler.getLooper().quitSafely();
        try {
            mMap.get(threadName).handlerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mMap.remove(threadName);
        LogUtil.logEngine(threadName + "#finish");
    }

    public void destroyThread(String threadName, Runnable pre, Runnable post) {
        if (pre != null) {
            pre.run();
        }
        destroyThread(threadName);
        if (post != null) {
            post.run();
        }
    }

//    public void destroy() {
//    }
}
