package com.galix.avcore.avcore;

import android.content.res.AssetManager;
import android.graphics.Rect;

import com.galix.avcore.render.IRender;
import com.galix.avcore.util.LogUtil;
import com.galix.avcore.util.OtherUtils;

import org.libpag.PAGFile;

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
        pagFile.setExcludedFromTimeline(true);
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
        pagFile.removeAllLayers();
        return RESULT_OK;
    }

    @Override
    public int readFrame() {
        if (!isOpen()) return RESULT_FAILED;
        boolean isInValid = mPagPts < 0 || mPagPts >= getClipDuration() || !isVisible();
        OtherUtils.RecordStart("avpag#setProgress#" + pagPath);
        double progress = isInValid ? 1.1f : mPagPts * 1.0 / getClipDuration();
        if (pagFile.getProgress() != progress) {
            pagFile.setProgress(progress);
        }
        OtherUtils.RecordEnd("avpag#setProgress#" + pagPath);
        peekFrame().setExt(pagFile);
        peekFrame().setPts(mPagPts + getEngineStartTime());
        peekFrame().setValid(true);
        peekFrame().setRoi(mFrameRoi);
        peekFrame().setDuration((long) (1000000.f / 30));
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
        return RESULT_OK;
    }
}
