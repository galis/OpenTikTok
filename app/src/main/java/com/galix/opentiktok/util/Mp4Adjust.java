package com.galix.opentiktok.util;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;

public class Mp4Adjust {

    private static final String TAG = Mp4Adjust.class.getSimpleName();
    private int mGop;
    private int mVb;
    private int mAb;
    private String mSrcPath;
    private String mDstPath;

    private MediaExtractor mMediaExtractor;
    private MediaMuxer mMediaMuxer;
    private Stream mAudioDecodeStream, mVideoDecodeStream;
    private Stream mAudioEncodeStream, mVideoEncodeStream;

    public Mp4Adjust(int mGop, int mVb, int mAb, String mSrcPath, String mDstPath) {
        this.mGop = mGop;
        this.mVb = mVb;
        this.mAb = mAb;
        this.mSrcPath = mSrcPath;
        this.mDstPath = mDstPath;
        mAudioDecodeStream = mVideoDecodeStream = null;
        mAudioEncodeStream = mVideoEncodeStream = null;
    }

    private class Frame {
        ByteBuffer byteBuffer;
        long pts;
    }

    private class Stream {
        public int trackIdx;
        public long nextPts;
        public long duration;
        public MediaCodec mediaCodec;
        public MediaFormat format;
        public boolean isInputEOF;
        public boolean isOutputEOF;
        public ByteBuffer buffer;
        public MediaExtractor mediaExtractor;
        public Frame avFrame;
    }

    private void openDecodeStream(int trackIdx) {
        MediaFormat mediaFormat = mMediaExtractor.getTrackFormat(trackIdx);
        Stream stream = new Stream();
        stream.trackIdx = trackIdx;
        stream.duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
        stream.isInputEOF = stream.isOutputEOF = false;
        stream.buffer = ByteBuffer.allocateDirect(mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
        stream.mediaExtractor = new MediaExtractor();
        try {
            stream.mediaExtractor.setDataSource(mSrcPath);
            stream.mediaExtractor.selectTrack(trackIdx);
            stream.mediaCodec = MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));
            stream.mediaCodec.configure(mediaFormat, null, null, 0);
            stream.mediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (mediaFormat.getString(MediaFormat.KEY_MIME).contains("video")) {
            mVideoDecodeStream = stream;
        } else {
            mAudioDecodeStream = stream;
        }
    }

