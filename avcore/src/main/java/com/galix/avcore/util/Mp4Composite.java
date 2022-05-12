package com.galix.avcore.util;

import android.graphics.Color;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.galix.avcore.avcore.AVAudio;
import com.galix.avcore.avcore.AVComponent;
import com.galix.avcore.avcore.AVEngine;
import com.galix.avcore.avcore.AVFrame;
import com.galix.avcore.avcore.AVTransaction;
import com.galix.avcore.avcore.AVWord;
import com.galix.avcore.avcore.ThreadManager;
import com.galix.avcore.render.IVideoRender;
import com.galix.avcore.render.OESRender;
import com.galix.avcore.render.ScreenRender;
import com.galix.avcore.render.filters.GLTexture;
import com.galix.avcore.render.filters.TextureFilter;
import com.galix.avcore.render.filters.TranAlphaFilter;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glViewport;

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
    private AVComponent mLastVideo;
    private AVAudio mLastAudio;
    private CompositeCallback mCallback;

    private final Object mMediaMuxerLock = new Object();


    public interface CompositeCallback {
        void handle(int progress);

    }

    public Mp4Composite(AVEngine avEngine) {
        mVideoState = avEngine.getVideoState();
        mEngine = avEngine;
        mGop = mVideoState.compositeGop;
        mVb = mVideoState.compositeVb;
        mAb = mVideoState.compositeAb;
        mDstPath = mVideoState.compositePath;
        mAudioEncodeStream = mVideoEncodeStream = null;
    }

    public static class Stream {
        public int trackIdx = -1;
        public long nextPts = 0;
        public long currentPts = 0;
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
                    mVideoState.canvasSize.getWidth(), mVideoState.canvasSize.getHeight());
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
        if (mVideoEncodeStream.nextPts >= mVideoState.durationUS) {
            mVideoEncodeStream.isInputEOF = true;
            return null;
        }
        mVideoEncodeStream.isInputEOF = false;
        List<AVComponent> components;
        components = mEngine.findComponents(AVComponent.AVComponentType.TRANSACTION, mVideoEncodeStream.nextPts);
        if (components.isEmpty()) {
            components = mEngine.findComponents(AVComponent.AVComponentType.VIDEO, mVideoEncodeStream.nextPts);
        }
        if (components.isEmpty()) {
            LogUtil.logEngine("readVideoFrame#" + mVideoEncodeStream.nextPts + "#" + mVideoState.durationUS);
            return null;
        }
        AVComponent video = components.get(0);
        video.seekFrame(mVideoEncodeStream.nextPts);
        mLastVideo = video;
        mVideoEncodeStream.currentPts = mVideoEncodeStream.nextPts;
        mVideoEncodeStream.nextPts += 1000000.f / mVideoState.compositeFrameRate;
        return mLastVideo.peekFrame();
    }

    private void renderVideo(TextureFilter textureFilter) {
        AVFrame videoFrame = readVideoFrame();
        if (videoFrame == null) {
            mVideoEncodeStream.mediaCodec.signalEndOfInputStream();//采用surface输入的时候要注意这个了
            LogUtil.logEngine("check#signalEndOfInputStream");
            return;
        }
        GLTexture lastTexture;
        boolean isFlipVertical = true;
        if (mLastVideo instanceof AVTransaction) {
            if (mLastVideo.getRender() != null) {
                mLastVideo.getRender().render(videoFrame);
            }
            lastTexture = ((IVideoRender) mLastVideo.getRender()).getOutTexture();
            isFlipVertical = false;
        } else {
            lastTexture = mLastVideo.peekFrame().getTexture();
        }
        LogUtil.logEngine("readVideoFrame#" + videoFrame.getPts());
        textureFilter.write(
                "use_fbo", false,
                "textureMat", lastTexture.getMatrix(),
                "inputImageTexture", lastTexture,
                "isOes", lastTexture.isOes(),
                "bgColor", mVideoState.bgColor,
                "isFlipVertical", isFlipVertical
        );
        textureFilter.setPreTask(new Runnable() {
            @Override
            public void run() {
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glViewport(0, 0, mVideoState.canvasSize.getWidth(), mVideoState.canvasSize.getHeight());
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            }
        });
        textureFilter.render();
    }

    private void renderSticker(TextureFilter textureFilter) {
        List<AVComponent> components = mEngine.findComponents(AVComponent.AVComponentType.STICKER, mVideoEncodeStream.currentPts);
        if (components.isEmpty()) {
            return;
        }
        for (AVComponent component : components) {
            component.seekFrame(mVideoEncodeStream.currentPts);
            textureFilter.write(
                    "use_fbo", false,
                    "textureMat", component.getMatrix(),
                    "inputImageTexture", component.peekFrame().getTexture(),
                    "isOes", component.peekFrame().getTexture().isOes(),
                    "bgColor", 0,
                    "isFlipVertical", true
            );
            textureFilter.setPreTask(new Runnable() {
                @Override
                public void run() {
                    GLES30.glEnable(GLES30.GL_BLEND);
                    GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
                }
            });
            textureFilter.setPostTask(new Runnable() {
                @Override
                public void run() {
                    GLES30.glDisable(GLES30.GL_BLEND);
                }
            });
            textureFilter.render();
        }
    }

    private void renderWord(TextureFilter textureFilter) {
        List<AVComponent> components = mEngine.findComponents(AVComponent.AVComponentType.WORD, mVideoEncodeStream.currentPts);
        if (components.isEmpty()) {
            return;
        }
        for (AVComponent component : components) {
            component.write(AVWord.CONFIG_USE_BITMAP, true);
            component.seekFrame(mVideoEncodeStream.currentPts);
            component.write(AVWord.CONFIG_USE_BITMAP, false);
            textureFilter.write(
                    "use_fbo", false,
                    "textureMat", component.getMatrix(),
                    "inputImageTexture", component.peekFrame().getTexture(),
                    "isOes", component.peekFrame().getTexture().isOes(),
                    "bgColor", 0,
                    "isFlipVertical", true
            );
            textureFilter.setPreTask(new Runnable() {
                @Override
                public void run() {
                    GLES30.glEnable(GLES30.GL_BLEND);
                    GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
                }
            });
            textureFilter.setPostTask(new Runnable() {
                @Override
                public void run() {
                    GLES30.glDisable(GLES30.GL_BLEND);
                }
            });
            textureFilter.render();
        }
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
        LogUtil.logEngine("readAudioFrame111#");
        if (mLastAudio != audio) {
            audio.seekFrame(mAudioEncodeStream.nextPts);
        } else {
            audio.readFrame();
        }
        mLastAudio = audio;
        mAudioEncodeStream.nextPts += mLastAudio.peekFrame().getDuration();
        LogUtil.logEngine("readAudioFrame#" + mLastAudio.peekFrame().getPts());
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
                    LogUtil.logEngine("writeFrame#isOutputEOF#track");
                }
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    LogUtil.logEngine("writeFrame#BUFFER_FLAG_CODEC_CONFIG#" + bufferInfo.size + "#trackId#" + stream.trackIdx);
                }
                if (stream.trackIdx == -1) {
                    LogUtil.logEngine("BAD!!!!!");
                }
                if (!stream.isOutputEOF) {
                    ByteBuffer byteBuffer = mediaCodec.getOutputBuffer(status);
                    byteBuffer.position(bufferInfo.offset);
                    byteBuffer.limit(bufferInfo.offset + bufferInfo.size);
                    LogUtil.logEngine("writeSampleData#bufferSize#" + byteBuffer.limit() + "stream#index#" + stream.trackIdx +
                            "#presentationTimeUs#" + bufferInfo.presentationTimeUs + "#nextpts#" + stream.nextPts);
                    mMediaMuxer.writeSampleData(stream.trackIdx, mediaCodec.getOutputBuffer(status), bufferInfo);
                }
                mediaCodec.releaseOutputBuffer(status, false);
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                synchronized (mMediaMuxerLock) {
                    stream.trackIdx = mMediaMuxer.addTrack(mediaCodec.getOutputFormat());
                    LogUtil.logEngine("WAT#INFO_OUTPUT_FORMAT_CHANGED#trackIdx#" + stream.trackIdx +
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
        if (!mVideoState.hasVideo) {
            return -1;
        }
        mCallback = bufferCallback;
        ThreadManager.getInstance().createThread("Composite_Muxer", new Handler.Callback() {
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
        ThreadManager.getInstance().getHandler("Composite_Muxer").sendEmptyMessage(COMPOSITE_INIT);
        ThreadManager.getInstance().createThread("Composite_Audio", new Runnable() {
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
                            mAudioEncodeStream.mediaCodec.queueInputBuffer(status, 0, 0,
                                    mVideoState.durationUS, BUFFER_FLAG_END_OF_STREAM);
                            break;
                        }
                        ByteBuffer byteBuffer = mAudioEncodeStream.mediaCodec.getInputBuffer(status);
                        byteBuffer.put(audioFrame.getByteBuffer());
                        LogUtil.logEngine("audioFrame.isEof()" + audioFrame.isEof());
                        mAudioEncodeStream.mediaCodec.queueInputBuffer(status, 0, byteBuffer.limit(), audioFrame.getPts(),
                                audioFrame.isEof() ? BUFFER_FLAG_END_OF_STREAM : 0);
                        if (audioFrame.isEof()) {
                            mAudioEncodeStream.isInputEOF = true;
                        }
                    }
                    ThreadManager.getInstance().getHandler("Composite_Muxer").sendEmptyMessage(COMPOSITE_AUDIO_VALID);
                }
                LogUtil.logEngine("mAudioThread finish");
            }
        });
        mVideoEncodeStream = openEncodeStream(true);
        //创建Render
        EglHelper eglHelper = mEngine.getEglHelper();
        eglHelper.createSurface(mVideoEncodeStream.inputSurface);
        eglHelper.makeCurrent();
        OESRender oesRender = new OESRender();
        oesRender.open();
        oesRender.write(TimeUtils.BuildMap("surface_size", mVideoState.canvasSize));
        TextureFilter textureFilter = new TextureFilter();
        textureFilter.open();
        while (!mVideoEncodeStream.isInputEOF) {
            //渲染视频
            renderVideo(textureFilter);
            //渲染贴纸
            renderSticker(textureFilter);
            //渲染文字
            renderWord(textureFilter);
            mEngine.getEglHelper().setPresentationTime(mVideoEncodeStream.currentPts * 1000);
            mEngine.getEglHelper().swap();
            int progress = (int) (mVideoEncodeStream.currentPts * 1.0f / mVideoState.durationUS * 100);
            mCallback.handle(progress);
            LogUtil.logEngine("mCallback.handle#" + progress
                    + "#" + mVideoEncodeStream.currentPts
                    + "#" + mVideoEncodeStream.nextPts
                    + "#" + mLastVideo.peekFrame().getPts());
            ThreadManager.getInstance().getHandler("Composite_Muxer").sendEmptyMessage(COMPOSITE_FRAME_VALID);
        }

        ThreadManager.getInstance().destroyThread("Composite_Audio");
        ThreadManager.getInstance().getHandler("Composite_Muxer").sendEmptyMessage(COMPOSITE_DESTROY);
        ThreadManager.getInstance().destroyThread("Composite_Muxer");
        //销毁OpenGL资源
        textureFilter.close();
        eglHelper.destroySurface();
        eglHelper.makeCurrent();
        LogUtil.logEngine("Composite finish");
        return 0;
    }

}
