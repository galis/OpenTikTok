package com.galix.opentiktok.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.util.Size;

import com.galix.avcore.avcore.AVComponent;
import com.galix.avcore.avcore.AVPag;
import com.galix.avcore.avcore.AVVideo;
import com.galix.avcore.render.IRender;
import com.galix.avcore.render.filters.GLTexture;
import com.galix.avcore.util.IOUtils;
import com.galix.opentiktok.R;

import org.opencv.core.Point;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Map;

public class GameComponent extends AVComponent {

    private AVVideo mCoachVideo;
    private AVComponent mPlayerComponent;
    private AVPag mScreenEffect;
    private AVPag mPlayerEffect;
    private String mCoachPath;
    private String mPlayerTestPath;
    private String mScreenEffectPath;
    private String mPlayerEffectPath;
    //    private String mBeautyLutPath;
//    private String mPlayerLutPath;
    private GameInfo mGameInfo;
    public WeakReference<Context> mContext;

    public static class PlayerMaskInfo {
        public ByteBuffer playerMaskBuffer;//用户Mask
        public Size playerMaskSize;//用户Mask大小
        public Rect playerMaskRoi;//用户MASK ROI
    }

    public static class GameInfo {
        //必须设置
        public SurfaceTexture coachSurfaceTexture;
        public SurfaceTexture playerSurfaceTexture;
        public GLTexture coachTexture = new GLTexture(0, true);//教练输入
        public GLTexture playerTexture = new GLTexture(0, true);//用户输入
        public GLTexture screenEffectTexture = new GLTexture(0, false);//全屏特效
        public GLTexture playerEffectTexture = new GLTexture(0, false);//用户身上特效
        public Size videoSize = new Size(1920, 1080);//画面大小
        public long screenEffectDuration = 0;
        public long playerEffectDuration = 0;
        public boolean useBeauty;//启用美颜
        public boolean usePlayerEffect;//启用用户特效
        public Bitmap beautyLut;//美颜Lut
        public Bitmap playerLut;//用户Lut
        public PlayerMaskInfo playerMaskInfo;

        //以下是手动计算
        public Point[] srcPoints;
        public Point[] dstPoints;
        public GLTexture playerMaskTexture = new GLTexture(0, false);
        public FloatBuffer playerMaskMat = FloatBuffer.allocate(3 * 3);
        public FloatBuffer playerEffectMat = FloatBuffer.allocate(3 * 3);

        public GameInfo() {
            if (srcPoints == null) {
                srcPoints = new Point[3];
                for (int i = 0; i < 3; i++) {
                    srcPoints[i] = new Point();
                }
            }
            if (dstPoints == null) {
                dstPoints = new Point[3];
                for (int i = 0; i < 3; i++) {
                    dstPoints[i] = new Point();
                }
            }
        }
    }

    public GameComponent(Context context,
                         long engineStartTime,
                         String coachPath,
                         String playerTestVideoPath,
                         String screenEffectPath,
                         String playerEffectPath,
                         Bitmap beautyLut,
                         Bitmap playerLut,
                         boolean useBeauty,
                         IRender render) {
        super(engineStartTime, AVComponentType.VIDEO, render);
        mGameInfo = new GameInfo();
        mGameInfo.useBeauty = useBeauty;
        mGameInfo.beautyLut = beautyLut;
        mGameInfo.playerLut = playerLut;
        this.mContext = new WeakReference<>(context);
        this.mCoachPath = coachPath;
        this.mPlayerTestPath = playerTestVideoPath;
        this.mScreenEffectPath = screenEffectPath;
        this.mPlayerEffectPath = playerEffectPath;
    }

    public GameComponent(Context context,
                         long engineStartTime,
                         String coachPath,
                         AVComponent dpComponent,
                         String screenEffectPath,
                         String playerEffectPath,
                         Bitmap beautyLut,
                         Bitmap playerLut,
                         boolean useBeauty,
                         IRender render) {
        super(engineStartTime, AVComponentType.VIDEO, render);
        mGameInfo = new GameInfo();
        mGameInfo.useBeauty = useBeauty;
        mGameInfo.beautyLut = beautyLut;
        mGameInfo.playerLut = playerLut;
        this.mContext = new WeakReference<>(context);
        this.mCoachPath = coachPath;
        this.mPlayerComponent = dpComponent;
        this.mScreenEffectPath = screenEffectPath;
        this.mPlayerEffectPath = playerEffectPath;
//        this.mBeautyLutPath = beautyLutPath;
//        this.mPlayerLutPath = playerLutPath;
    }

