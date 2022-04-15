package com.galix.avcore.util;

import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.Surface;

import javax.microedition.khronos.egl.EGL10;

import static com.galix.avcore.util.EglCore.FLAG_TRY_GLES3;


/**
 * @Author: Galis
 * @Date:2022.03.21
 */
public class EglHelper {

    public static int GL_VERSION_3 = FLAG_TRY_GLES3;
    private EglCore mEglCore;
    private EGLSurface mEglSurface = EGL14.EGL_NO_SURFACE;

    public void create(EGLContext shareContext, int version) {
        mEglCore = new EglCore(shareContext, version);
        LogUtil.log("Egl#create(EGLContext,int)" + mEglCore.toString());
    }

    public boolean createSurface(Surface surface) {
        destroySurface();
        mEglSurface = mEglCore.createWindowSurface(surface);
        if (mEglSurface == null) {
            return false;
        }
        LogUtil.log("Egl#createSurface" + mEglCore.toString());
        return true;
    }

    public boolean swap() {
//        LogUtil.log("Egl#swap()");
        if (mEglCore == null) {
            LogUtil.log("Egl#swap() null????");
        }
        if (!mEglCore.swapBuffers(mEglSurface)) {
            int error = EGL14.eglGetError();
            LogUtil.log(LogUtil.ENGINE_TAG + "mEglHelper.swap() Error#" + error);
            return false;
        }
        return true;
    }

    public void destroySurface() {
        LogUtil.log("Egl#destroySurface()");
        if (mEglSurface != EGL14.EGL_NO_SURFACE) {
            mEglCore.releaseSurface(mEglSurface);
            mEglSurface = EGL14.EGL_NO_SURFACE;
            LogUtil.log("Egl#destroySurface() success!");
        }
    }

    public void release() {
        LogUtil.log("Egl#release()");
        if (mEglCore == null) {
            LogUtil.log("Egl#mEglCore==null");
        }
        mEglCore.releaseSurface(mEglSurface);
        mEglCore.release();
        mEglCore = null;
        mEglSurface = EGL14.EGL_NO_SURFACE;
    }

    public boolean makeCurrent() {
        LogUtil.log("Egl#makeCurrent()");
        return mEglCore.makeCurrent(mEglSurface);
    }

    public void makeNothingCurrent() {
        LogUtil.log("Egl#makeNothingCurrent()");
        mEglCore.makeNothingCurrent();
    }


    public void setPresentationTime(long nsecs) {
        LogUtil.log("Egl#setPresentationTime()");
        mEglCore.setPresentationTime(mEglSurface, nsecs);
    }

}
