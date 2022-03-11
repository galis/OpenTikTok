package com.galix.avcore.render;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import com.galix.avcore.avcore.AVFrame;

import static android.media.AudioTrack.WRITE_BLOCKING;

public class AudioRender implements IRender {
    private AudioTrack mAudioTrack;
    private int mMinBufferSize;

    @Override
    public boolean isOpen() {
        return mAudioTrack != null;
    }

    @Override
    public void open() {
        if (isOpen()) return;
        mMinBufferSize = 4096;
        mAudioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                44100,
                AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                mMinBufferSize,
                AudioTrack.MODE_STREAM
        );
        mAudioTrack.play();
    }

    @Override
    public void close() {
        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack.release();
            mAudioTrack = null;
        }
    }

    @Override
    public void write(Object config) {

    }

    @Override
    public void render(AVFrame avFrame) {
        mAudioTrack.write(avFrame.getByteBuffer(), mMinBufferSize, WRITE_BLOCKING);
    }
}
