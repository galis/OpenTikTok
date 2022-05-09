package com.galix.avcore.avcore;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import com.galix.avcore.render.IRender;
import com.galix.avcore.util.LogUtil;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;

/**
 * 音频片段
 */
public class AVAudio extends AVComponent {

    public static final String TAG = AVComponent.class.getSimpleName();
    private boolean isInputEOF;
    private boolean isOutputEOF;
    private String path;
    private MediaCodec mediaCodec;
    private MediaExtractor mediaExtractor;
    private MediaFormat mediaFormat;
    private ByteBuffer sampleBuffer;

    public AVAudio(long engineStartTime, String path, IRender render) {
        super(engineStartTime, AVComponentType.AUDIO, render);
        this.path = path;
    }

    @Override
    public int open() {
        if (isOpen()) return RESULT_FAILED;
        isOutputEOF = false;
        isInputEOF = false;
        mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(path);
            for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                if (mediaExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).contains("audio")) {
                    mediaFormat = mediaExtractor.getTrackFormat(i);
                    mediaExtractor.selectTrack(i);
                    long duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
                    setClipStartTime(0);
                    setClipEndTime(duration);
                    setDuration(duration);
                    mediaCodec = MediaCodec.createDecoderByType(mediaExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME));
                    break;
                }
            }
            if (mediaCodec == null) {
                return RESULT_FAILED;
            }
            mediaCodec.configure(mediaFormat, null, null, 0);
            mediaCodec.start();
            sampleBuffer = ByteBuffer.allocateDirect(mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
            peekFrame().setByteBuffer(ByteBuffer.allocateDirect(4096));//TODO
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
        try {
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
                mediaCodec = null;
            }
            if (mediaExtractor != null) {
                mediaExtractor.release();
                mediaExtractor = null;
            }
        } catch (Exception e) {
            LogUtil.log("AVAudio#Error#close" + e.getMessage());
        }
        if (sampleBuffer != null) {
            sampleBuffer = null;
        }
        isInputEOF = false;
        isOutputEOF = false;
        markOpen(false);
        return RESULT_OK;
    }

    @Override
    public int readFrame() {//没打开或者已经返回一个eof frame,那么就返回RESULT_FAILED
        if (!isOpen() || isOutputEOF) return RESULT_FAILED;
        AVFrame avFrame = peekFrame();
        while (!isInputEOF || !isOutputEOF) {
            try {
                if (!isInputEOF) {
                    int inputBufIdx = mediaCodec.dequeueInputBuffer(0);
                    if (inputBufIdx >= 0) {
                        int sampleSize = mediaExtractor.readSampleData(sampleBuffer, 0);
                        if (sampleSize < 0) {
                            sampleSize = 0;
                            isInputEOF = true;
                            LogUtil.log("Audio readFrame()#isInputEOF");
                        }
                        mediaCodec.getInputBuffer(inputBufIdx).put(sampleBuffer);
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
                        if (bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM) {
                            isOutputEOF = true;
                            avFrame.setPts(getClipDuration() + getEngineStartTime());//换算Engine的时间
                            avFrame.setEof(true);
                        } else {
                            avFrame.setEof(false);
                            avFrame.setPts(bufferInfo.presentationTimeUs - getClipStartTime() + getEngineStartTime());//换算Engine的时间
                        }
                        ByteBuffer byteBuffer = mediaCodec.getOutputBuffer(outputBufIdx);
                        LogUtil.log(LogUtil.ENGINE_TAG + "readFrame()#getOutputBuffer#size" + bufferInfo.size + "#offset#" + bufferInfo.offset + "#pts#" + bufferInfo.presentationTimeUs);
                        peekFrame().getByteBuffer().position(0);
                        peekFrame().getByteBuffer().put(byteBuffer);
                        peekFrame().getByteBuffer().position(0);
                        peekFrame().setDuration(22320);//TODO
                        byteBuffer.position(0);
                        avFrame.setValid(true);
                        mediaCodec.releaseOutputBuffer(outputBufIdx, false);
                        break;
                    } else if (outputBufIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        LogUtil.log(LogUtil.ENGINE_TAG + "readFrame()#INFO_TRY_AGAIN_LATER:" + bufferInfo.presentationTimeUs);
                    } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        LogUtil.log(LogUtil.ENGINE_TAG + "readFrame()#INFO_OUTPUT_BUFFERS_CHANGED:" + bufferInfo.presentationTimeUs);
                    } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        LogUtil.log(LogUtil.ENGINE_TAG + "readFrame()#INFO_OUTPUT_FORMAT_CHANGED:" + bufferInfo.presentationTimeUs);
                    }
                }
            } catch (Exception e) {
                LogUtil.log(LogUtil.ENGINE_TAG + "readFrame()#Error#readFrame" + e.getMessage());
                retry(peekFrame().getPts() - getEngineStartTime() + getClipStartTime());
                return RESULT_FAILED;
            }
        }
        avFrame.setEof(isOutputEOF);
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        if (!isOpen()) return RESULT_FAILED;
        LogUtil.log(LogUtil.ENGINE_TAG + "seekFrame()");
        long correctPosition = position - getEngineStartTime();
        if (position < getEngineStartTime() || position > getEngineEndTime() || correctPosition > getDuration()) {
            return RESULT_FAILED;
        }
        isInputEOF = false;
        isOutputEOF = false;
        mediaExtractor.seekTo(correctPosition + getClipStartTime(), MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        mediaCodec.flush();
        return readFrame();
    }

    private void retry(long position) {
        LogUtil.log(LogUtil.ENGINE_TAG + "retry()#Error#close");
        close();
        LogUtil.log(LogUtil.ENGINE_TAG + "retry()#Error#open");
        open();
        LogUtil.log(LogUtil.ENGINE_TAG + "retry()#Error#seekFrame");
        seekFrame(position);
    }

    @Override
    public String toString() {
        return "AVAudio{\n" +
                "\tisInputEOF=" + isInputEOF + "\n" +
                "\tisOutputEOF=" + isOutputEOF + "\n" +
                "\tpath='" + path + '\'' + "\n" +
                "\tmediaCodec=" + mediaCodec + "\n" +
                "\tmediaExtractor=" + mediaExtractor + "\n" +
                "\tmediaFormat=" + mediaFormat + "\n" +
                "\tsampleBuffer=" + sampleBuffer + "\n" +
                "} " + super.toString() + "\n";
    }
}
