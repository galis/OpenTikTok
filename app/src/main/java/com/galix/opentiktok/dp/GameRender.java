package com.galix.opentiktok.dp;

import android.util.Size;

import com.galix.avcore.avcore.AVFrame;
import com.galix.avcore.render.IVideoRender;
import com.galix.avcore.render.filters.BaseFilter;
import com.galix.avcore.render.filters.BeautyFilter;
import com.galix.avcore.render.filters.GLTexture;
import com.galix.avcore.render.filters.IFilter;
import com.galix.avcore.render.filters.LutFilter;
import com.galix.avcore.render.filters.OesFilter;
import com.galix.avcore.util.TimeUtils;
import com.galix.opentiktok.R;

import java.util.HashMap;
import java.util.Map;

import static com.galix.avcore.render.filters.IFilter.FBO_SIZE;
import static com.galix.avcore.render.filters.IFilter.INPUT_IMAGE;
import static com.galix.avcore.render.filters.IFilter.USE_FBO;

/**
 * 地平线渲染器
 */
public class GameRender implements IVideoRender {

    private static final String TAG = GameRender.class.getSimpleName();
    private HashMap<Class<?>, IFilter> mFilters;

    //参数
    private boolean mIsOpen = false;
    private Size mSurfaceSize = new Size(1920, 1080);
    private Size mBeautySize = new Size(1920 / 4, 1080 / 4);
    private Map<String, Object> mConfig = new HashMap<>();
    private GameComponent.GameInfo mCacheGameInfo;

    @Override
    public boolean isOpen() {
        return mIsOpen;
    }

    @Override
    public void open() {
        if (mIsOpen) return;
        registerFilter(LutFilter.class,
                GameFilter.class,
                OesFilter.class,
                BeautyFilter.class,
                PlayerFilter.class,
                LightOuterFilter.class);
        mIsOpen = true;
    }

    @Override
    public void close() {
        closeFilters();
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
        TimeUtils.RecordStart("oes_use_time");
        mConfig.clear();
        mConfig.put(USE_FBO, true);
        mConfig.put(FBO_SIZE, mCacheGameInfo.videoSize);
        mConfig.put(INPUT_IMAGE, mCacheGameInfo.playerTexture);
        mFilters.get(OesFilter.class).write(mConfig);
        mFilters.get(OesFilter.class).render();
        lastFilter = mFilters.get(OesFilter.class);
        TimeUtils.RecordEnd("oes_use_time");

        TimeUtils.RecordStart("player_use_time");
        mConfig.clear();
        mConfig.put(USE_FBO, true);
        mConfig.put(FBO_SIZE, mCacheGameInfo.videoSize);
        mConfig.put(INPUT_IMAGE, lastFilter.getOutputTexture());
        mConfig.put(PlayerFilter.PLAYER_MASK_TEXTURE, mCacheGameInfo.playerMaskTexture);
        mConfig.put(PlayerFilter.PLAYER_MASK_MAT, mCacheGameInfo.playerMaskMat);
        mFilters.get(PlayerFilter.class).write(mConfig);
        mFilters.get(PlayerFilter.class).render();
        lastFilter = mFilters.get(PlayerFilter.class);
        TimeUtils.RecordEnd("player_use_time");

        if (mCacheGameInfo.useBeauty) {
            //美颜
            TimeUtils.RecordStart("beauty_use_time");
            mConfig.clear();
            mConfig.put(BeautyFilter.USE_FBO, true);
            mConfig.put(BeautyFilter.FBO_SIZE, mBeautySize);
            mConfig.put(BeautyFilter.INPUT_IMAGE, lastFilter.getOutputTexture());
            mConfig.put(BeautyFilter.BEAUTY_LUT, mCacheGameInfo.beautyLut);
            mConfig.put(BeautyFilter.BEAUTY_ALPHA, 1.0f);
            mFilters.get(BeautyFilter.class).write(mConfig);
            mFilters.get(BeautyFilter.class).render();
            lastFilter = mFilters.get(BeautyFilter.class);
            TimeUtils.RecordEnd("beauty_use_time");
        }

        //用户lut变换.
        TimeUtils.RecordStart("lut_use_time");
        mConfig.clear();
        mConfig.put(USE_FBO, true);
        mConfig.put(FBO_SIZE, mCacheGameInfo.videoSize);
        mConfig.put(INPUT_IMAGE, lastFilter.getOutputTexture());
        mConfig.put(LutFilter.LUT_BITMAP, mCacheGameInfo.playerLut);
        mConfig.put(LutFilter.LUT_ALPHA, 1.0f);
        mFilters.get(LutFilter.class).write(mConfig);
        mFilters.get(LutFilter.class).render();
        lastFilter = mFilters.get(LutFilter.class);
        TimeUtils.RecordEnd("lut_use_time");

        //游戏画面合成.
        TimeUtils.RecordStart("game_use_time");
        mConfig.clear();
        mConfig.put("use_fbo", true);
        mConfig.put("fbo_size", mCacheGameInfo.videoSize);
        mConfig.put("coachTexture", mCacheGameInfo.coachTexture);
        mConfig.put("playerTexture", lastFilter.getOutputTexture());
        mConfig.put("playerEffectTexture", mCacheGameInfo.playerEffectTexture);
        mConfig.put("playerEffectMat", mCacheGameInfo.playerEffectMat);
        mFilters.get(GameFilter.class).write(mConfig);
        mFilters.get(GameFilter.class).render();
        TimeUtils.RecordEnd("game_use_time");
    }

    @Override
    public GLTexture getOutTexture() {
        return mFilters.get(GameFilter.class).getOutputTexture();
    }


    public static class GameFilter extends BaseFilter {

        public GameFilter() {
            super(R.raw.gamevs, R.raw.gamefs);
        }

        @Override
        public void onRenderPre() {
            bindTexture("coachTexture");
            bindTexture("playerTexture");
            bindTexture("screenEffectTexture");
            bindTexture("playerEffectTexture");
            bindMat3("playerEffectMat");
        }

    }

    public static class PlayerFilter extends BaseFilter {

        public static final String PLAYER_MASK_TEXTURE = "playerMaskTexture";
        public static final String PLAYER_MASK_MAT = "playerMaskMat";

        public PlayerFilter() {
            super(R.raw.player_vs, R.raw.player_fs);
        }

        @Override
        public void onRenderPre() {
            bindTexture(INPUT_IMAGE);
            bindTexture(PLAYER_MASK_TEXTURE);
            bindMat3(PLAYER_MASK_MAT);
        }

    }

    public static class LightOuterFilter extends BaseFilter {

        public LightOuterFilter() {
            super(R.raw.light_outer_vs, R.raw.light_outer_fs);
        }

        @Override
        public void onRenderPre() {
            bindTexture("inputImageTexture");
            bindVec2("fboSize");
            bindFloat("seed", Math.min(0.3f, (float) (Math.random() * 2 % 1.0 * 1.f)));
            bindFloat("alpha", 1.0f);
        }

    }

    private void registerFilter(Class... filterClass) {
        if (mFilters == null) {
            mFilters = new HashMap<>();
        }
        for (int i = 0; i < filterClass.length; i++) {
            try {
                IFilter filter = (IFilter) filterClass[i].newInstance();
                mFilters.put(filterClass[i], filter);
                filter.open();
            } catch (IllegalAccessException | InstantiationException e) {
                e.printStackTrace();
            }
        }
    }

    private void closeFilters() {
        if (mFilters == null) return;
        for (Map.Entry<Class<?>, IFilter> ii : mFilters.entrySet()) {
            ii.getValue().close();
        }
        mFilters.clear();
    }

}
