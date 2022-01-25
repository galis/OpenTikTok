package com.galix.opentiktok.render;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import com.galix.opentiktok.avcore.AVFrame;

import static android.media.AudioTrack.WRITE_BLOCKING;

public class AudioRender implements IRender {
    private AudioTrack mAudioTrack;
    private int mMinBufferSize;

    @Override
    public void open() {
        if (mAudioTrack != null) return;
        mMinBufferSize = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mAudioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                44100,
                AudioFormat.CHANNEL_CONFIGURATION_MONO,
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
        if(avFrame==null) return;
        mAudioTrack.write(avFrame.getByteBuffer(), mMinBufferSize, WRITE_BLOCKING);
    }
}
