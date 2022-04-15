package com.galix.avcore.util;

import android.util.Log;

import static com.galix.avcore.util.LogUtil.LogLevel.NONE;

public class LogUtil {
    public static final String TAG = "AVCore";
    public static final String ENGINE_TAG = "Engine#";
    public static final String MAIN_TAG = "Main#";
    public static final String EGL_TAG = "EGL#";
    public static LogLevel logLevel = NONE;

    public enum LogLevel {
        NONE,
        ENGINE,
        MAIN,
        FULL,
    }

    public static void setLogLevel(LogLevel level) {
        logLevel = level;
    }

    public static void log(String msg) {
        if (logLevel != NONE) {
            if (logLevel == LogLevel.FULL) {
                Log.d(TAG, msg);
            } else if (logLevel == LogLevel.ENGINE && msg.contains(ENGINE_TAG)) {
                Log.d(TAG, msg);
            } else if (logLevel == LogLevel.MAIN && msg.contains(MAIN_TAG)) {
                Log.d(TAG, msg);
            }
        }
    }

    public static void logEngine(String msg) {
        log(ENGINE_TAG + msg);
    }
}
