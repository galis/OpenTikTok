package com.galix.avcore.avcore;

import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.opengl.GLES30;
import android.view.Surface;

import com.galix.avcore.render.IRender;
import com.galix.avcore.util.LogUtil;
import com.galix.avcore.util.OtherUtils;

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
    private MediaFormat mediaFormat;
    private Surface surface;
    private SurfaceTexture surfaceTexture;
    private boolean isTextureType;

    //输出到surface
    public AVVideo(boolean isTextureType, long engineStartTime, String path, IRender render) {
        super(engineStartTime, AVComponentType.VIDEO, render);
        this.isTextureType = isTextureType;
        this.path = path;
//        this.textureId = 0;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
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
        while (!isInputEOF || !isOutputEOF) {
            if (!isInputEOF) {
                int inputBufIdx = mediaCodec.dequeueInputBuffer(0);
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
                }
            }
            if (!isOutputEOF) {
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                int outputBufIdx = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                if (outputBufIdx >= 0) {
                    peekFrame().setValid(true);
                    if (bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM) {
                        isOutputEOF = true;
                        peekFrame().setEof(true);
                        peekFrame().setPts(getDuration() + getEngineStartTime());
                    } else {
                        peekFrame().setEof(false);
                        peekFrame().setPts(bufferInfo.presentationTimeUs - getClipStartTime() + getEngineStartTime());
                    }
                    peekFrame().setDuration((long) (1000000.f / 30));//TODO
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
                    LogUtil.log(LogUtil.ENGINE_TAG + "readFrame()#INFO_TRY_AGAIN_LATER:" + bufferInfo.presentationTimeUs);
                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    LogUtil.log(LogUtil.ENGINE_TAG + "readFrame()#INFO_OUTPUT_BUFFERS_CHANGED:" + bufferInfo.presentationTimeUs);
                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    LogUtil.log(LogUtil.ENGINE_TAG + "readFrame()#INFO_OUTPUT_FORMAT_CHANGED:" + bufferInfo.presentationTimeUs);
                }
            }
        }
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        if (!isOpen()) return RESULT_FAILED;
        long correctPosition = position - getEngineStartTime();//engine position => file position
        if (position < getEngineStartTime() || position > getEngineEndTime() || correctPosition > getEngineDuration()) {
            return RESULT_FAILED;
        }
        isInputEOF = false;
        isOutputEOF = false;
        mediaExtractor.seekTo(correctPosition + getClipStartTime(), MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        mediaCodec.flush();
        peekFrame().setPts(Long.MIN_VALUE);
        while (peekFrame().getPts() < position) {
            OtherUtils.RecordStart("seekFrame");
            readFrame();
            OtherUtils.RecordEnd("seekFrame");
            LogUtil.log(LogUtil.ENGINE_TAG + "AVVideo#seekframe()" + peekFrame().getPts());
        }
        return RESULT_OK;
    }

    @Override
    public String toString() {
        return "AVVideo{" +
                ", isInputEOF=" + isInputEOF +
                ", isOutputEOF=" + isOutputEOF +
                ", path='" + path + '\'' +
                ", mediaCodec=" + mediaCodec +
                ", mediaExtractor=" + mediaExtractor +
                ", mediaFormat=" + mediaFormat +
                ", surface=" + surface +
                ", surfaceTexture=" + surfaceTexture +
                ", isTextureType=" + isTextureType +
                "} " + super.toString();
    }
}
