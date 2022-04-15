package com.galix.avcore.avcore;

import android.content.res.AssetManager;
import android.graphics.Rect;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.os.Handler;
import android.os.HandlerThread;

import com.galix.avcore.render.IRender;
import com.galix.avcore.render.filters.GLTexture;
import com.galix.avcore.util.EglHelper;
import com.galix.avcore.util.LogUtil;
import com.galix.avcore.util.OtherUtils;

import org.libpag.PAGFile;
import org.libpag.PAGPlayer;
import org.libpag.PAGSurface;

import java.nio.IntBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.glTexParameterf;
import static android.opengl.GLES20.glTexParameteri;

/**
 * PAG组件支持
 *
 * @Author: Galis
 * @Date:2022.04.01
 */
public class AVPag extends AVComponent {
    private PAGPlayer pagPlayer = new PAGPlayer();
    private GLTexture cacheTexture = new GLTexture(0, false);
    private PAGFile pagFile;
    private String pagPath;
    private long mPagPts = 0;
    private long mPagDts = 0;
    private Rect mFrameRoi;
    private boolean mUseAsset = false;
    private AssetManager mAssetManager;
    private HandlerThread mHandlerThread;
    private Handler mDecodeHandler;
    private final Object mDecodeSync = new Object();
    private boolean mIsExit = false;
    private EGLContext mCurrentContext;

    public AVPag(String path, long engineStartTime, IRender render) {
        super(engineStartTime, AVComponentType.PAG, render);
        pagPath = path;
        mUseAsset = false;
    }

    public AVPag(AssetManager manager, String path, long engineStartTime, IRender render) {
        super(engineStartTime, AVComponentType.PAG, render);
        mAssetManager = manager;
        pagPath = path;
        mUseAsset = true;
    }


    @Override
    public int open() {
        if (isOpen()) return RESULT_OK;
        mIsExit = false;
        //初始化PagFile
        if (mUseAsset) {
            pagFile = PAGFile.Load(mAssetManager, pagPath);
        } else {
            pagFile = PAGFile.Load(pagPath);
        }
        //新建Texture，该Texture可以被解码线程通用.
        IntBuffer intBuffer = IntBuffer.allocate(1);
        GLES30.glGenTextures(1, intBuffer);
        GLES30.glBindTexture(GL_TEXTURE_2D, intBuffer.get(0));
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, pagFile.width(), pagFile.height(), 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES30.glBindTexture(GL_TEXTURE_2D, 0);
        mFrameRoi = new Rect(0, 0, pagFile.width(), pagFile.height());
        cacheTexture = new GLTexture(intBuffer.get(0), false);
        cacheTexture.setSize(pagFile.width(), pagFile.height());

        //设置相关参数
        setDuration(pagFile.duration());
        setClipStartTime(0);
        setClipEndTime(getDuration());
        setEngineEndTime(getEngineStartTime() + getDuration());

        //创建解码线程。
        mCurrentContext = EGL14.eglGetCurrentContext();
        mHandlerThread = new HandlerThread("AVPag#Decode#" + pagPath);
        mHandlerThread.start();
        mDecodeHandler = new Handler(mHandlerThread.getLooper());
        mDecodeHandler.post(new Runnable() {
            @Override
            public void run() {
                EglHelper eglHelper = new EglHelper();
                eglHelper.create(mCurrentContext, EglHelper.GL_VERSION_3);
                eglHelper.makeCurrent();
                pagPlayer.setComposition(pagFile);
                pagPlayer.setSurface(PAGSurface.FromTexture(cacheTexture.id(), pagFile.width(), pagFile.height()));
                pagPlayer.setCacheEnabled(true);
                pagPlayer.setProgress(0);//提前update一次，相当于初始化配置。
                pagPlayer.flush();
                while (!mIsExit) {
                    double progress = mPagDts * 1.f / getClipDuration();
                    OtherUtils.recordStart("AVPag#readFrame()#" + progress);
                    pagPlayer.setProgress(progress);
                    pagPlayer.flush();
                    OtherUtils.recordEnd("AVPag#readFrame()#" + progress);
                    synchronized (mDecodeSync) {
                        try {
                            mDecodeSync.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                eglHelper.release();
                LogUtil.log(LogUtil.ENGINE_TAG + "AVPag#DecodeThread exit");
            }
        });

        markOpen(true);
        return 0;
    }

    @Override
    public int close() {
        if (!isOpen()) return RESULT_OK;
        mIsExit = true;
        synchronized (mDecodeSync) {
            mDecodeSync.notifyAll();
        }
        mDecodeHandler.getLooper().quitSafely();
        if (mHandlerThread != null) {
            try {
                mHandlerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mHandlerThread = null;
        }
        cacheTexture.release();
        pagPlayer.release();
        return RESULT_OK;
    }

    @Override
    public int readFrame() {
        if (!isOpen()) return RESULT_FAILED;
        if (mPagPts >= getClipDuration()) {
            peekFrame().setTexture(0);
            peekFrame().setRoi(new Rect(0, 0, 16, 16));
            peekFrame().setValid(true);
            //这里可以去通知解码线程先去解码。回到初始位置0。
            if (mPagDts != 0) {
                mPagDts = 0;
                synchronized (mDecodeSync) {
                    mDecodeSync.notifyAll();
                }
            }
            return RESULT_FAILED;
        }
        peekFrame().setTexture(cacheTexture);
        peekFrame().setPts(mPagPts + getEngineStartTime());
        peekFrame().setValid(true);
        peekFrame().setRoi(mFrameRoi);
        peekFrame().setDuration((long) (1000000.f / 24));
        mPagPts += peekFrame().getDuration();
        if (isLoop()) {
            mPagPts %= getClipDuration();
        }
        mPagDts = mPagPts;
        synchronized (mDecodeSync) {
            mDecodeSync.notifyAll();
        }
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        if (!isOpen()) return RESULT_FAILED;
        if (position < getEngineStartTime() || position > getEngineEndTime()) {
            return RESULT_FAILED;
        }
        mPagPts = position - getEngineStartTime();
        return readFrame();
    }
}
