package com.galix.avcore.avcore;

import android.content.res.AssetManager;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLUtils;

import com.galix.avcore.render.IRender;
import com.galix.avcore.render.filters.GLTexture;

import org.libpag.PAGFile;
import org.libpag.PAGPlayer;
import org.libpag.PAGSurface;

import java.io.IOException;
import java.nio.IntBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_NEAREST;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.glDeleteTextures;
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
    private long mCurrentFramePts = 0;
    private Rect mFrameRoi;
    private boolean mUseAsset = false;
    private AssetManager mAssetManager;

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
        if (mUseAsset) {
            pagFile = PAGFile.Load(mAssetManager, pagPath);
        } else {
            pagFile = PAGFile.Load(pagPath);
        }
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
        pagPlayer.setSurface(PAGSurface.FromTexture(intBuffer.get(0), pagFile.width(), pagFile.height()));
        pagPlayer.setComposition(pagFile);
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
        cacheTexture.release();
        pagPlayer.release();
        return RESULT_OK;
    }

    @Override
    public int readFrame() {
        if (!isOpen()) return RESULT_FAILED;
        if (isLoop()) {
            mCurrentFramePts %= getClipDuration();
        }
        if (mCurrentFramePts < 0 || mCurrentFramePts >= getEngineDuration()) {
            peekFrame().setTexture(0);
            peekFrame().setRoi(new Rect(0, 0, 16, 16));
            peekFrame().setValid(false);
            return RESULT_FAILED;
        }
        pagPlayer.setProgress(mCurrentFramePts * 1.0f / getClipDuration());
        pagPlayer.flush();
        peekFrame().setTexture(cacheTexture.id());
        peekFrame().setPts(mCurrentFramePts + getEngineStartTime());
        peekFrame().setValid(true);
        peekFrame().setRoi(mFrameRoi);
        peekFrame().setDuration(33000);
        mCurrentFramePts += peekFrame().getDuration();
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        if (!isOpen()) return RESULT_FAILED;
        long correctPosition = position - getEngineStartTime();//engine position => file position
        if (position < getEngineStartTime() || position > getEngineEndTime() || correctPosition > getEngineDuration()) {
            return RESULT_FAILED;
        }
        mCurrentFramePts = position - getEngineStartTime();
        return readFrame();
    }
}
