package com.galix.avcore.util;

import java.nio.ByteBuffer;

/**
 * YUV工具类
 *
 * @Author:galis
 * @Date:2022.01.19
 */
public class YuvUtils {

    static {
        System.loadLibrary("arcore");
    }

    /**
     * 缩放YUV并且保存为JPG图片
     *
     * @param yuvBuffer nio buffer
     * @param format    输出格式:NV21 NV12等
     * @param srcW      原宽度
     * @param srcH      原高度
     * @param dstW      目的宽度
     * @param dstH      目的高度
     * @param path      JPG图片路径
     */
    public static native void scaleAndSaveYuvAsJPEG(ByteBuffer yuvBuffer, int format, int srcW, int srcH, int dstW, int dstH, String path);
}
