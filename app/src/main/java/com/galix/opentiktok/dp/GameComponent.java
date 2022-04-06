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

import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Map;

import static org.opencv.core.CvType.CV_32F;

public class GameComponent extends AVComponent {

    private AVVideo mCoachVideo;
    private AVVideo mPlayerTestVideo;
    private AVPag mScreenEffect;
    private AVPag mPlayerEffect;
    private String mCoachPath;
    private String mPlayerTestPath;
    private String mScreenEffectPath;
    private String mPlayerEffectPath;
    private String mBeautyLutPath;
    private String mPlayerLutPath;
    private ByteBuffer mTestPlayerByteBuffer;
    private GameInfo mGameInfo;
    public WeakReference<Context> mContext;

    public static class GameInfo {
        //必须设置
        public SurfaceTexture coachSurfaceTexture;
        public SurfaceTexture playerSurfaceTexture;
        public GLTexture coachTexture = new GLTexture(0, true);//教练输入
        public GLTexture playerTexture = new GLTexture(0, true);//用户输入
        public GLTexture screenEffectTexture = new GLTexture(0, false);//全屏特效
        public GLTexture playerEffectTexture = new GLTexture(0, false);//用户身上特效
        public Size videoSize = new Size(1920, 1080);//画面大小
        public boolean useBeauty;//启用美颜
        public boolean usePlayerEffect;//启用用户特效
        public Bitmap beautyLut;//美颜Lut
        public Bitmap playerLut;//用户Lut
        public ByteBuffer playerMaskBuffer;//用户Mask
        public Size playerMaskSize = new Size(256, 204);//用户Mask大小
        public Rect playerMaskRoi = new Rect(276, 234, 276 + 1059, 234 + 845);//用户MASK ROI

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
                         String beautyLutPath,
                         String playerLutPath,
                         boolean useBeauty,
                         IRender render) {
        super(engineStartTime, AVComponentType.VIDEO, render);
        mGameInfo = new GameInfo();
        this.mContext = new WeakReference<>(context);
        this.mCoachPath = coachPath;
        this.mPlayerTestPath = playerTestVideoPath;
        this.mScreenEffectPath = screenEffectPath;
        this.mPlayerEffectPath = playerEffectPath;
        this.mBeautyLutPath = beautyLutPath;
        this.mPlayerLutPath = playerLutPath;
        mGameInfo.useBeauty = useBeauty;
    }

    @Override
    public int open() {
        if (isOpen()) return RESULT_OK;
        try {
            mTestPlayerByteBuffer = IOUtils.read(mContext.get().getResources().openRawResource(R.raw.playmask), 52224);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mTestPlayerByteBuffer.position(0);
        mCoachVideo = new AVVideo(true, getEngineStartTime(), mCoachPath, null);
        mPlayerTestVideo = new AVVideo(true, getEngineStartTime(), mPlayerTestPath, null);
        mScreenEffect = new AVPag(mContext.get().getAssets(), mScreenEffectPath, getEngineStartTime(), null);
        mScreenEffect.setLoop(true);
        mPlayerEffect = new AVPag(mContext.get().getAssets(), mPlayerEffectPath, getEngineStartTime(), null);
        mPlayerEffect.setLoop(true);
        try {
            mGameInfo.beautyLut = BitmapFactory.decodeStream(mContext.get().getAssets().open(mBeautyLutPath));
            mGameInfo.playerLut = BitmapFactory.decodeStream(mContext.get().getAssets().open(mPlayerLutPath));
        } catch (IOException e) {
            e.printStackTrace();
        }

        //打开各个组件
        mCoachVideo.open();
        mPlayerTestVideo.open();
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
        mPlayerTestVideo.close();
        mScreenEffect.close();
        mPlayerEffect.close();
        return RESULT_OK;
    }

    @Override
    public int write(Map<String, Object> configs) {
        if (configs.containsKey("player_lut")) {
            mPlayerLutPath = (String) configs.get("player_lut");
            try {
                mGameInfo.playerLut = BitmapFactory.decodeStream(mContext.get().getAssets().open(mPlayerLutPath));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (configs.containsKey("beauty_lut")) {
            mPlayerLutPath = (String) configs.get("beauty_lut");
            try {
                mGameInfo.beautyLut = BitmapFactory.decodeStream(mContext.get().getAssets().open(mBeautyLutPath));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (configs.containsKey("use_beauty")) {
            mGameInfo.useBeauty = (boolean) configs.get("use_beauty");
        }
        return super.write(configs);
    }

    @Override
    public int readFrame() {
        mCoachVideo.readFrame();
        mPlayerTestVideo.readFrame();
        mScreenEffect.readFrame();
        mPlayerEffect.readFrame();
        freshFrame();
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        mCoachVideo.seekFrame(position);
        mPlayerTestVideo.seekFrame(position);
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
        mGameInfo.playerTexture.idAsBuf().put(mPlayerTestVideo.peekFrame().getTexture());
        mGameInfo.playerTexture.setSize(mPlayerTestVideo.peekFrame().getRoi().width(), mPlayerTestVideo.peekFrame().getRoi().height());
        mGameInfo.playerSurfaceTexture = mPlayerTestVideo.peekFrame().getSurfaceTexture();
        mGameInfo.playerMaskBuffer = mTestPlayerByteBuffer;//要改
//        mDpInfo.playerMaskRoi =xxxx;//要改
//        mDpInfo.playerMaskSize = xxxx;//要改

        //全屏特效
        mGameInfo.screenEffectTexture.idAsBuf().put(mScreenEffect.peekFrame().getTexture());
        mGameInfo.screenEffectTexture.setSize(mScreenEffect.peekFrame().getRoi().width(),
                mScreenEffect.peekFrame().getRoi().height());

        //用户特效
        mGameInfo.playerEffectTexture.idAsBuf().put(mPlayerEffect.peekFrame().getTexture());
        mGameInfo.playerEffectTexture.setSize(mPlayerEffect.peekFrame().getRoi().width(),
                mPlayerEffect.peekFrame().getRoi().height());

        //作为私有数据
        peekFrame().setExt(mGameInfo);
        peekFrame().setRoi(mCoachVideo.peekFrame().getRoi());
        peekFrame().setPts(mCoachVideo.peekFrame().getPts());
        peekFrame().setEof(mCoachVideo.peekFrame().isEof());
        peekFrame().setDuration(mCoachVideo.peekFrame().getDuration());
        peekFrame().setValid(true);
    }
}