    @Override
    public int open() {
        if (isOpen()) return RESULT_OK;
        mCoachVideo = new AVVideo(true, getEngineStartTime(), mCoachPath, null);
        if (!mPlayerTestPath.isEmpty()) {
            mPlayerComponent = new AVVideo(true, getEngineStartTime(), mPlayerTestPath, null);
            PlayerMaskInfo playerMaskInfo = new PlayerMaskInfo();
            playerMaskInfo.playerMaskSize = new Size(256, 204);//用户Mask大小
            playerMaskInfo.playerMaskRoi = new Rect(276, 234, 276 + 1059, 234 + 845);//用户MASK ROI
            try {
                playerMaskInfo.playerMaskBuffer = IOUtils.read(mContext.get().getResources().openRawResource(R.raw.playmask), 52224);
                playerMaskInfo.playerMaskBuffer.position(0);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mPlayerComponent.peekFrame().setExt(playerMaskInfo);
        }
        mScreenEffect = new AVPag(mContext.get().getAssets(), mScreenEffectPath, Long.MAX_VALUE, null);
        mPlayerEffect = new AVPag(mContext.get().getAssets(), mPlayerEffectPath, Long.MAX_VALUE, null);

        //打开各个组件
        mCoachVideo.open();
        mPlayerComponent.open();
        mScreenEffect.open();
        mPlayerEffect.open();
        setDuration(mCoachVideo.getDuration());
        setEngineEndTime(mCoachVideo.getEngineStartTime() + getDuration());
        setClipStartTime(0);
        setClipEndTime(getDuration());
        markOpen(true);
        return RESULT_OK;
    }

    @Override
    public int close() {
        if (!isOpen()) return RESULT_FAILED;
        mGameInfo = null;
        mCoachVideo.close();
        mPlayerComponent.close();
        mScreenEffect.close();
        mPlayerEffect.close();
        return RESULT_OK;
    }

    @Override
    public int write(Map<String, Object> configs) {
        if (configs.containsKey("player_lut")) {
            mGameInfo.playerLut = (Bitmap) configs.get("player_lut");
        }
        if (configs.containsKey("beauty_lut")) {
            mGameInfo.beautyLut = (Bitmap) configs.get("beauty_lut");
        }
        if (configs.containsKey("use_beauty")) {
            mGameInfo.useBeauty = (boolean) configs.get("use_beauty");
        }
        if (configs.containsKey("screen_effect_duration")) {
            long playTime = peekFrame().getPts() + 60000;
            mGameInfo.screenEffectDuration = (long) configs.get("screen_effect_duration");
            mScreenEffect.setLoop(mGameInfo.screenEffectDuration == -1);
            mScreenEffect.setEngineStartTime(playTime);//延迟一点播放
            mScreenEffect.setEngineEndTime(mScreenEffect.isLoop() ? Long.MAX_VALUE : playTime + mGameInfo.screenEffectDuration);
            mScreenEffect.seekFrame(playTime);
        }
        if (configs.containsKey("player_effect_duration")) {
            long playTime = peekFrame().getPts() + 60000;
            mGameInfo.playerEffectDuration = (long) configs.get("player_effect_duration");
            mPlayerEffect.setLoop(mGameInfo.playerEffectDuration == -1);
            mPlayerEffect.setEngineStartTime(playTime);
            mPlayerEffect.setEngineEndTime(mPlayerEffect.isLoop() ? Long.MAX_VALUE : playTime + mGameInfo.playerEffectDuration);
            mPlayerEffect.seekFrame(playTime);
        }
        return super.write(configs);
    }

    @Override
    public int readFrame() {
        mCoachVideo.readFrame();
        mPlayerComponent.readFrame();
        if (peekFrame().getPts() >= mScreenEffect.getEngineStartTime()) {
            mScreenEffect.readFrame();
        }
        if (peekFrame().getPts() >= mPlayerEffect.getEngineStartTime()) {
            mPlayerEffect.readFrame();
        }
        freshFrame();
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        mCoachVideo.seekFrame(position);
        mPlayerComponent.seekFrame(position);
        mScreenEffect.seekFrame(position);
        mPlayerEffect.seekFrame(position);
        freshFrame();
        return RESULT_OK;
    }

    private void freshFrame() {

        //教练
        mGameInfo.coachTexture.idAsBuf().put(mCoachVideo.peekFrame().getTexture());
        mGameInfo.coachTexture.setSize(mCoachVideo.peekFrame().getRoi().width(), mCoachVideo.peekFrame().getRoi().height());
        mGameInfo.coachSurfaceTexture = mCoachVideo.peekFrame().getSurfaceTexture();

        //玩家
        mGameInfo.playerTexture.idAsBuf().put(mPlayerComponent.peekFrame().getTexture());
        mGameInfo.playerTexture.setSize(mPlayerComponent.peekFrame().getRoi().width(), mPlayerComponent.peekFrame().getRoi().height());
        mGameInfo.playerSurfaceTexture = mPlayerComponent.peekFrame().getSurfaceTexture();
        mGameInfo.playerMaskInfo = (PlayerMaskInfo) mPlayerComponent.peekFrame().getExt();

        //全屏特效
        if (mScreenEffect.peekFrame().isValid()) {
            mGameInfo.screenEffectTexture.idAsBuf().put(mScreenEffect.peekFrame().getTexture());
            mGameInfo.screenEffectTexture.setSize(mScreenEffect.peekFrame().getRoi().width(),
                    mScreenEffect.peekFrame().getRoi().height());
        } else {
            mGameInfo.screenEffectTexture.idAsBuf().put(0);
            mGameInfo.screenEffectTexture.setSize(16, 16);
        }

        //用户特效
        if (mPlayerEffect.peekFrame().isValid()) {
            mGameInfo.playerEffectTexture.idAsBuf().put(mPlayerEffect.peekFrame().getTexture());
            mGameInfo.playerEffectTexture.setSize(mPlayerEffect.peekFrame().getRoi().width(),
                    mPlayerEffect.peekFrame().getRoi().height());
        } else {
            mGameInfo.playerEffectTexture.idAsBuf().put(0);
            mGameInfo.playerEffectTexture.setSize(16, 16);
        }

        //作为私有数据
        peekFrame().setExt(mGameInfo);
        peekFrame().setRoi(mCoachVideo.peekFrame().getRoi());
        peekFrame().setPts(mCoachVideo.peekFrame().getPts());
        peekFrame().setEof(mCoachVideo.peekFrame().isEof());
        peekFrame().setDuration(mCoachVideo.peekFrame().getDuration());
        peekFrame().setValid(true);
    }
}