    private void openEncodeStream(int trackIdx) {
        MediaFormat mediaFormat = mMediaExtractor.getTrackFormat(trackIdx);
        if (mediaFormat.getString(MediaFormat.KEY_MIME).contains("video")) {
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mVb);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mGop);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        } else {
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mAb);//MP3jibie
        }
        Stream stream = new Stream();
        stream.duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
        stream.trackIdx = mMediaMuxer.addTrack(mediaFormat);
        stream.isInputEOF = stream.isOutputEOF = false;
        stream.buffer = ByteBuffer.allocateDirect(mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
        try {
            stream.mediaCodec = MediaCodec.createEncoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));
            stream.mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            stream.mediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        if (mediaFormat.getString(MediaFormat.KEY_MIME).contains("video")) {
            mVideoEncodeStream = stream;
        } else {
            mAudioEncodeStream = stream;
        }
    }


    private Frame readFrame(Stream stream) {
        if (stream == null || stream.isOutputEOF) return null;
        if (stream.avFrame == null) {
            stream.avFrame = new Frame();
        }
        Frame avFrame = stream.avFrame;
        MediaExtractor mediaExtractor = stream.mediaExtractor;
        while (!stream.isInputEOF || !stream.isOutputEOF) {
            if (!stream.isOutputEOF) {
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                int outputBufIdx = stream.mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                if (outputBufIdx >= 0) {
                    if (bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM) {
                        stream.isOutputEOF = true;
                    }
                    ByteBuffer byteBuffer = stream.mediaCodec.getOutputBuffer(outputBufIdx);
                    if (avFrame.byteBuffer == null) {
                        avFrame.byteBuffer = ByteBuffer.allocateDirect(bufferInfo.size);
                    }
                    avFrame.pts = bufferInfo.presentationTimeUs;
                    avFrame.byteBuffer.position(0);
                    avFrame.byteBuffer.put(byteBuffer);
                    stream.mediaCodec.releaseOutputBuffer(outputBufIdx, false);
                    return avFrame;
                } else if (outputBufIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.d(TAG, "INFO_TRY_AGAIN_LATER:" + bufferInfo.presentationTimeUs);
                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED:" + bufferInfo.presentationTimeUs);
                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED:" + bufferInfo.presentationTimeUs);
                }
            }
            if (!stream.isInputEOF) {
                int inputBufIdx = stream.mediaCodec.dequeueInputBuffer(0);
                if (inputBufIdx >= 0) {
                    int sampleSize = mediaExtractor.readSampleData(stream.buffer, 0);
                    if (sampleSize < 0) {
                        sampleSize = 0;
                        stream.isInputEOF = true;
                        Log.d(TAG, "isInputEOF");
                    }
                    stream.mediaCodec.getInputBuffer(inputBufIdx).put(stream.buffer);
                    stream.mediaCodec.queueInputBuffer(inputBufIdx, 0,
                            sampleSize,
                            mediaExtractor.getSampleTime(),
                            stream.isInputEOF ? BUFFER_FLAG_END_OF_STREAM : 0);
                    Log.d(TAG, "getSampleTime" + mediaExtractor.getSampleTime());
                    mediaExtractor.advance();
                }
            }
        }
        return null;
    }

    private void writeFrame(Stream stream, Frame frame) {
        MediaCodec mediaCodec = stream.mediaCodec;
        if (!stream.isInputEOF) {
            int status = mediaCodec.dequeueInputBuffer(-1);
            if (status >= 0) {
                if (frame != null) {
                    mediaCodec.getInputBuffer(status).put(frame.byteBuffer);
                }
                mediaCodec.queueInputBuffer(status, 0, frame == null ? 0 : frame.byteBuffer.limit(), frame == null ? -1 : frame.pts, frame == null ?
                        BUFFER_FLAG_END_OF_STREAM : 0);
            }
        }
        if (!stream.isOutputEOF) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean ret = false;
            while (!ret) {
                int status = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                if (status >= 0) {
                    if (bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM) {
                        stream.isOutputEOF = true;
                        stream.nextPts = stream.duration;
                    }
                    if (!stream.isOutputEOF) {
                        mMediaMuxer.writeSampleData(stream.trackIdx, mediaCodec.getOutputBuffer(status), bufferInfo);
                        stream.nextPts = bufferInfo.presentationTimeUs;
                    }
                    mediaCodec.releaseOutputBuffer(status, false);
                    ret = true;
                }
            }
        }
    }

    private void muxer() {
        mMediaMuxer.start();
        boolean hasVideo = mVideoEncodeStream != null && !mVideoEncodeStream.isOutputEOF;
        boolean hasAudio = mAudioEncodeStream != null && !mAudioEncodeStream.isOutputEOF;
        while ((hasVideo && !mVideoEncodeStream.isOutputEOF) || (hasAudio && !mAudioEncodeStream.isOutputEOF)) {
            boolean writeVideo = hasVideo && !hasAudio || hasVideo && mVideoEncodeStream.nextPts <= mAudioEncodeStream.nextPts;
            if (writeVideo) {
                Log.d(TAG, "writeVideo#pts" + mVideoEncodeStream.nextPts);
                writeFrame(mVideoEncodeStream, readFrame(mVideoDecodeStream));
            }
            boolean writeAudio = hasAudio && !hasVideo || hasAudio && mVideoEncodeStream.nextPts > mAudioEncodeStream.nextPts;
            if (writeAudio) {
                Log.d(TAG, "writeAudio#pts" + mAudioEncodeStream.nextPts);
                writeFrame(mAudioEncodeStream, readFrame(mAudioDecodeStream));
            }
        }

    }

    public int start() {
        try {
            mMediaExtractor = new MediaExtractor();
            mMediaExtractor.setDataSource(mSrcPath);
            mMediaMuxer = new MediaMuxer(mDstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            for (int i = 0; i < mMediaExtractor.getTrackCount(); i++) {
                openDecodeStream(i);
                openEncodeStream(i);
            }
            muxer();
            mMediaMuxer.stop();
            mMediaMuxer.release();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }


}
