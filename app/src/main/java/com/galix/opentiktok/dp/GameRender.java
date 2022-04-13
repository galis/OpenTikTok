package com.galix.opentiktok.dp;

import android.graphics.Rect;
import android.opengl.GLES30;
import android.util.Log;
import android.util.Size;

import com.galix.avcore.avcore.AVFrame;
import com.galix.avcore.render.IRender;
import com.galix.avcore.render.filters.BaseFilter;
import com.galix.avcore.render.filters.BeautyFilter;
import com.galix.avcore.render.filters.IFilter;
import com.galix.avcore.render.filters.LutFilter;
import com.galix.avcore.render.filters.OesFilter;
import com.galix.avcore.util.LogUtil;
import com.galix.avcore.util.MathUtils;
import com.galix.opentiktok.R;

import org.opencv.core.Mat;

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
public class GameRender implements IRender {

    private static final String TAG = GameRender.class.getSimpleName();
    //滤镜
    private LutFilter mLutFilter;
    private GameFilter mGameFilter;
    private OesFilter mOesFilter;
    private BeautyFilter mBeautyFilter;

    //参数
    private boolean mIsOpen = false;
    private Size mSurfaceSize = new Size(1920, 1080);
    private Size mBeautySize = new Size(1920 / 2, 1080 / 2);
    private Map<String, Object> mConfig = new HashMap<>();
    private GameComponent.GameInfo mCacheGameInfo;

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
    }

    @Override
    public void render(AVFrame avFrame) {
        //bind
        mCacheGameInfo = (GameComponent.GameInfo) avFrame.getExt();
        IFilter lastFilter;

        //先用OesFilter把EglImage内容复制一份。。为了让Lut滤镜不有坑.
        mConfig.clear();
        mConfig.put("use_fbo", true);
        mConfig.put("fbo_size", mCacheGameInfo.videoSize);
        mConfig.put("oes_input", mCacheGameInfo.playerTexture);
        mOesFilter.write(mConfig);
        mOesFilter.render();
        lastFilter = mOesFilter;

        if (mCacheGameInfo.useBeauty) {
            //美颜
            mConfig.clear();
            mConfig.put("use_fbo", true);
            mConfig.put("fbo_size", mBeautySize);
            mConfig.put("beauty_input", lastFilter.getOutputTexture());
            mConfig.put("beauty_lut", mCacheGameInfo.beautyLut);
            mConfig.put("beauty_alpha", 1.0f);
            mBeautyFilter.write(mConfig);
            mBeautyFilter.render();
            lastFilter = mBeautyFilter;
        }

        //用户lut变换.
        mConfig.clear();
        mConfig.put("use_fbo", true);
        mConfig.put("fbo_size", mCacheGameInfo.videoSize);
        mConfig.put("lut_src", mCacheGameInfo.playerLut);
        mConfig.put("lut_input", lastFilter.getOutputTexture());
        mConfig.put("lut_alpha", 1.0f);
        mLutFilter.write(mConfig);
        mLutFilter.render();
        lastFilter = mLutFilter;

        //游戏画面合成.
        mConfig.clear();
        mConfig.put("use_fbo", false);
        mConfig.put("coachTexture", mCacheGameInfo.coachTexture);
        mConfig.put("playerTexture", lastFilter.getOutputTexture());
        mConfig.put("playerMaskTexture", mCacheGameInfo.playerMaskTexture);
        mConfig.put("playerEffectTexture", mCacheGameInfo.playerEffectTexture);
        mConfig.put("screenEffectTexture", mCacheGameInfo.screenEffectTexture);
        mConfig.put("playerMaskMat", mCacheGameInfo.playerMaskMat);
        mConfig.put("playerEffectMat", mCacheGameInfo.playerEffectMat);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, mSurfaceSize.getWidth(), mSurfaceSize.getHeight());
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        mGameFilter.write(mConfig);
        mGameFilter.render();
    }


    public static class GameFilter extends BaseFilter {

        public GameFilter() {
            super(R.raw.gamevs, R.raw.gamefs);
        }

        @Override
        public void onRenderPre() {
            bindTexture("coachTexture");
            bindTexture("playerTexture");
            bindTexture("playerMaskTexture");
            bindTexture("screenEffectTexture");
            bindTexture("playerEffectTexture");
            bindMat3("playerMaskMat");
            bindMat3("playerEffectMat");
        }

        @Override
        public void onWrite(Map<String, Object> config) {
        }
    }

}
