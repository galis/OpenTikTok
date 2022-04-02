package com.galix.opentiktok.dp;

import android.content.Context;
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
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.opencv.core.CvType.CV_32F;

public class DpComponent extends AVComponent {

    private AVVideo mCoachVideo;
    private AVVideo mPlayerTestVideo;
    private AVPag mPagEffect;
    private String mCoachPath;
    private String mPlayerTestPath;
    private ByteBuffer mTestPlayerByteBuffer;
    private DpInfo mDpInfo;
    public static Context context;

    public static class DpInfo {
        //必须设置
        public SurfaceTexture coachSurfaceTexture;
        public SurfaceTexture playerSurfaceTexture;
        //        public SurfaceTexture effectSurfaceTexture;
        public GLTexture coachTexture = new GLTexture(0, true);
        public GLTexture playerTexture = new GLTexture(0, true);
        public GLTexture effectTexture = new GLTexture(0, false);
        public Size videoSize = new Size(1920, 1080);

        //mask相关
        public ByteBuffer playerMaskBuffer;
        public Size playerMaskSize = new Size(256, 204);
        public Rect playerMaskRoi = new Rect(276, 234, 276 + 1059, 234 + 845);

        //以下是手动计算
        public Point[] srcPoints;
        public Point[] dstPoints;
        public GLTexture playerMaskTexture = new GLTexture(0, false);
        public Mat playerMaskMat = Mat.eye(3, 3, CV_32F);
        public FloatBuffer playerMaskMatBuffer = FloatBuffer.allocate(3 * 3);

        public DpInfo() {
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

    public DpComponent(long engineStartTime, String coachPath, String playerTestVideoPath, IRender render) {
        super(engineStartTime, AVComponentType.VIDEO, render);
        this.mCoachPath = coachPath;
        this.mPlayerTestPath = playerTestVideoPath;
    }

    @Override
    public int open() {
        if (isOpen()) return RESULT_OK;
        mDpInfo = new DpInfo();
        try {
            mTestPlayerByteBuffer = IOUtils.read(context.getResources().openRawResource(R.raw.playmask), 52224);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mTestPlayerByteBuffer.position(0);
        mCoachVideo = new AVVideo(true, getEngineStartTime(), mCoachPath, null);
        mPlayerTestVideo = new AVVideo(true, getEngineStartTime(), mPlayerTestPath, null);
        mPagEffect = new AVPag("/data/data/com.galix.opentiktok/cache/test.pag", getEngineStartTime(), null);
        mCoachVideo.open();
        mPlayerTestVideo.open();
        mPagEffect.open();
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
        mDpInfo = null;
        mCoachVideo.close();
        mPlayerTestVideo.close();
        mPagEffect.close();
        return RESULT_OK;
    }

    @Override
    public int readFrame() {
        mCoachVideo.readFrame();
        mPlayerTestVideo.readFrame();
        mPagEffect.readFrame();
        freshFrame();
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        mCoachVideo.seekFrame(position);
        mPlayerTestVideo.seekFrame(position);
        mPagEffect.seekFrame(position);
        freshFrame();
        return RESULT_OK;
    }

    private void freshFrame() {

        //教练
        mDpInfo.coachTexture.idAsBuf().put(mCoachVideo.peekFrame().getTexture());
        mDpInfo.coachTexture.setSize(mCoachVideo.peekFrame().getRoi().width(), mCoachVideo.peekFrame().getRoi().height());
        mDpInfo.coachSurfaceTexture = mCoachVideo.peekFrame().getSurfaceTexture();

        //玩家
        mDpInfo.playerTexture.idAsBuf().put(mPlayerTestVideo.peekFrame().getTexture());
        mDpInfo.playerTexture.setSize(mPlayerTestVideo.peekFrame().getRoi().width(), mPlayerTestVideo.peekFrame().getRoi().height());
        mDpInfo.playerSurfaceTexture = mPlayerTestVideo.peekFrame().getSurfaceTexture();
        mDpInfo.playerMaskBuffer = mTestPlayerByteBuffer;//要改
//        mDpInfo.playerMaskRoi =xxxx;//要改
//        mDpInfo.playerMaskSize = xxxx;//要改

        //特效
//        mDpInfo.effectSurfaceTexture = mPagEffect.peekFrame().getSurfaceTexture();
        mDpInfo.effectTexture.idAsBuf().put(mPagEffect.peekFrame().getTexture());
        mDpInfo.effectTexture.setSize(mPagEffect.peekFrame().getRoi().width(), mPagEffect.peekFrame().getRoi().height());

        //作为私有数据
        peekFrame().setExt(mDpInfo);
        peekFrame().setRoi(mCoachVideo.peekFrame().getRoi());
        peekFrame().setPts(mCoachVideo.peekFrame().getPts());
        peekFrame().setEof(mCoachVideo.peekFrame().isEof());
        peekFrame().setDuration(mCoachVideo.peekFrame().getDuration());
        peekFrame().setValid(true);
    }
}
