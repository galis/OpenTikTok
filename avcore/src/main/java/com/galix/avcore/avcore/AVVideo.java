package com.galix.avcore.avcore;

import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.opengl.GLES30;
import android.util.Size;
import android.view.Surface;

import com.galix.avcore.render.IRender;
import com.galix.avcore.util.LogUtil;
import com.galix.avcore.util.TimeUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;

/**
 * 视频组件
 */
public class AVVideo extends AVComponent {
    private static final String TAG = AVVideo.class.getSimpleName();
    //    private int textureId;
    private boolean isInputEOF;
    private boolean isOutputEOF;
    private String path;
    private MediaCodec mediaCodec;
    private MediaExtractor mediaExtractor;
    private int gop = 0;
    private MediaFormat mediaFormat;
    private Surface surface;
    private SurfaceTexture surfaceTexture;
    private Size videoSize;
    private boolean isTextureType;
    private int frameRate = 0;

    //输出到surface
    public AVVideo(boolean isTextureType, long engineStartTime, String path, IRender render) {
        super(engineStartTime, AVComponentType.VIDEO, render);
        this.isTextureType = isTextureType;
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Size getVideoSize() {
        return videoSize;
    }

    public int getFrameRate() {
        return frameRate;
    }

    @Override
    public int open() {
        if (isOpen()) return RESULT_FAILED;
        peekFrame().setValid(false);//TODO release
        mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(path);
            for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                if (mediaExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).contains("video")) {
                    mediaFormat = mediaExtractor.getTrackFormat(i);
                    mediaExtractor.selectTrack(i);
                    mediaCodec = MediaCodec.createDecoderByType(mediaExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME));
                    frameRate = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
                    videoSize = new Size(mediaFormat.getInteger(MediaFormat.KEY_WIDTH), mediaFormat.getInteger(MediaFormat.KEY_HEIGHT));
                    long duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
                    setClipStartTime(0);
                    setClipEndTime(duration);
                    setDuration(duration);
                    break;
                }
            }
            if (mediaCodec == null) {
                return RESULT_FAILED;
            }
            peekFrame().setByteBuffer(ByteBuffer.allocateDirect(mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)));
            peekFrame().setRoi(new Rect(0, 0, mediaFormat.getInteger(MediaFormat.KEY_WIDTH), mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)));
            if (isTextureType) {
                int[] textures = new int[1];
                GLES30.glGenTextures(1, textures, 0);
                surfaceTexture = new SurfaceTexture(textures[0]);
                surface = new Surface(surfaceTexture);
                peekFrame().setTexture(textures[0]);
                peekFrame().getTexture().setOes(true);
                peekFrame().getTexture().setSize(mediaFormat.getInteger(MediaFormat.KEY_WIDTH), mediaFormat.getInteger(MediaFormat.KEY_HEIGHT));
                peekFrame().setSurfaceTexture(surfaceTexture);
                mediaCodec.configure(mediaFormat, surface, null, 0);
            } else {
                mediaCodec.configure(mediaFormat, null, null, 0);
            }
            mediaCodec.start();
            setEngineEndTime(getEngineStartTime() + getDuration());
            markOpen(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return RESULT_OK;
    }

    @Override
    public int close() {
        if (!isOpen()) return RESULT_FAILED;
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
        }
        if (mediaExtractor != null) {
            mediaExtractor.release();
            mediaExtractor = null;
        }
        if (surfaceTexture != null) {
            surfaceTexture.release();
            surfaceTexture = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
        if (getRender() != null) {
            getRender().close();
            setRender(null);
        }
        isInputEOF = false;
        isOutputEOF = false;
        markOpen(false);
        return RESULT_OK;
    }

    @Override
    public int readFrame() {
        if (!isOpen() || isOutputEOF) return RESULT_FAILED;
        int outputBufIdx = -1;
        int inputBufIdx = -1;
        while (!isInputEOF || !isOutputEOF) {
            if (!isOutputEOF) {
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                try {
                    outputBufIdx = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (outputBufIdx >= 0) {
                    peekFrame().setValid(true);
                    if (bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM) {
                        isOutputEOF = true;
                        peekFrame().setEof(true);
                        peekFrame().setPts(getClipDuration() + getEngineStartTime());
                    } else {
                        peekFrame().setEof(false);
                        peekFrame().setPts(bufferInfo.presentationTimeUs - getClipStartTime() + getEngineStartTime());
                        LogUtil.logEngine("AVVideo#bufferInfo.presentationTimeUs#" + bufferInfo.presentationTimeUs);
                    }
                    peekFrame().setDuration(33333L);//TODO 无效
                    if (!isTextureType) {//no output surface texture
                        ByteBuffer byteBuffer = mediaCodec.getOutputBuffer(outputBufIdx);
                        peekFrame().getByteBuffer().put(byteBuffer);
                        byteBuffer.position(0);
                        peekFrame().getByteBuffer().position(0);
                        mediaCodec.releaseOutputBuffer(outputBufIdx, false);
                    } else {
                        mediaCodec.releaseOutputBuffer(outputBufIdx, true);
                        surfaceTexture.updateTexImage();
                    }
                    break;
                } else if (outputBufIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    LogUtil.logEngine(LogUtil.ENGINE_TAG + "readFrame()#INFO_TRY_AGAIN_LATER:" + bufferInfo.presentationTimeUs);
                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    LogUtil.logEngine(LogUtil.ENGINE_TAG + "readFrame()#INFO_OUTPUT_BUFFERS_CHANGED:" + bufferInfo.presentationTimeUs);
                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    LogUtil.logEngine(LogUtil.ENGINE_TAG + "readFrame()#INFO_OUTPUT_FORMAT_CHANGED:" + bufferInfo.presentationTimeUs);
                }
            }
            if (!isInputEOF) {
                inputBufIdx = mediaCodec.dequeueInputBuffer(0);
                if (inputBufIdx >= 0) {
                    int sampleSize = mediaExtractor.readSampleData(peekFrame().getByteBuffer(), 0);
                    if (sampleSize < 0) {
                        sampleSize = 0;
                        isInputEOF = true;
                        LogUtil.log(LogUtil.ENGINE_TAG + "readFrame()#isInputEOF");
                    }
                    mediaCodec.getInputBuffer(inputBufIdx).put(peekFrame().getByteBuffer());
                    mediaCodec.queueInputBuffer(inputBufIdx, 0,
                            sampleSize,
                            mediaExtractor.getSampleTime(),
                            isInputEOF ? BUFFER_FLAG_END_OF_STREAM : 0);
                    mediaExtractor.advance();
                    LogUtil.logEngine("mediaExtractor.getSampleTime()#" + mediaExtractor.getSampleTime());
                }
            }
//            if (outputBufIdx == -1 && inputBufIdx == -1) {
//                LogUtil.logEngine("Something#Error!");
//            }
        }
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        if (!isOpen()) return RESULT_FAILED;
        LogUtil.logEngine("seekFrame#start#" + position);
        long correctPosition = position - getEngineStartTime();//engine position => file position
        if (position < getEngineStartTime() || position > getEngineEndTime() || correctPosition > getEngineDuration()) {
            return RESULT_FAILED;
        }
        isInputEOF = false;
        isOutputEOF = false;
        if (position < getPosition()) {
            LogUtil.logEngine("seekFrame#SEEK SYNC#" + position + "#" + getPosition());
            mediaExtractor.seekTo(correctPosition + getClipStartTime(), MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            mediaCodec.flush();
            peekFrame().setPts(Long.MIN_VALUE);
        }
        while (peekFrame().getPts() < position) {
            LogUtil.logEngine("seekFrame#readFrame#" + peekFrame().getPts() + "#" + position);
            TimeUtils.RecordStart("seekFrame");
            readFrame();
            TimeUtils.RecordEnd("seekFrame");
            LogUtil.log(LogUtil.ENGINE_TAG + "AVVideo#seekframe()" + peekFrame().getPts());
        }
        setPosition(position);
        LogUtil.logEngine("seekFrame#end");
        return RESULT_OK;
    }

    @Override
    public String toString() {
        return "AVVideo{\n" +
                "\tisInputEOF=" + isInputEOF + ",\n" +
                "\tisOutputEOF=" + isOutputEOF + ",\n" +
                "\tpath='" + path + '\'' + ",\n" +
                "\tmediaCodec=" + mediaCodec + ",\n" +
                "\tmediaExtractor=" + mediaExtractor + ",\n" +
                "\tgop=" + gop + ",\n" +
                "\tmediaFormat=" + mediaFormat + ",\n" +
                "\tsurface=" + surface + ",\n" +
                "\tsurfaceTexture=" + surfaceTexture + ",\n" +
                "\tvideoSize=" + videoSize + ",\n" +
                "\tisTextureType=" + isTextureType + ",\n" +
                "\tframeRate=" + frameRate + ",\n" +
                "} " + super.toString() + "\n";
    }
}
