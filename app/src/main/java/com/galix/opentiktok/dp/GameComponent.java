package com.galix.opentiktok.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.opengl.GLES30;
import android.util.Size;

import com.galix.avcore.avcore.AVComponent;
import com.galix.avcore.avcore.AVFrame;
import com.galix.avcore.avcore.AVPag;
import com.galix.avcore.avcore.AVVideo;
import com.galix.avcore.render.IRender;
import com.galix.avcore.render.filters.GLTexture;
import com.galix.avcore.util.IOUtils;
import com.galix.avcore.util.MathUtils;
import com.galix.opentiktok.R;

import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Map;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_LUMINANCE;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.GL_UNPACK_ALIGNMENT;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glDeleteTextures;
import static android.opengl.GLES20.glTexParameterf;
import static android.opengl.GLES20.glTexParameteri;

public class GameComponent extends AVComponent {

    private AVVideo mCoachVideo;
    private AVComponent mPlayerComponent;
    //    private AVPag mPlayerEffect;
    private String mCoachPath;
    private String mPlayerTestPath;
    private String mPlayerEffectPath;
    private GameInfo mGameInfo = new GameInfo();
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
                         String playerEffectPath,
                         Bitmap beautyLut,
                         Bitmap playerLut,
                         boolean useBeauty,
                         IRender render) {
        super(engineStartTime, AVComponentType.VIDEO, render);
        mGameInfo.useBeauty = useBeauty;
        mGameInfo.beautyLut = beautyLut;
        mGameInfo.playerLut = playerLut;
        this.mContext = new WeakReference<>(context);
        this.mCoachPath = coachPath;
        this.mPlayerTestPath = playerTestVideoPath;
        this.mPlayerEffectPath = playerEffectPath;
    }

    public GameComponent(Context context,
                         long engineStartTime,
                         String coachPath,
                         AVComponent dpComponent,
                         String playerEffectPath,
                         Bitmap beautyLut,
                         Bitmap playerLut,
                         boolean useBeauty,
                         IRender render) {
        super(engineStartTime, AVComponentType.VIDEO, render);
        mGameInfo.useBeauty = useBeauty;
        mGameInfo.beautyLut = beautyLut;
        mGameInfo.playerLut = playerLut;
        this.mContext = new WeakReference<>(context);
        this.mCoachPath = coachPath;
        this.mPlayerComponent = dpComponent;
        this.mPlayerEffectPath = playerEffectPath;
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
//        mPlayerEffect = new AVPag(mContext.get().getAssets(), mPlayerEffectPath, Long.MAX_VALUE, null);

        //打开各个组件
        mCoachVideo.open();
        mPlayerComponent.open();
//        mPlayerEffect.open();
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
        mCoachVideo.close();
        mPlayerComponent.close();
//        mPlayerEffect.close();
        gameInfoCloseIfNeed();
        return RESULT_OK;
    }

    private void gameInfoCloseIfNeed() {
        mGameInfo.playerMaskInfo.playerMaskBuffer = null;
        mGameInfo.playerEffectTexture.release();
        mGameInfo.playerMaskTexture.release();
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
        if (configs.containsKey("player_effect_duration")) {
            long playTime = peekFrame().getPts() + 60000;
            mGameInfo.playerEffectDuration = (long) configs.get("player_effect_duration");
//            mPlayerEffect.lock();
//            mPlayerEffect.setLoop(mGameInfo.playerEffectDuration == -1);
//            mPlayerEffect.setEngineStartTime(playTime);
//            mPlayerEffect.setEngineEndTime(mPlayerEffect.isLoop() ? Long.MAX_VALUE : playTime + mGameInfo.playerEffectDuration);
//            mPlayerEffect.seekFrame(playTime);
//            mPlayerEffect.unlock();
        }
        return super.write(configs);
    }

    @Override
    public int readFrame() {
        mCoachVideo.readFrame();
        mPlayerComponent.readFrame();
//        if (peekFrame().getPts() >= mPlayerEffect.getEngineStartTime()) {
//            mPlayerEffect.readFrame();
//        }
        freshFrame();
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        mCoachVideo.seekFrame(position);
        mPlayerComponent.seekFrame(position);
//        mPlayerEffect.seekFrame(position);
        freshFrame();
        return RESULT_OK;
    }

    private void freshFrame() {

        //教练
        mGameInfo.coachTexture = mCoachVideo.peekFrame().getTexture();
        mGameInfo.coachSurfaceTexture = mCoachVideo.peekFrame().getSurfaceTexture();

        //玩家
        mGameInfo.playerTexture = mPlayerComponent.peekFrame().getTexture();
        mGameInfo.playerSurfaceTexture = mPlayerComponent.peekFrame().getSurfaceTexture();
        mGameInfo.playerMaskInfo = (PlayerMaskInfo) mPlayerComponent.peekFrame().getExt();

        //用户特效
//        if (mPlayerEffect.peekFrame().isValid()) {
//            mGameInfo.playerEffectTexture = mPlayerEffect.peekFrame().getTexture();
//        } else {
        mGameInfo.playerEffectTexture.idAsBuf().put(0);
        mGameInfo.playerEffectTexture.setSize(16, 16);
//        }

        //作为私有数据
        peekFrame().setExt(mGameInfo);
        peekFrame().setRoi(mCoachVideo.peekFrame().getRoi());
        peekFrame().setPts(mCoachVideo.peekFrame().getPts());
        peekFrame().setEof(mCoachVideo.peekFrame().isEof());
        peekFrame().setDuration(mCoachVideo.peekFrame().getDuration());
        peekFrame().setValid(true);

        renderReady(peekFrame());
    }

    private void renderReady(AVFrame avFrame) {
        GameComponent.GameInfo gameInfo = (GameComponent.GameInfo) avFrame.getExt();
        gameInfo.playerSurfaceTexture.updateTexImage();
        gameInfo.coachSurfaceTexture.updateTexImage();
//        dpInfo.effectSurfaceTexture.updateTexImage();

        if (gameInfo.playerMaskInfo.playerMaskBuffer != null) {
            if (gameInfo.playerMaskTexture.id() != 0) {
                glDeleteTextures(1, gameInfo.playerMaskTexture.idAsBuf());
            }
            GLES30.glGenTextures(1, gameInfo.playerMaskTexture.idAsBuf());
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glBindTexture(GL_TEXTURE_2D, gameInfo.playerMaskTexture.id());
            GLES30.glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            GLES30.glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE,
                    gameInfo.playerMaskInfo.playerMaskSize.getWidth(),
                    gameInfo.playerMaskInfo.playerMaskSize.getHeight(),
                    0, GL_LUMINANCE, GL_UNSIGNED_BYTE,
                    gameInfo.playerMaskInfo.playerMaskBuffer);//注意检查 dpInfo.playerMaskBuffer position==0 limit==width*height
            GLES30.glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            gameInfo.playerTexture.setSize(gameInfo.playerMaskInfo.playerMaskSize.getWidth(),
                    gameInfo.playerMaskInfo.playerMaskSize.getHeight());
        } else {
            gameInfo.playerMaskTexture.idAsBuf().put(0);
            gameInfo.playerTexture.setSize(0, 0);
        }
        gameInfo.playerMaskTexture.idAsBuf().position(0);

        //根据roi计算 mask mat
        gameInfo.srcPoints[0].x = 0;
        gameInfo.srcPoints[0].y = 0;
        gameInfo.srcPoints[1].x = gameInfo.videoSize.getWidth();
        gameInfo.srcPoints[1].y = 0;
        gameInfo.srcPoints[2].x = 0;
        gameInfo.srcPoints[2].y = gameInfo.videoSize.getHeight();

        gameInfo.dstPoints[0].x = gameInfo.playerMaskInfo.playerMaskRoi.left;
        gameInfo.dstPoints[0].y = gameInfo.playerMaskInfo.playerMaskRoi.top;
        gameInfo.dstPoints[1].x = gameInfo.playerMaskInfo.playerMaskRoi.left + gameInfo.playerMaskInfo.playerMaskRoi.width();
        gameInfo.dstPoints[1].y = gameInfo.dstPoints[0].y;
        gameInfo.dstPoints[2].x = gameInfo.playerMaskInfo.playerMaskRoi.left;
        gameInfo.dstPoints[2].y = gameInfo.dstPoints[0].y + gameInfo.playerMaskInfo.playerMaskRoi.height();
        Mat resultMat = MathUtils.getTransform(gameInfo.srcPoints, gameInfo.videoSize,
                gameInfo.dstPoints, gameInfo.videoSize);
        gameInfo.playerMaskMat.clear();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                gameInfo.playerMaskMat.put((float) resultMat.get(i, j)[0]);
            }
        }
        gameInfo.playerMaskMat.position(0);

        //根据roi计算 玩家特效 mat
        int targetHeight = (int) (gameInfo.playerMaskInfo.playerMaskRoi.width() *
                (gameInfo.playerEffectTexture.size().getHeight() * 1.0f /
                        gameInfo.playerEffectTexture.size().getWidth()));
        gameInfo.srcPoints[0].x = 0;
        gameInfo.srcPoints[0].y = 0;
        gameInfo.srcPoints[1].x = gameInfo.videoSize.getWidth();
        gameInfo.srcPoints[1].y = 0;
        gameInfo.srcPoints[2].x = 0;
        gameInfo.srcPoints[2].y = gameInfo.videoSize.getHeight();

        gameInfo.dstPoints[0].x = gameInfo.playerMaskInfo.playerMaskRoi.left;
        gameInfo.dstPoints[0].y = gameInfo.playerMaskInfo.playerMaskRoi.top + (gameInfo.playerMaskInfo.playerMaskRoi.height() - targetHeight);
        gameInfo.dstPoints[1].x = gameInfo.playerMaskInfo.playerMaskRoi.left + gameInfo.playerMaskInfo.playerMaskRoi.width();
        gameInfo.dstPoints[1].y = gameInfo.dstPoints[0].y;
        gameInfo.dstPoints[2].x = gameInfo.playerMaskInfo.playerMaskRoi.left;
        gameInfo.dstPoints[2].y = gameInfo.dstPoints[0].y + targetHeight;
        resultMat = MathUtils.getTransform(gameInfo.srcPoints, gameInfo.videoSize,
                gameInfo.dstPoints, gameInfo.videoSize);
        gameInfo.playerEffectMat.clear();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                gameInfo.playerEffectMat.put((float) resultMat.get(i, j)[0]);
            }
        }
        gameInfo.playerEffectMat.position(0);

    }
}
