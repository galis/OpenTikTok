package com.galix.opentiktok;

public class ARContext {

    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("arcore");
    }

    private long mNativePtr;

    public int create() {
        mNativePtr = nativeCreate();
        return mNativePtr == -1 ? 1 : 0;
    }

    public int onSurfaceChanged(int width, int height) {
        return nativeSurfaceChanged(mNativePtr, width, height);
    }

    public int destroy() {
        if (mNativePtr > 0) {
            nativeDestroy(mNativePtr);
        }
        return 0;
    }

    public void draw(int cameraSurfaceId) {
        nativeDraw(mNativePtr, cameraSurfaceId);
    }

    public void setFace(float x, float y, float scale, float pitch, float yaw, float roll) {
        nativeSetFace(mNativePtr, x, y, scale, pitch, yaw, roll);
    }

    public static native long nativeCreate();

    public static native int nativeSetFace(long nativePtr, float x, float y, float scale, float pitch, float yaw, float roll);

    public static native int nativeSurfaceChanged(long nativePtr, int width, int height);

    public static native int nativeDraw(long nativePtr, int surfaceId);

    public static native int nativeDestroy(long nativePtr);

}
