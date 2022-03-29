package com.galix.opentiktok.dp;

import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.util.Size;

import com.galix.avcore.avcore.AVFrame;
import com.galix.avcore.render.IRender;
import com.galix.avcore.render.filters.BaseFilter;
import com.galix.avcore.render.filters.LutFilter;
import com.galix.avcore.render.filters.OesFilter;
import com.galix.avcore.render.filters.ScreenFilter;
import com.galix.avcore.util.GLUtil;
import com.galix.avcore.util.MathUtils;
import com.galix.opentiktok.R;


import java.util.HashMap;
import java.util.Map;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_LUMINANCE;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glDeleteTextures;
import static android.opengl.GLES20.glTexParameterf;
import static android.opengl.GLES20.glTexParameteri;
import static android.opengl.GLES20.glViewport;

/**
 * 地平线渲染器
 */
public class DPFastRender implements IRender {

    private LutFilter mLutFilter;
    private PlayerFilter mPlayerFilter;
    private ScreenFilter mScreenFilter;
    private OesFilter mOesFilter;
    private boolean mIsOpen = false;
    private Size mSurfaceSize = new Size(1920, 1080);
    private Bitmap mLut;
    private Map<String, Object> mConfig = new HashMap<>();
    private DpComponent.DpInfo mCacheDpInfo;

    @Override
    public boolean isOpen() {
        return mIsOpen;
    }

    @Override
    public void open() {
        if (mIsOpen) return;
        mLutFilter = new LutFilter();
        mLutFilter.open();
        mPlayerFilter = new PlayerFilter();
        mPlayerFilter.open();
        mScreenFilter = new ScreenFilter();
        mScreenFilter.open();
        mOesFilter = new OesFilter();
        mOesFilter.open();
        mIsOpen = true;
    }

    @Override
    public void close() {
        mLutFilter.close();
        mPlayerFilter.close();
        mScreenFilter.close();
        mLutFilter = null;
        mPlayerFilter = null;
        mScreenFilter = null;
        mIsOpen = false;
    }

    @Override
    public void write(Map<String, Object> config) {
        if (config.containsKey("surface_size")) {
            mSurfaceSize = (Size) config.get("surface_size");
        }
        if (config.containsKey("lut")) {
            mLut = (Bitmap) config.get("lut");
        }
        return;
    }

    @Override
    public void render(AVFrame avFrame) {
        renderReady(avFrame);
//        Bitmap coacheTexture = GLUtil.dumpTexture(mCacheDpInfo.coachTexture, 1920, 1080);
//        Bitmap playerTexture = GLUtil.dumpTexture(mCacheDpInfo.playerTexture, 1920, 1080);

//        mConfig.clear();
//        mConfig.put("use_fbo", true);
//        mConfig.put("fbo_size", mSurfaceSize);
//        mConfig.put("oes_input", mCacheDpInfo.coachTexture);
//        mOesFilter.write(mConfig);
//        mOesFilter.render();
//
//        mConfig.clear();
//        mConfig.put("screen_input", mOesFilter.getOutputTexture());
//        mConfig.put("use_fbo", false);
//        mScreenFilter.write(mConfig);
//        glBindFramebuffer(GL_FRAMEBUFFER, 0);
//        glViewport(0, 0, mSurfaceSize.getWidth(), mSurfaceSize.getHeight());
//        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
//        mScreenFilter.render();


        mConfig.put("use_fbo", true);
        mConfig.put("fbo_size", mCacheDpInfo.videoSize);
        mPlayerFilter.write(mConfig);
        mPlayerFilter.render();

        mConfig.clear();
        mConfig.put("lut_src", mLut);
        mConfig.put("lut_input", mPlayerFilter.getOutputTexture());
        mConfig.put("lut_alpha", 100.f);
        mConfig.put("use_fbo", true);
        mConfig.put("fbo_size", mCacheDpInfo.videoSize);
        mLutFilter.write(mConfig);
        mLutFilter.render();

        mConfig.clear();
        mConfig.put("screen_input", mLutFilter.getOutputTexture());
        mConfig.put("use_fbo", false);
        mScreenFilter.write(mConfig);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, mSurfaceSize.getWidth(), mSurfaceSize.getHeight());
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        mScreenFilter.render();

    }

    private void renderReady(AVFrame avFrame) {
        DpComponent.DpInfo dpInfo = (DpComponent.DpInfo) avFrame.getExt();
        dpInfo.playerSurfaceTexture.updateTexImage();
        dpInfo.coachSurfaceTexture.updateTexImage();

        if (dpInfo.playerMaskBuffer != null) {
            if (dpInfo.playerMaskTexture.id() != 0) {
                glDeleteTextures(1, dpInfo.playerMaskTexture.idAsBuf());
            }
            dpInfo.playerMaskTexture.idAsBuf().position(0);
            GLES30.glGenTextures(1, dpInfo.playerMaskTexture.idAsBuf());
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glBindTexture(GL_TEXTURE_2D, dpInfo.playerMaskTexture.id());
            GLES30.glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE,
                    dpInfo.playerMaskSize.getWidth(), dpInfo.playerMaskSize.getHeight(),
                    0, GL_LUMINANCE, GL_UNSIGNED_BYTE,
                    dpInfo.playerMaskBuffer);//注意检查 dpInfo.playerMaskBuffer position==0 limit==width*height
        } else {
            dpInfo.playerMaskTexture.idAsBuf().position(0);
            dpInfo.playerMaskTexture.idAsBuf().put(0);
        }
        dpInfo.playerMaskTexture.idAsBuf().position(0);


        dpInfo.srcPoints[0].x = 0;
        dpInfo.srcPoints[0].y = 0;
        dpInfo.srcPoints[1].x = dpInfo.videoSize.getWidth();
        dpInfo.srcPoints[1].y = 0;
        dpInfo.srcPoints[2].x = 0;
        dpInfo.srcPoints[2].y = dpInfo.videoSize.getHeight();

        dpInfo.dstPoints[0].x = dpInfo.playerMaskRoi.left;
        dpInfo.dstPoints[0].y = dpInfo.playerMaskRoi.top;
        dpInfo.dstPoints[1].x = dpInfo.playerMaskRoi.left + dpInfo.playerMaskRoi.width();
        dpInfo.dstPoints[1].y = dpInfo.playerMaskRoi.top;
        dpInfo.dstPoints[2].x = dpInfo.playerMaskRoi.left;
        dpInfo.dstPoints[2].y = dpInfo.playerMaskRoi.top + dpInfo.playerMaskRoi.height();

        dpInfo.playerMaskMat = MathUtils.getTransform(dpInfo.srcPoints, dpInfo.videoSize,
                dpInfo.dstPoints, dpInfo.videoSize);
        dpInfo.playerMaskMatBuffer.clear();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                dpInfo.playerMaskMatBuffer.put((float) dpInfo.playerMaskMat.get(i, j)[0]);
            }
        }
        dpInfo.playerMaskMatBuffer.position(0);
        mCacheDpInfo = dpInfo;
    }

    public class PlayerFilter extends BaseFilter {

        public PlayerFilter() {
            super(R.raw.dbrendervs, R.raw.dbrenderfs);
        }

        @Override
        public void onRenderPre() {
            bindTexture("coachTexture", mCacheDpInfo.coachTexture);
            bindTexture("playerTexture", mCacheDpInfo.playerTexture);
            bindTexture("playerMaskTexture", mCacheDpInfo.playerMaskTexture);
            bindMat3("playerMaskMat", mCacheDpInfo.playerMaskMatBuffer);
        }

        @Override
        public void onWrite(Map<String, Object> config) {
        }
    }

}
