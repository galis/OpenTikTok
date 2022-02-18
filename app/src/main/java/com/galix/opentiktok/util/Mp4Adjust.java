package com.galix.opentiktok.util;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodec.mapHardwareBuffer;

/**
 * Mp4调整工具类
 *
 * @Author:Galis
 * @Date:2022.02.09
 */
public class Mp4Adjust {

    private static final String TAG = Mp4Adjust.class.getSimpleName();
    private int mGop;//Gop
    private int mVb;//video 比特率
    private int mAb;//音频 比特率
    private String mSrcPath;
    private String mDstPath;
    private String mCacheDir;

    private MediaExtractor mMediaExtractor;
    private MediaMuxer mMediaMuxer;
    private Stream mAudioDecodeStream, mVideoDecodeStream;
    private Stream mAudioEncodeStream, mVideoEncodeStream;

    private BufferCallback mCallback;

    public interface BufferCallback {
        void handle(Stream stream, ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo);

    }

    public Mp4Adjust(int mGop, int mVb, int mAb, String mSrcPath, String mDstPath, String cacheDir) {
        this.mGop = mGop;
        this.mVb = mVb;
        this.mAb = mAb;
        this.mSrcPath = mSrcPath;
        this.mDstPath = mDstPath;
        this.mCacheDir = cacheDir;
        mAudioDecodeStream = mVideoDecodeStream = null;
        mAudioEncodeStream = mVideoEncodeStream = null;
    }

    private static class Frame {
        ByteBuffer byteBuffer;
        long pts = -1;
        boolean isEOF = false;
    }

    public static class Stream {
        public int trackIdx = -1;
        public long nextPts = -1;
        public long duration = -1;
        public long thumbPos = -1;
        public boolean isInputEOF = false;
        public boolean isOutputEOF = false;
        public ByteBuffer buffer;
        public Frame avFrame;
        public MediaCodec mediaCodec;
        public MediaFormat format;
        public MediaExtractor mediaExtractor;
    }

    private void openDecodeStream(int trackIdx) {
        MediaFormat mediaFormat = mMediaExtractor.getTrackFormat(trackIdx);
        if (mediaFormat.getLong(MediaFormat.KEY_DURATION, 0) == 0) return;
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar);
        Stream stream = new Stream();
        stream.trackIdx = trackIdx;
        stream.duration = mediaFormat.getLong(MediaFormat.KEY_DURATION, 0);
        stream.isInputEOF = stream.isOutputEOF = false;
        stream.buffer = ByteBuffer.allocateDirect(mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
        stream.format = mediaFormat;
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
        Log.d(TAG, "openDecodeStream" + stream.format.toString());
    }

