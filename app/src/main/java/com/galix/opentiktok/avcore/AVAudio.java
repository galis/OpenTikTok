package com.galix.opentiktok.avcore;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import com.galix.opentiktok.render.IRender;

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

    public AVAudio(long srcStartTime, long srcEndTime, String path, IRender render) {
        super(srcStartTime, srcEndTime, AVComponentType.AUDIO, render);
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
                    setFileStartTime(0);
                    setFileEndTime(duration);
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
            if (!isInputEOF) {
                int inputBufIdx = mediaCodec.dequeueInputBuffer(0);
                if (inputBufIdx >= 0) {
                    int sampleSize = mediaExtractor.readSampleData(sampleBuffer, 0);
                    if (sampleSize < 0) {
                        sampleSize = 0;
                        isInputEOF = true;
                        Log.d(TAG, "isInputEOF");
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
                    }
                    ByteBuffer byteBuffer = mediaCodec.getOutputBuffer(outputBufIdx);
                    Log.d(TAG, "AVAudio#getOutputBuffer#size" + bufferInfo.size);
                    peekFrame().getByteBuffer().position(0);
                    peekFrame().getByteBuffer().put(byteBuffer);
                    byteBuffer.position(0);
                    peekFrame().getByteBuffer().position(0);
                    avFrame.setPts(bufferInfo.presentationTimeUs - getFileStartTime() + getEngineStartTime());//换算Engine的时间
                    avFrame.setValid(true);
                    mediaCodec.releaseOutputBuffer(outputBufIdx, false);
                    break;
                } else if (outputBufIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.d(TAG, "INFO_TRY_AGAIN_LATER:" + bufferInfo.presentationTimeUs);
                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED:" + bufferInfo.presentationTimeUs);
                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED:" + bufferInfo.presentationTimeUs);
                }
            }
        }
        avFrame.setEof(isOutputEOF);
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        if (!isOpen()) return RESULT_FAILED;
        long correctPosition = position - getEngineStartTime();
        if (position < getEngineStartTime() || position > getEngineEndTime() || correctPosition > getDuration()) {
            return RESULT_FAILED;
        }
        isInputEOF = false;
        isOutputEOF = false;
        mediaExtractor.seekTo(correctPosition + getFileStartTime(), MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        mediaCodec.flush();
        return readFrame();
    }

    @Override
    public String toString() {
        return "AVAudio{" +
                "isInputEOF=" + isInputEOF +
                ", isOutputEOF=" + isOutputEOF +
                ", path='" + path + '\'' +
                ", mediaCodec=" + mediaCodec +
                ", mediaExtractor=" + mediaExtractor +
                ", mediaFormat=" + mediaFormat +
                "} " + super.toString();
    }
}
