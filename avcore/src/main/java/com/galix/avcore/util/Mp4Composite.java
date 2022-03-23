package com.galix.avcore.util;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.galix.avcore.avcore.AVAudio;
import com.galix.avcore.avcore.AVComponent;
import com.galix.avcore.avcore.AVEngine;
import com.galix.avcore.avcore.AVFrame;
import com.galix.avcore.avcore.AVVideo;
import com.galix.avcore.render.OESRender;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;

/**
 * Mp4合成类
 *
 * @Author:Galis
 * @Date:2022.03.21
 */
public class Mp4Composite {

    private static final String TAG = Mp4Composite.class.getSimpleName();
    private static final int COMPOSITE_INIT = 1;
    private static final int COMPOSITE_FRAME_VALID = 2;
    private static final int COMPOSITE_AUDIO_VALID = 3;
    private static final int COMPOSITE_DESTROY = 4;
    private int mGop;//Gop
    private int mVb;//video 比特率
    private int mAb;//音频 比特率
    private boolean mMuxerStart = false;
    private String mDstPath;
    private AVEngine.VideoState mVideoState;
    private MediaMuxer mMediaMuxer;
    private AVEngine mEngine;
    private Stream mAudioEncodeStream, mVideoEncodeStream;
    private AVVideo mLastVideo;
    private AVAudio mLastAudio;
    private CompositeCallback mCallback;
    private HandlerThread mCompositeThread;
    private Handler mCompositeHandler;
    private HandlerThread mGLThread;
    private Handler mGLHandler;
    private final Object mMediaMuxerLock = new Object();
    private HandlerThread mAudioThread;
    private Handler mAudioHandler;


    public interface CompositeCallback {
        void handle(int progress);

    }

    public Mp4Composite(AVEngine avEngine) {
        mVideoState = avEngine.getVideoState();
        mEngine = avEngine;
        mGop = mVideoState.mTargetGop;
        mVb = mVideoState.mTargetVb;
        mAb = mVideoState.mTargetAb;
        mDstPath = mVideoState.mTargetPath;
        mAudioEncodeStream = mVideoEncodeStream = null;
    }

    public static class Stream {
        public int trackIdx = -1;
        public long nextPts = 0;
        public long duration = 0;
        public long thumbPos = -1;
        public boolean isInputEOF = false;
        public boolean isOutputEOF = false;
        public ByteBuffer buffer;
        public AVFrame avFrame;
        public MediaCodec mediaCodec;
        public MediaFormat format;
        public MediaExtractor mediaExtractor;
        public Surface inputSurface;
    }