    private void openEncodeStream(int trackIdx) {
        MediaFormat mediaFormat = mMediaExtractor.getTrackFormat(trackIdx);
        if (mediaFormat.getLong(MediaFormat.KEY_DURATION, 0) == 0) return;
        Stream stream = new Stream();
        stream.buffer = ByteBuffer.allocateDirect(mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
        stream.duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
        if (mediaFormat.getString(MediaFormat.KEY_MIME).contains("video")) {
            int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
            int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
            mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                    width, height);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mVb);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mGop);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        } else {
            mediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 2);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE,mAb);
        }
        stream.trackIdx = -1;
        stream.isInputEOF = stream.isOutputEOF = false;
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

    /**
     * 读取一帧，返回Frame.最后一帧标志isEOF为true
     *
     * @param stream 读取流，不为空
     * @return Frame
     */
    private Frame readFrame(Stream stream) {
        if (stream.avFrame == null) {
            stream.avFrame = new Frame();
        }
        Frame avFrame = stream.avFrame;
        MediaExtractor mediaExtractor = stream.mediaExtractor;
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        while (!stream.isInputEOF || !stream.isOutputEOF) {
            Log.d(TAG, "readFrame#");
            if (!stream.isOutputEOF) {
                int outputBufIdx = stream.mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                if (outputBufIdx >= 0) {
                    if (bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM) {
                        stream.isOutputEOF = true;
                        if (stream.trackIdx == 0) Log.d(TAG, "readFrame#isOutputEOF");
                    }
                    ByteBuffer byteBuffer = stream.mediaCodec.getOutputBuffer(outputBufIdx);
                    if (mCallback != null) {
                        mCallback.handle(stream, byteBuffer, bufferInfo);
                    }
                    if (avFrame.byteBuffer == null || avFrame.byteBuffer.limit() < bufferInfo.size) {
                        avFrame.byteBuffer = ByteBuffer.allocateDirect(bufferInfo.size);
                    }
                    if (stream.trackIdx == 0) Log.d(TAG, "readFrame#pts#" + bufferInfo.presentationTimeUs);
                    avFrame.isEOF = stream.isOutputEOF;
                    avFrame.pts = bufferInfo.presentationTimeUs;
                    avFrame.byteBuffer.position(0);
                    avFrame.byteBuffer.put(byteBuffer);
                    byteBuffer.position(0);
                    avFrame.byteBuffer.position(0);
                    stream.mediaCodec.releaseOutputBuffer(outputBufIdx, false);
                    return avFrame;
                } else if (outputBufIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.d(TAG, "INFO_TRY_AGAIN_LATER:" + bufferInfo.presentationTimeUs);
                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED:" + bufferInfo.presentationTimeUs);
                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED:" + bufferInfo.presentationTimeUs + "#" + stream.format.toString());
                }
            }
            if (!stream.isInputEOF) {
                int inputBufIdx = stream.mediaCodec.dequeueInputBuffer(0);
                if (inputBufIdx >= 0) {
                    int sampleSize = mediaExtractor.readSampleData(stream.buffer, 0);
                    if (sampleSize < 0) {
                        sampleSize = 0;
                        stream.isInputEOF = true;
                        if (stream.trackIdx == 0) Log.d(TAG, "readFrame#isInputEOF");
                    }
                    stream.mediaCodec.getInputBuffer(inputBufIdx).put(stream.buffer);
                    stream.mediaCodec.queueInputBuffer(inputBufIdx, 0,
                            sampleSize,
                            mediaExtractor.getSampleTime(),
                            stream.isInputEOF ? BUFFER_FLAG_END_OF_STREAM : 0);
                    if (stream.trackIdx == 0) Log.d(TAG, "readFrame#getSampleTime" + mediaExtractor.getSampleTime());
                    mediaExtractor.advance();
                }
            }
        }
        return stream.avFrame;
    }

    /**
     * 向MP4文件写入一帧音视频数据
     *
     * @param stream
     * @param frame
     */
    private void writeFrame(Stream stream, Frame frame) {
        MediaCodec mediaCodec = stream.mediaCodec;
        boolean ret = false;
        while (!ret) {
            if (!stream.isOutputEOF) {
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                int status = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                if (status >= 0) {
                    if (bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM) {
                        stream.isOutputEOF = true;
                        stream.nextPts = stream.duration;
                        Log.d(TAG, "writeFrame#isOutputEOF#track");
                    }
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        Log.d(TAG, "writeFrame#BUFFER_FLAG_CODEC_CONFIG#" + bufferInfo.size + "#trackId#" + stream.trackIdx);
                    }
                    if (stream.trackIdx == -1) {
                        Log.d(TAG, "BAD!!!!!");
                    }
                    if (!stream.isOutputEOF) {
                        ByteBuffer byteBuffer = mediaCodec.getOutputBuffer(status);
                        byteBuffer.position(bufferInfo.offset);
                        byteBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        Log.d(TAG, "writeSampleData#bufferSize#" + byteBuffer.limit() + "stream#index#" + stream.trackIdx +
                                "#presentationTimeUs#" + bufferInfo.presentationTimeUs);
                        mMediaMuxer.writeSampleData(stream.trackIdx, mediaCodec.getOutputBuffer(status), bufferInfo);
                        stream.nextPts = bufferInfo.presentationTimeUs;
                    }
                    mediaCodec.releaseOutputBuffer(status, false);
                } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    stream.trackIdx = mMediaMuxer.addTrack(mediaCodec.getOutputFormat());
                    Log.d(TAG, "WAT#INFO_OUTPUT_FORMAT_CHANGED#trackIdx#" + stream.trackIdx +
                            "#outputformat#" + mediaCodec.getOutputFormat().toString());
                }
            }
            if (!stream.isInputEOF) {
                int status = mediaCodec.dequeueInputBuffer(0);
                if (status >= 0) {
                    mediaCodec.getInputBuffer(status).put(frame.byteBuffer);
                    stream.isInputEOF = frame.isEOF;
                    if (stream.isInputEOF) Log.d(TAG, "writeFrame#isInputEOF");
                    mediaCodec.queueInputBuffer(status, 0, stream.isInputEOF ? 0 : frame.byteBuffer.limit(), stream.isInputEOF ? -1 : frame.pts, stream.isInputEOF ?
                            BUFFER_FLAG_END_OF_STREAM : 0);
                    ret = true;
                }
                Log.d(TAG, "dequeueInputBuffer#status#" + status);
            } else {
                ret = true;
            }
        }
    }

    /**
     * 合成MP4.
     */
    private void muxer() {
        while (mVideoDecodeStream != null && mVideoEncodeStream.trackIdx == -1) {
            writeFrame(mVideoEncodeStream, readFrame(mVideoDecodeStream));
        }
        while (mAudioDecodeStream != null && mAudioEncodeStream.trackIdx == -1) {
            writeFrame(mAudioEncodeStream, readFrame(mAudioDecodeStream));
        }
        mMediaMuxer.start();
        boolean hasVideo = mVideoEncodeStream != null && !mVideoEncodeStream.isOutputEOF;
        boolean hasAudio = mAudioEncodeStream != null && !mAudioEncodeStream.isOutputEOF;
        while (hasVideo || hasAudio) {
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
            hasAudio = mAudioEncodeStream != null && !mAudioEncodeStream.isOutputEOF;
            hasVideo = mVideoEncodeStream != null && !mVideoEncodeStream.isOutputEOF;
        }

    }

    public int process(BufferCallback bufferCallback) {
        mCallback = bufferCallback;
        try {
            mMediaExtractor = new MediaExtractor();
            mMediaExtractor.setDataSource(mSrcPath);
            mMediaMuxer = new MediaMuxer(mDstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            for (int i = 0; i < mMediaExtractor.getTrackCount(); i++) {
                openDecodeStream(i);
                openEncodeStream(i);
            }
            muxer();
            mMediaMuxer.stop();//flush文件
            mMediaMuxer.release();//释放资源
            mMediaExtractor.release();
            Log.d(TAG, "muxer#finish");
        } catch (IOException e) {
            e.printStackTrace();
            File file = new File(mDstPath);
            if (file.exists()) file.delete();
        }
        return -1;
    }


}
