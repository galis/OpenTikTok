package com.galix.opentiktok;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;

/**
 * 视频编辑界面
 *
 * @Author Galis
 * @Date 2022.01.15
 */
public class VideoEditActivity extends AppCompatActivity {

    private static final String TAG = VideoEditActivity.class.getSimpleName();
    private static final String PATH = "/sdcard/demo2.mp4";
    private static final int REQUEST_CODE = 1;

    private GLSurfaceView mSurfaceView;
    private SeekBar mSeekBar;
    private VideoState mVideoState;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private ARContext mARContext;

    class VideoState {
        public boolean isSeek = false;
        public boolean isSeekDone = true;
        public boolean isInputEOF = false;
        public boolean isOutputEOF = false;
        public boolean isExit = false;
        public long position = -1;
        public long duration = -1;

        //纹理
        public int surfaceTextureId;
        public Surface surface;
        public SurfaceTexture surfaceTexture;
    }

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("arcore");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_edit);
        mSurfaceView = findViewById(R.id.glsurface_preview);
        mSurfaceView.setEGLContextClientVersion(3);
        mSurfaceView.setRenderer(new GLSurfaceView.Renderer() {
            @Override
            public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                mARContext = new ARContext();
                mARContext.create();
                dumpMp4Thumb();
            }

            @Override
            public void onSurfaceChanged(GL10 gl, int width, int height) {
                mARContext.onSurfaceChanged(width, height);
            }

            @Override
            public void onDrawFrame(GL10 gl) {
                mVideoState.surfaceTexture.updateTexImage();
                mARContext.draw(mVideoState.surfaceTextureId);
            }
        });
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        checkPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

            }
        });

        mSeekBar = findViewById(R.id.seekbar_drag);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mVideoState != null) {
                    mVideoState.position = (int) (progress / 100.f * mVideoState.duration);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (mVideoState != null) {
                    mVideoState.isSeek = true;
                    mVideoState.isInputEOF = false;
                    mVideoState.isOutputEOF = false;
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mVideoState != null) {
                    mVideoState.isSeek = false;
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            mVideoState.isExit = true;
            mHandler.getLooper().quit();
            mHandlerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(VideoEditActivity.this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(VideoEditActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(VideoEditActivity.this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(VideoEditActivity.this,
                    new String[]{Manifest.permission.CAMERA,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.INTERNET,
                            Manifest.permission.ACCESS_NETWORK_STATE
                    }, REQUEST_CODE);
        }
    }

    private void dumpMp4Thumb() {
        mHandlerThread = new HandlerThread("dumpMp4");
        mHandlerThread.start();
        int[] surface = new int[1];
        GLES30.glGenTextures(1, surface, 0);
        if (surface[0] == -1) return;//申请不到texture,出错
        mVideoState = new VideoState();
        mVideoState.surfaceTexture = new SurfaceTexture(surface[0]);
        mVideoState.surfaceTextureId = surface[0];
        mVideoState.surface = new Surface(mVideoState.surfaceTexture);
        mHandler = new Handler(mHandlerThread.getLooper());
        mHandler.post(() -> {
            MediaExtractor mediaExtractor = new MediaExtractor();
            int videoTrackIndex = -1;
            try {
                mediaExtractor.setDataSource(PATH);
                for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                    if (mediaExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).contains("video")) {
                        videoTrackIndex = i;
                        break;
                    }
                    Log.d("TAG", mediaExtractor.getTrackFormat(i).toString());
                }
                if (videoTrackIndex != -1) {
                    mediaExtractor.selectTrack(videoTrackIndex);
                    MediaFormat videoFormat = mediaExtractor.getTrackFormat(videoTrackIndex);
                    MediaCodec mediaCodec = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
                    int width = videoFormat.getInteger(MediaFormat.KEY_WIDTH) / 2;
                    int height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT) / 2;
                    videoFormat.setInteger(MediaFormat.KEY_WIDTH, width);
                    videoFormat.setInteger(MediaFormat.KEY_HEIGHT, height);
                    if (mediaCodec == null) return;
                    mediaCodec.configure(videoFormat, mVideoState.surface, null, 0);
                    mediaCodec.start();
                    ByteBuffer sampleBuffer = ByteBuffer.allocate(videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
                    int frameCount = 0;
                    mVideoState.duration = mediaExtractor.getTrackFormat(videoTrackIndex).getLong(MediaFormat.KEY_DURATION);
                    while (!mVideoState.isExit) {
                        if (!(mVideoState.isSeek || !mVideoState.isOutputEOF || !mVideoState.isInputEOF)) {
                            Thread.sleep(20);
                            continue;
                        }
                        if (mVideoState.isSeek) {
                            mediaExtractor.seekTo(mVideoState.position, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                            mediaCodec.flush();
                            if (!mVideoState.isInputEOF) {
                                int inputBufIdx = mediaCodec.dequeueInputBuffer(0);
                                if (inputBufIdx >= 0) {
                                    mediaExtractor.readSampleData(sampleBuffer, 0);
                                    mVideoState.isInputEOF = mediaExtractor.readSampleData(sampleBuffer, 0) < 0;
                                    if (mVideoState.isInputEOF) {
                                        Log.d(TAG, "mVideoState.isInputEOF");
                                    }
                                    mediaCodec.getInputBuffer(inputBufIdx).put(sampleBuffer);
                                    mediaCodec.queueInputBuffer(inputBufIdx, 0,
                                            sampleBuffer.position(),
                                            mediaExtractor.getSampleTime(),
                                            mVideoState.isInputEOF ? BUFFER_FLAG_END_OF_STREAM : 0);
                                }
                            }
                            mVideoState.isSeekDone = false;
                            while (!mVideoState.isSeekDone && !mVideoState.isOutputEOF) {
                                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                                int outputBufIdx = mediaCodec.dequeueOutputBuffer(bufferInfo, -1);
                                if (outputBufIdx >= 0) {
                                    if (bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM) {
                                        mVideoState.isOutputEOF = true;
                                        Log.d(TAG, "BUFFER_FLAG_END_OF_STREAM:" + bufferInfo.presentationTimeUs);
                                        continue;
                                    }
                                    mediaCodec.releaseOutputBuffer(outputBufIdx, true);
                                    mSurfaceView.requestRender();
                                    mVideoState.isSeekDone = true;
                                } else if (outputBufIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                    Log.d(TAG, "INFO_TRY_AGAIN_LATER:" + bufferInfo.presentationTimeUs);
                                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED:" + bufferInfo.presentationTimeUs);
                                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                    Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED:" + bufferInfo.presentationTimeUs);
                                }
                            }
                        } else {
                            if (!mVideoState.isInputEOF) {
                                int inputBufIdx = mediaCodec.dequeueInputBuffer(0);
                                if (inputBufIdx >= 0) {
                                    mediaExtractor.readSampleData(sampleBuffer, 0);
                                    mVideoState.isInputEOF = mediaExtractor.readSampleData(sampleBuffer, 0) < 0;
                                    if (mVideoState.isInputEOF) {
                                        Log.d(TAG, "mVideoState.isInputEOF");
                                    }
                                    mediaCodec.getInputBuffer(inputBufIdx).put(sampleBuffer);
                                    mediaCodec.queueInputBuffer(inputBufIdx, 0,
                                            sampleBuffer.position(),
                                            mediaExtractor.getSampleTime(),
                                            mVideoState.isInputEOF ? BUFFER_FLAG_END_OF_STREAM : 0);
                                    mediaExtractor.advance();
                                }
                            }
                            if (!mVideoState.isOutputEOF) {
                                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                                int outputBufIdx = mediaCodec.dequeueOutputBuffer(bufferInfo, -1);
                                if (outputBufIdx >= 0) {
                                    if (bufferInfo.flags == BUFFER_FLAG_END_OF_STREAM) {
                                        mVideoState.isOutputEOF = true;
                                        continue;
                                    }
                                    mediaCodec.releaseOutputBuffer(outputBufIdx, true);
                                    mSurfaceView.requestRender();
                                    Log.d(TAG, "frameIndex:" + frameCount);
                                    frameCount++;
                                } else if (outputBufIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                    Log.d(TAG, "INFO_TRY_AGAIN_LATER:" + bufferInfo.presentationTimeUs);
                                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED:" + bufferInfo.presentationTimeUs);
                                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                    Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED:" + bufferInfo.presentationTimeUs);
                                }
                            }
                        }

                    }
                    mediaExtractor.release();
                    mediaCodec.release();
                    Log.d(TAG, "DONE!");
                }
            } catch (IOException e) {
                e.printStackTrace();
                mediaExtractor = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, permissions[i] + "IS NOT ALLOW!!", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
        }
    }

}