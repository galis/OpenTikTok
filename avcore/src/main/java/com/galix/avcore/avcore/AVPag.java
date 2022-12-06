package com.galix.avcore.avcore;

import android.graphics.Rect;
import android.opengl.GLES20;
import android.util.Size;

import com.galix.avcore.gl.ResourceManager;
import com.galix.avcore.render.IRender;
import com.galix.avcore.render.filters.GLTexture;
import com.galix.avcore.util.GLUtil;
import com.galix.avcore.util.LogUtil;
import com.galix.avcore.util.TimeUtils;

import org.libpag.PAGFile;
import org.libpag.PAGPlayer;
import org.libpag.PAGSurface;

/**
 * PAG组件支持
 *
 * @Author: Galis
 * @Date:2022.04.01
 */
public class AVPag extends AVComponent {
    private PAGFile mPagFile;
    private String mPagPath;
    private Rect mFrameRoi;
    private boolean mUseAsset;
    private long mPagPts;
    private PAGPlayer mPagPlayer;
    private GLTexture mPagTexture;

    public AVPag(String assetPath, long engineStartTime, IRender render) {
        super(engineStartTime, AVComponentType.PAG, render);
        mPagPath = assetPath;
        mPagPts = 0;
    }

    public AVPag(String assetPath, IRender render) {
        super(-1L, AVComponentType.PAG, render);
        mPagPath = assetPath;
        mPagPts = 0;
    }


    @Override
    public int open() {
        if (isOpen()) return RESULT_OK;
        //初始化PagFile
        mPagFile = PAGFile.Load(ResourceManager.getManager().getContext().getAssets(), mPagPath);
        mFrameRoi = new Rect(0, 0, mPagFile.width(), mPagFile.height());

        mPagPlayer = new PAGPlayer();
        mPagTexture = GLUtil.gen2DTexture();
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mPagFile.width(), mPagFile.height(),
                0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        mPagTexture.setSize(mPagFile.width(), mPagFile.height());
        mPagPlayer.setComposition(mPagFile);
        mPagPlayer.setSurface(PAGSurface.FromTexture(mPagTexture.id(), mPagFile.width(), mPagFile.height()));

        //设置相关参数
        setDuration(mPagFile.duration());
        setClipStartTime(0);
        setClipEndTime(getDuration());
        setEngineEndTime(getEngineStartTime() + getDuration());
        markOpen(true);
        return 0;
    }

    @Override
    public int close() {
        if (!isOpen()) return RESULT_OK;
        mPagFile.removeAllLayers();
        return RESULT_OK;
    }

    @Override
    public int readFrame() {
        if (!isOpen()) return RESULT_FAILED;
        boolean isInValid = mPagPts < 0 || mPagPts >= getClipDuration() || !isVisible();
        TimeUtils.RecordStart("avpag#setProgress#" + mPagPath);
        double progress = isInValid ? 1.1f : mPagPts * 1.0 / getClipDuration();
        mPagPlayer.setProgress(progress);
        mPagPlayer.flush();
        TimeUtils.RecordEnd("avpag#setProgress#" + mPagPath);
        peekFrame().setExt(this);
        peekFrame().setPts(mPagPts + getEngineStartTime());
        peekFrame().setValid(true);
        peekFrame().setRoi(mFrameRoi);
        peekFrame().setDuration((long) (1000000.f / 30));
        peekFrame().setTexture(mPagTexture);
        peekFrame().getTexture().setMatrix(getMatrix());
        mPagPts += peekFrame().getDuration();
        LogUtil.logEngine("avpag#pag pts#" + mPagPts);
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        if (!isOpen()) return RESULT_FAILED;
        LogUtil.logEngine("avpag#pag seekFrame " + position);
        if (position < getEngineStartTime() || position > getEngineEndTime()) {
            mPagPts = -1;
            return RESULT_FAILED;
        }
        mPagPts = position - getEngineStartTime();
        readFrame();
        return RESULT_OK;
    }

    public Size getSize() {
        return new Size(mFrameRoi.width(), mFrameRoi.height());
    }

    public String getPath() {
        return mPagFile.path();
    }
}
