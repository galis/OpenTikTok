package com.galix.opentiktok.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.galix.avcore.avcore.AVComponent;
import com.galix.avcore.avcore.AVFrame;
import com.galix.avcore.avcore.AVVideo;
import com.galix.avcore.render.IRender;
import com.galix.opentiktok.R;

public class DpComponent extends AVComponent {

    private AVVideo mCoachVideo;
    private AVVideo mPlayerTestVideo;
    private String mCoachPath;
    private String mPlayerTestPath;
    private int mCoachTextureId;
    private int mPlayerTextureId;
    private AVFrame mFrame;
    private Bitmap mTestPlayerMaskBitmap;//这里写死
    public static Context context;

    public DpComponent(long engineStartTime, long engineEndTime, String coachPath, int coachTextureId, String playerTestVideoPath, int playerTextureId, IRender render) {
        super(engineStartTime, engineEndTime, AVComponentType.VIDEO, render);
        this.mCoachPath = coachPath;
        this.mPlayerTestPath = playerTestVideoPath;
        this.mCoachTextureId = coachTextureId;
        this.mPlayerTextureId = playerTextureId;
    }

    @Override
    public int open() {
        if (isOpen()) return RESULT_OK;
        mTestPlayerMaskBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.playmask);
        mCoachVideo = new AVVideo(getEngineStartTime(), getEngineEndTime(), mCoachPath, mCoachTextureId, null);
        mPlayerTestVideo = new AVVideo(getEngineStartTime(), getEngineEndTime(), mPlayerTestPath, mPlayerTextureId, null);
        mCoachVideo.open();
        mPlayerTestVideo.open();
        markOpen(true);
        return RESULT_OK;
    }

    @Override
    public int close() {
        if (!isOpen()) return RESULT_FAILED;
        mCoachVideo.close();
        mPlayerTestVideo.close();
        return RESULT_OK;
    }

    @Override
    public int readFrame() {
        mCoachVideo.readFrame();
        mPlayerTestVideo.readFrame();
        freshFrame();
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        mCoachVideo.seekFrame(position);
        mPlayerTestVideo.seekFrame(position);
        freshFrame();
        return RESULT_OK;
    }

    private void freshFrame() {
        peekFrame().setPts(mCoachVideo.peekFrame().getPts());
        peekFrame().setRoi(mCoachVideo.peekFrame().getRoi());
        peekFrame().setEof(mCoachVideo.peekFrame().isEof());
        peekFrame().setSurfaceTexture(mCoachVideo.peekFrame().getSurfaceTexture());
        peekFrame().setDuration(mCoachVideo.peekFrame().getDuration());
        peekFrame().setTexture(mCoachVideo.peekFrame().getTexture());
        peekFrame().setTextureExt(mPlayerTestVideo.peekFrame().getTexture());
        peekFrame().setSurfaceTextureExt(mPlayerTestVideo.peekFrame().getSurfaceTexture());
        peekFrame().setBitmap(mTestPlayerMaskBitmap);
        peekFrame().setValid(true);
    }
}
