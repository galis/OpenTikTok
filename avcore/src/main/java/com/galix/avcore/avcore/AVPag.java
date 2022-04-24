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

import org.libpag.PAGComposition;
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
    private PAGFile pagFile;
    private String pagPath;
    private Rect mFrameRoi;
    private boolean mUseAsset;
    private AssetManager mAssetManager;
    private long mPagPts;

    public AVPag(String path, long engineStartTime, IRender render) {
        super(engineStartTime, AVComponentType.PAG, render);
        pagPath = path;
        mUseAsset = false;
        mPagPts = 0;
    }

    public AVPag(AssetManager manager, String path, long engineStartTime, IRender render) {
        super(engineStartTime, AVComponentType.PAG, render);
        mAssetManager = manager;
        pagPath = path;
        mUseAsset = true;
        mPagPts = 0;
    }


    @Override
    public int open() {
        if (isOpen()) return RESULT_OK;
        //初始化PagFile
        if (mUseAsset) {
            pagFile = PAGFile.Load(mAssetManager, pagPath);
        } else {
            pagFile = PAGFile.Load(pagPath);
        }
        mFrameRoi = new Rect(0, 0, pagFile.width(), pagFile.height());
        //设置相关参数
        setDuration(pagFile.duration());
        setClipStartTime(0);
        setClipEndTime(getDuration());
        setEngineEndTime(getEngineStartTime() + getDuration());
        markOpen(true);
        return 0;
    }

    @Override
    public int close() {
        if (!isOpen()) return RESULT_OK;
        return RESULT_OK;
    }

    @Override
    public int readFrame() {
        if (!isOpen()) return RESULT_FAILED;
        if (mPagPts >= getClipDuration() || !isVisible()) {
            peekFrame().setTexture(0);
            peekFrame().setRoi(new Rect(0, 0, 16, 16));
            peekFrame().setValid(true);
            return RESULT_FAILED;
        }
//        pagFile.setMatrix(getMatrix());
        peekFrame().setExt(pagFile);
        peekFrame().setPts(mPagPts + getEngineStartTime());
        peekFrame().setValid(true);
        peekFrame().setRoi(mFrameRoi);
        peekFrame().setDuration((long) (1000000.f / 24));
        peekFrame().getTexture().setMatrix(getMatrix());
        mPagPts += peekFrame().getDuration();
        if (isLoop()) {
            mPagPts %= getClipDuration();
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
