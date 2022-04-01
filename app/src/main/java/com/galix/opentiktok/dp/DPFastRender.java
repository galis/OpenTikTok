package com.galix.opentiktok.dp;

import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.util.Log;
import android.util.Size;

import com.galix.avcore.avcore.AVFrame;
import com.galix.avcore.render.IRender;
import com.galix.avcore.render.filters.BaseFilter;
import com.galix.avcore.render.filters.BeautyFilter;
import com.galix.avcore.render.filters.GLTexture;
import com.galix.avcore.render.filters.IFilter;
import com.galix.avcore.render.filters.LutFilter;
import com.galix.avcore.render.filters.OesFilter;
import com.galix.avcore.render.filters.ScreenFilter;
import com.galix.avcore.util.MathUtils;
import com.galix.opentiktok.R;


import java.nio.FloatBuffer;
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
import static android.opengl.GLES20.GL_UNPACK_ALIGNMENT;
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

    private static final String TAG = DPFastRender.class.getSimpleName();
    //滤镜
    private LutFilter mLutFilter;
    private GameFilter mGameFilter;
    private OesFilter mOesFilter;
    private BeautyFilter mBeautyFilter;

    //参数
    private boolean mIsOpen = false;
    private Size mSurfaceSize = new Size(1920, 1080);
    private Size mBeautySize = new Size(1920 / 4, 1080 / 4);
    private Bitmap mPlayerLut, mBeautyLut;
    private Map<String, Object> mConfig = new HashMap<>();
    private DpComponent.DpInfo mCacheDpInfo;
    private boolean mIsBeauty = true;

    @Override
    public boolean isOpen() {
        return mIsOpen;
    }

    @Override
    public void open() {
        if (mIsOpen) return;
        mLutFilter = new LutFilter();
        mLutFilter.open();
        mGameFilter = new GameFilter();
        mGameFilter.open();
        mOesFilter = new OesFilter();
        mOesFilter.open();
        mBeautyFilter = new BeautyFilter();
        mBeautyFilter.open();
        mIsOpen = true;
    }

    @Override
    public void close() {
        mLutFilter.close();
        mGameFilter.close();
        mBeautyFilter.close();
        mLutFilter = null;
        mGameFilter = null;
        mIsOpen = false;
    }

    @Override
    public void write(Map<String, Object> config) {
        if (config.containsKey("surface_size")) {
            mSurfaceSize = (Size) config.get("surface_size");
        }
        if (config.containsKey("player_lut")) {
            mPlayerLut = (Bitmap) config.get("player_lut");
        }
        if (config.containsKey("beauty_lut")) {
            mBeautyLut = (Bitmap) config.get("beauty_lut");
        }
        if (config.containsKey("use_beauty")) {
            mIsBeauty = (boolean) config.get("use_beauty");
        }
    }

    @Override
    public void render(AVFrame avFrame) {
        long nowTime1 = System.currentTimeMillis();
        renderReady(avFrame);

        //先用OesFilter把EglImage内容复制一份。。为了让Lut滤镜不有坑.
        mConfig.clear();
        mConfig.put("use_fbo", true);
        mConfig.put("fbo_size", mCacheDpInfo.videoSize);
        mConfig.put("oes_input", mCacheDpInfo.playerTexture);
        mOesFilter.write(mConfig);
        mOesFilter.render();

        IFilter lastFilter = mOesFilter;
        if (mIsBeauty) {
            //美颜
            mConfig.clear();
            mConfig.put("use_fbo", true);
            mConfig.put("fbo_size", mBeautySize);
            mConfig.put("beauty_input", mOesFilter.getOutputTexture());
            mConfig.put("beauty_lut", mBeautyLut);
            mConfig.put("beauty_alpha", 1.0f);
            mBeautyFilter.write(mConfig);
            mBeautyFilter.render();
            lastFilter = mBeautyFilter;
        }

        //用户lut变换.
        mConfig.clear();
        mConfig.put("use_fbo", true);
        mConfig.put("fbo_size", mCacheDpInfo.videoSize);
        mConfig.put("lut_src", mPlayerLut);
        mConfig.put("lut_input", lastFilter.getOutputTexture());
        mConfig.put("lut_alpha", 1.0f);
        mLutFilter.write(mConfig);
        mLutFilter.render();

        //游戏画面合成.
        mConfig.clear();
        mConfig.put("use_fbo", false);
        mConfig.put("coachTexture", mCacheDpInfo.coachTexture);
        mConfig.put("playerTexture", mLutFilter.getOutputTexture());
        mConfig.put("playerMaskTexture", mCacheDpInfo.playerMaskTexture);
        mConfig.put("playerMaskMatBuffer", mCacheDpInfo.playerMaskMatBuffer);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, mSurfaceSize.getWidth(), mSurfaceSize.getHeight());
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        mGameFilter.write(mConfig);
        mGameFilter.render();

        long nowTime2 = System.currentTimeMillis();
        Log.d(TAG, "render time#" + (nowTime2 - nowTime1));

    }

    private void renderReady(AVFrame avFrame) {
        DpComponent.DpInfo dpInfo = (DpComponent.DpInfo) avFrame.getExt();
        dpInfo.playerSurfaceTexture.updateTexImage();
        dpInfo.coachSurfaceTexture.updateTexImage();

        if (dpInfo.playerMaskBuffer != null) {
            if (dpInfo.playerMaskTexture.id() != 0) {
                glDeleteTextures(1, dpInfo.playerMaskTexture.idAsBuf());
            }
            GLES30.glGenTextures(1, dpInfo.playerMaskTexture.idAsBuf());
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glBindTexture(GL_TEXTURE_2D, dpInfo.playerMaskTexture.id());
            GLES30.glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            GLES30.glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE,
                    dpInfo.playerMaskSize.getWidth(), dpInfo.playerMaskSize.getHeight(),
                    0, GL_LUMINANCE, GL_UNSIGNED_BYTE,
                    dpInfo.playerMaskBuffer);//注意检查 dpInfo.playerMaskBuffer position==0 limit==width*height
            GLES30.glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            dpInfo.playerTexture.setSize(dpInfo.playerMaskSize.getWidth(), dpInfo.playerMaskSize.getHeight());
        } else {
            dpInfo.playerMaskTexture.idAsBuf().put(0);
            dpInfo.playerTexture.setSize(0, 0);
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

    public static class GameFilter extends BaseFilter {

        private GLTexture coachTexture;
        private GLTexture playerTexture;
        private GLTexture playerMaskTexture;
        private FloatBuffer playerMaskMatBuffer;

        public GameFilter() {
            super(R.raw.gamevs, R.raw.gamefs);
        }

        @Override
        public void onRenderPre() {
            bindTexture("coachTexture", coachTexture);
            bindTexture("playerTexture", playerTexture);
            bindTexture("playerMaskTexture", playerMaskTexture);
            bindMat3("playerMaskMat", playerMaskMatBuffer);
        }

        @Override
        public void onWrite(Map<String, Object> config) {
            if (config.containsKey("coachTexture")) {
                coachTexture = (GLTexture) config.get("coachTexture");
            }
            if (config.containsKey("playerTexture")) {
                playerTexture = (GLTexture) config.get("playerTexture");
            }
            if (config.containsKey("playerMaskTexture")) {
                playerMaskTexture = (GLTexture) config.get("playerMaskTexture");
            }
            if (config.containsKey("playerMaskMatBuffer")) {
                playerMaskMatBuffer = (FloatBuffer) config.get("playerMaskMatBuffer");
            }
        }
    }

}
