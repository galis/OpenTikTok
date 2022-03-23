package com.galix.opentiktok.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.galix.avcore.avcore.AVComponent;
import com.galix.avcore.avcore.AVFrame;
import com.galix.avcore.avcore.AVVideo;
import com.galix.avcore.render.IRender;
import com.galix.avcore.util.FileUtils;
import com.galix.avcore.util.IOUtils;
import com.galix.opentiktok.R;

import java.io.IOException;
import java.nio.ByteBuffer;

public class DpComponent extends AVComponent {

    private AVVideo mCoachVideo;
    private AVVideo mPlayerTestVideo;
    private String mCoachPath;
    private String mPlayerTestPath;
    private AVFrame mFrame;
    private Bitmap mTestPlayerMaskBitmap;//这里写死
    private ByteBuffer mTestPlayerByteBuffer;
    public static Context context;

    public DpComponent(long engineStartTime, long engineEndTime, String coachPath, int coachTextureId, String playerTestVideoPath, int playerTextureId, IRender render) {
        super(engineStartTime, engineEndTime, AVComponentType.VIDEO, render);
        this.mCoachPath = coachPath;
        this.mPlayerTestPath = playerTestVideoPath;
    }

    @Override
    public int open() {
        if (isOpen()) return RESULT_OK;
        mTestPlayerMaskBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.playmask);
        try {
            mTestPlayerByteBuffer = IOUtils.read(context.getResources().openRawResource(R.raw.playmask), 52224);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mTestPlayerByteBuffer.position(0);
        mCoachVideo = new AVVideo(true, getEngineStartTime(), getEngineEndTime(), mCoachPath, null);
        mPlayerTestVideo = new AVVideo(true, getEngineStartTime(), getEngineEndTime(), mPlayerTestPath, null);
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
        peekFrame().setByteBuffer(mTestPlayerByteBuffer);
//        peekFrame().setBitmap(mTestPlayerMaskBitmap);
//        peekFrame().setByteBuffer(mPlayerTestVideo.peekFrame().getByteBuffer());
        peekFrame().setValid(true);
    }
}