    private Stream openEncodeStream(boolean isVideo) {
        Stream stream = new Stream();
        MediaFormat mediaFormat = null;
        if (isVideo) {
            mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                    mVideoState.mTargetSize.getWidth(), mVideoState.mTargetSize.getHeight());
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
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mAb);
        }
        try {
            stream.trackIdx = -1;
            stream.isInputEOF = stream.isOutputEOF = false;
            stream.mediaCodec = MediaCodec.createEncoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));
            stream.mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            if (isVideo) {
                stream.inputSurface = stream.mediaCodec.createInputSurface();
            }
            stream.mediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return stream;
    }

    /**
     * 读取一帧视频，返回Frame.最后一帧标志isEOF为true
     *
     * @return Frame
     */
    private AVFrame readVideoFrame() {
        if (mVideoEncodeStream.nextPts > mVideoState.durationUS) {
            mVideoEncodeStream.isInputEOF = true;
            return null;
        }
        mVideoEncodeStream.isInputEOF = false;
        List<AVComponent> components = mEngine.findComponents(AVComponent.AVComponentType.VIDEO, mVideoEncodeStream.nextPts);
        AVVideo video = (AVVideo) components.get(0);
        if (mLastVideo != video) {
            video.seekFrame(mVideoEncodeStream.nextPts);
        } else {
            video.readFrame();
        }
        mLastVideo = video;
        mVideoEncodeStream.nextPts += mLastVideo.peekFrame().getDuration();
        return mLastVideo.peekFrame();
    }

    /**
     * 读取一帧音频，返回Frame.最后一帧标志isEOF为true
     *
     * @return Frame
     */
    private AVFrame readAudioFrame() {
        if (mAudioEncodeStream.nextPts > mVideoState.durationUS) {
            mAudioEncodeStream.isInputEOF = true;
            return null;
        }
        mAudioEncodeStream.isInputEOF = false;
        List<AVComponent> components = mEngine.findComponents(AVComponent.AVComponentType.AUDIO, mAudioEncodeStream.nextPts);
        AVAudio audio = (AVAudio) components.get(0);
        if (mLastAudio != audio) {
            audio.seekFrame(mAudioEncodeStream.nextPts);
        } else {
            audio.readFrame();
        }
        mLastAudio = audio;
        mAudioEncodeStream.nextPts += mLastAudio.peekFrame().getDuration();
        Log.d(TAG, "readAudioFrame#" + mLastAudio.peekFrame().getPts());
        return mLastAudio.peekFrame();
    }

    /**
     * 向MP4文件写入一帧音视频数据
     *
     * @param stream
     */
    private void drainEncoder(Stream stream) {
        MediaCodec mediaCodec = stream.mediaCodec;
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        if (stream.trackIdx != -1 && !mMuxerStart) {
            return;
        }
        while (!stream.isOutputEOF) {
            int status = mediaCodec.dequeueOutputBuffer(bufferInfo, 10);
            if (status >= 0) {
                if (bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM) {
                    stream.isOutputEOF = true;
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
                            "#presentationTimeUs#" + bufferInfo.presentationTimeUs + "#nextpts#" + stream.nextPts);
                    mMediaMuxer.writeSampleData(stream.trackIdx, mediaCodec.getOutputBuffer(status), bufferInfo);
                }
                mediaCodec.releaseOutputBuffer(status, false);
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                synchronized (mMediaMuxerLock) {
                    stream.trackIdx = mMediaMuxer.addTrack(mediaCodec.getOutputFormat());
                    Log.d(TAG, "WAT#INFO_OUTPUT_FORMAT_CHANGED#trackIdx#" + stream.trackIdx +
                            "#outputformat#" + mediaCodec.getOutputFormat().toString());
                    if (stream == mVideoEncodeStream) {
                        mVideoState.readyVideo = true;
                    } else {
                        mVideoState.readyAudio = true;
                    }
                    if (mVideoState.readyAudio && mVideoState.readyVideo) {
                        mMediaMuxer.start();
                        mMuxerStart = true;
                    }
                }
                break;
            } else if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!stream.isInputEOF) {
                    break;
                }
            }
        }

    }

    public int process(CompositeCallback bufferCallback) {
        mCallback = bufferCallback;
        mCompositeThread = new HandlerThread("CompositeThread");
        mCompositeThread.start();
        mCompositeHandler = new Handler(mCompositeThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case COMPOSITE_INIT:
                        //创建Opengl Context
                        try {
                            File targetFile = new File(mDstPath);
                            if (!targetFile.exists()) {
                                File parent = new File(targetFile.getParent());
                                if (!parent.exists()) {
                                    parent.mkdirs();
                                }
                            }
                            mMediaMuxer = new MediaMuxer(mDstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    case COMPOSITE_FRAME_VALID:
                        drainEncoder(mVideoEncodeStream);
                        break;
                    case COMPOSITE_AUDIO_VALID:
                        drainEncoder(mAudioEncodeStream);
                        break;
                    case COMPOSITE_DESTROY:
                        mCallback.handle(100);
                        mMediaMuxer.stop();//flush文件
                        mMediaMuxer.release();//释放资源
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
        mCompositeHandler.sendEmptyMessage(COMPOSITE_INIT);
        mAudioThread = new HandlerThread("AudioThread");
        mAudioThread.start();
        mAudioHandler = new Handler(mAudioThread.getLooper());
        mAudioHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mVideoState.hasAudio) {
                    mAudioEncodeStream = openEncodeStream(false);
                } else {
                    return;
                }
                int status;
                while (!mAudioEncodeStream.isInputEOF) {
                    status = mAudioEncodeStream.mediaCodec.dequeueInputBuffer(0);
                    if (status >= 0) {
                        AVFrame audioFrame = readAudioFrame();
                        if (audioFrame == null) {
                            Log.d(TAG, "check#audioFrame null");
                            break;
                        }
                        ByteBuffer byteBuffer = mAudioEncodeStream.mediaCodec.getInputBuffer(status);
                        byteBuffer.put(audioFrame.getByteBuffer());
                        Log.d(TAG, "check#pts" + audioFrame.getPts());
                        mAudioEncodeStream.mediaCodec.queueInputBuffer(status, 0, byteBuffer.limit(), audioFrame.getPts(),
                                audioFrame.isEof() ? BUFFER_FLAG_END_OF_STREAM : 0);
                        if (audioFrame.isEof()) {
                            mAudioEncodeStream.isInputEOF = true;
                        }
                    }
                    mCompositeHandler.sendEmptyMessage(COMPOSITE_AUDIO_VALID);
                }
                Log.d(TAG, "mAudioThread finish");
            }
        });
        if (mVideoState.hasVideo) {
            mVideoEncodeStream = openEncodeStream(true);
        } else {
            return -1;
        }

        //创建Render
        mEngine.getEglHelper().createSurface(mVideoEncodeStream.inputSurface);
        mEngine.getEglHelper().makeCurrent();
        OESRender oesRender = new OESRender();
        oesRender.open();
        oesRender.write(new OESRender.OesRenderConfig(mVideoState.mTargetSize.getWidth(), mVideoState.mTargetSize.getHeight()));

        while (!mVideoEncodeStream.isInputEOF) {
            AVFrame videoFrame = readVideoFrame();
            if (videoFrame == null) {
                mVideoEncodeStream.mediaCodec.signalEndOfInputStream();//采用surface输入的时候要注意这个了
                Log.d(TAG, "check#signalEndOfInputStream");
                break;
            }
            oesRender.render(videoFrame);
            mEngine.getEglHelper().setPresentationTime(videoFrame.getPts() * 1000);
            mEngine.getEglHelper().swap();
            mCallback.handle((int) (videoFrame.getPts() * 1.0f / mVideoState.durationUS * 100));
            mCompositeHandler.sendEmptyMessage(COMPOSITE_FRAME_VALID);
        }

        oesRender.close();
        try {
            mAudioHandler.getLooper().quitSafely();
            mAudioThread.join();
            mCompositeHandler.sendEmptyMessage(COMPOSITE_DESTROY);
            mCompositeHandler.getLooper().quitSafely();
            mCompositeThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mEngine.getEglHelper().destroySurface();
        mEngine.getEglHelper().makeCurrent();
        Log.d(TAG, "Composite finish");
        return 0;
    }

}
