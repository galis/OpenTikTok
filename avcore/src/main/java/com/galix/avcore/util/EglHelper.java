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
    }

    public boolean createSurface(Surface surface) {
        destroySurface();
        mEglSurface = mEglCore.createWindowSurface(surface);
        return true;
    }

    public boolean swap() {
        return mEglCore.swapBuffers(mEglSurface);
    }

    public void destroySurface() {
        if (mEglSurface != EGL14.EGL_NO_SURFACE) {
            mEglCore.releaseSurface(mEglSurface);
            mEglSurface = EGL14.EGL_NO_SURFACE;
        }
    }

    public void release() {
        mEglCore.releaseSurface(mEglSurface);
        mEglCore.release();
        mEglCore = null;
        mEglSurface = EGL14.EGL_NO_SURFACE;
    }

    public void makeCurrent() {
        mEglCore.makeCurrent(mEglSurface);
    }

    public void makeNothingCurrent() {
        mEglCore.makeNothingCurrent();
    }


    public void setPresentationTime(long nsecs) {
        mEglCore.setPresentationTime(mEglSurface, nsecs);
    }

}
