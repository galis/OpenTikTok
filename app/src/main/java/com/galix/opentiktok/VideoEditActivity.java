package com.galix.opentiktok;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaSync;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.galix.opentiktok.util.VideoUtil;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.ThreadPoolExecutor;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static androidx.recyclerview.widget.RecyclerView.HORIZONTAL;

/**
 * 视频编辑界面
 *
 * @Author Galis
 * @Date 2022.01.15
 */
public class VideoEditActivity extends Activity {

    private static final String TAG = VideoEditActivity.class.getSimpleName();
    private static final int REQUEST_CODE = 1;
    private static final int DRAG_HEAD = 0;
    private static final int DRAG_FOOT = 1;
    private static final int DRAG_IMG = 2;
    private static final int DRAG_SPLIT = 3;

    private LinkedList<ThumbInfo> mThumbsList;
    private GLSurfaceView mSurfaceView;
    private RecyclerView mTabRecyclerView;
    private RecyclerView mThumbDragRecyclerView;
    private VideoState mVideoState;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private ARContext mARContext;
    private TextView mTimeInfo;
    private ImageView mPlayBtn;
    private ImageView mFullScreenBtn;
    private int mScrollX = 0;

    //底部ICON info
    private static final int[] TAB_INFO_LIST = {
            R.drawable.icon_video_cut, R.string.tab_cut,
            R.drawable.icon_adjust, R.string.tab_audio,
            R.drawable.icon_adjust, R.string.tab_text,
            R.drawable.icon_adjust, R.string.tab_sticker,
            R.drawable.icon_adjust, R.string.tab_inner_picture,
            R.drawable.icon_adjust, R.string.tab_magic,
            R.drawable.icon_filter, R.string.tab_filter,
            R.drawable.icon_adjust, R.string.tab_ratio,
            R.drawable.icon_background, R.string.tab_background,
            R.drawable.icon_adjust, R.string.tab_adjust
    };

    public static void start(Context ctx) {
        Intent intent = new Intent(ctx, VideoEditActivity.class);
        ctx.startActivity(intent);
    }

    //视频核心信息类
    private static class VideoState {
        public static final int INIT = -1;
        public static final int PLAY = 0;
        public static final int PAUSE = 1;
        public static final int DESTROY = 2;

        public boolean isFirstOpen = true;
        public boolean isSeek = false;
        public boolean isSeekDone = true;
        public boolean isInputEOF = false;
        public boolean isOutputEOF = false;
        public boolean isExit = false;
        public long position = -1;//当前视频位置 ms
        public long duration = -1;//视频总时长 ms
        public long videoTime = Long.MIN_VALUE;//视频播放时间戳
        public long audioTime = -1;//音频播放时间戳
        public int status = INIT;//播放状态

        //纹理
        public int surfaceTextureId;
        public Surface surface;
        public SurfaceTexture surfaceTexture;
    }

    private class ThumbInfo {
        public int type;
        public String imgPath;
    }

    private class ImageViewHolder extends RecyclerView.ViewHolder {
        public ImageView imageView;
        public TextView textView;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
        }

    }

    private class ThumbViewHolder extends RecyclerView.ViewHolder {
        public View view1;
        public View view2;

        public ThumbViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("arcore");
    }

    //权限部分
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

    //UI回调
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_edit);
        //初始化VideoState
        mVideoState = new VideoState();

        //初始化Thumb信息
        mThumbsList = new LinkedList<>();
        ThumbInfo head = new ThumbInfo();
        head.type = DRAG_HEAD;
        ThumbInfo foot = new ThumbInfo();
        foot.type = DRAG_FOOT;
        mThumbsList.add(head);
        int pts = 0;
        while (pts < VideoUtil.mDuration) {
            ThumbInfo img = new ThumbInfo();
            img.type = DRAG_IMG;
            img.imgPath = VideoUtil.getThumbJpg(this, VideoUtil.mTargetPath, pts);
            pts += 1000000;
            mThumbsList.add(img);
        }
        mThumbsList.add(foot);

        mSurfaceView = findViewById(R.id.glsurface_preview);
        mSurfaceView.setEGLContextClientVersion(3);
        mSurfaceView.setRenderer(new GLSurfaceView.Renderer() {
            @Override
            public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                mARContext = new ARContext();
                mARContext.create();
                startVideoService();
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
        mTimeInfo = findViewById(R.id.text_duration);
        mPlayBtn = findViewById(R.id.image_play);
        mPlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mVideoState.status == VideoState.PLAY) {
                    mVideoState.status = VideoState.PAUSE;
                } else {
                    mVideoState.status = VideoState.PLAY;
                }
                freshUI();
            }
        });
        mFullScreenBtn = findViewById(R.id.image_fullscreen);
        mTabRecyclerView = findViewById(R.id.recyclerview_tab_mode);
        mTabRecyclerView.setLayoutManager(new LinearLayoutManager(this, HORIZONTAL, false));
        mTabRecyclerView.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View layout = getLayoutInflater().inflate(R.layout.layout_tab_item, parent, false);
                ImageViewHolder imageViewHolder = new ImageViewHolder(layout);
                imageViewHolder.itemView.getLayoutParams().width = (int) (60 * getResources().getDisplayMetrics().density);
                imageViewHolder.itemView.getLayoutParams().height = (int) (60 * getResources().getDisplayMetrics().density);
                imageViewHolder.imageView = layout.findViewById(R.id.image_video_thumb);
                imageViewHolder.textView = layout.findViewById(R.id.text_video_info);
                return imageViewHolder;
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ImageViewHolder imageViewHolder = (ImageViewHolder) holder;
                imageViewHolder.imageView.setImageResource(TAB_INFO_LIST[2 * position]);
                imageViewHolder.textView.setText(TAB_INFO_LIST[2 * position + 1]);
                imageViewHolder.itemView.setOnClickListener(v -> {
                    Toast.makeText(VideoEditActivity.this, "待实现", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public int getItemCount() {
                return TAB_INFO_LIST.length / 2;
            }
        });
        mTabRecyclerView.getAdapter().notifyDataSetChanged();
        mThumbDragRecyclerView = findViewById(R.id.recyclerview_drag_thumb);
        mThumbDragRecyclerView.setLayoutManager(new LinearLayoutManager(this, HORIZONTAL, false));
        mThumbDragRecyclerView.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ImageView view = new ImageView(parent.getContext());
                if (viewType == DRAG_HEAD || viewType == DRAG_FOOT) {
                    view.setLayoutParams(new ViewGroup.LayoutParams(
                            parent.getMeasuredWidth() / 2, (int) (80 * getResources().getDisplayMetrics().density)));
                } else {
                    view.setLayoutParams(new ViewGroup.LayoutParams(
                            (int) (80 * getResources().getDisplayMetrics().density),
                            (int) (80 * getResources().getDisplayMetrics().density)));
                }
                return new ThumbViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (getItemViewType(position) == DRAG_HEAD || getItemViewType(position) == DRAG_FOOT) {
                    holder.itemView.setBackgroundColor(0xFF000000);
                } else {
                    ImageView imageView = (ImageView) holder.itemView;
                    imageView.setScaleType(ImageView.ScaleType.FIT_XY);
                    Glide.with(VideoEditActivity.this)
                            .load(mThumbsList.get(position).imgPath)
                            .into(imageView);
                }
            }

            @Override
            public int getItemCount() {
                return mThumbsList.size();
            }

            @Override
            public int getItemViewType(int position) {
                return mThumbsList.get(position).type;
            }
        });
        mThumbDragRecyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                Log.d(TAG, "onScrollStateChanged#" + newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    videoSeek(true, -1);
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    videoSeek(false, -1);
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                mScrollX += dx;
                if (mVideoState != null) {
                    int slotWidth = (int) (80 * getResources().getDisplayMetrics().density);
                    if (mVideoState.isSeek) {
                        videoSeek(true, (long) (mScrollX / slotWidth * 1000000 + mScrollX % slotWidth * 1.0f / slotWidth * 1000000.f));
                    }
                    Log.d(TAG, "mScrollX@" + mScrollX + "@slotWidth@" + slotWidth);
                }
            }
        });
        checkPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
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

    //不同线程都可以刷新UI
    private void freshUI() {
        getWindow().getDecorView().post(() -> {
            if (mVideoState != null) {
                long durationInS = mVideoState.duration / 1000000;
                long positionInS = mVideoState.position / 1000000;
                mTimeInfo.setText(String.format("%02d:%02d / %02d:%02d",
                        (int) (positionInS / 60 % 60), (int) (positionInS % 60),
                        (int) (durationInS / 60 % 60), (int) (durationInS % 60)));
                mPlayBtn.setImageResource(mVideoState.status == VideoState.PLAY ? R.drawable.icon_video_pause : R.drawable.icon_video_play);
//                int correctScrollX = (int) (mVideoState.position / 1000000.f * (80 * getResources().getDisplayMetrics().density));
//                mThumbDragRecyclerView.scrollBy(correctScrollX - mThumbDragRecyclerView.getScrollX(), 0);
//                mThumbDragRecyclerView.getAdapter().notifyDataSetChanged();
                dumpVideoState();
            }
        });
    }

    //视频处理部分
    private void startVideoService() {
        mHandlerThread = new HandlerThread("VideoDaemon");
        mHandlerThread.start();
        int[] surface = new int[1];
        GLES30.glGenTextures(1, surface, 0);
        if (surface[0] == -1) return;//申请不到texture,出错
        mVideoState.surfaceTexture = new SurfaceTexture(surface[0]);
        mVideoState.surfaceTextureId = surface[0];
        mVideoState.surface = new Surface(mVideoState.surfaceTexture);
        mHandler = new Handler(mHandlerThread.getLooper());
        mHandler.post(() -> {
            MediaExtractor mediaExtractor = new MediaExtractor();
            int videoTrackIndex = -1;
            try {
                mediaExtractor.setDataSource(VideoUtil.mTargetPath);
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
                    if (mediaCodec == null) return;
                    mediaCodec.configure(videoFormat, mVideoState.surface, null, 0);
                    mediaCodec.start();
                    ByteBuffer sampleBuffer = ByteBuffer.allocate(videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
                    mVideoState.duration = mediaExtractor.getTrackFormat(videoTrackIndex).getLong(MediaFormat.KEY_DURATION);
                    while (!mVideoState.isExit) {
                        if (!(mVideoState.isFirstOpen || mVideoState.isSeek || !mVideoState.isOutputEOF || !mVideoState.isInputEOF)) {
                            Thread.sleep(20);
                            continue;
                        }
                        if (mVideoState.isFirstOpen || mVideoState.isSeek) {
                            mediaExtractor.seekTo(mVideoState.position, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            mediaCodec.flush();
                            if (!mVideoState.isInputEOF) {
                                int inputBufIdx = mediaCodec.dequeueInputBuffer(0);
                                if (inputBufIdx >= 0) {
                                    int sampleSize = mediaExtractor.readSampleData(sampleBuffer, 0);
                                    if (sampleSize < 0) {
                                        sampleSize = 0;
                                        mVideoState.isInputEOF = true;
                                        Log.d(TAG, "mVideoState.isInputEOF");
                                    }
                                    mediaCodec.getInputBuffer(inputBufIdx).put(sampleBuffer);
                                    mediaCodec.queueInputBuffer(inputBufIdx, 0,
                                            sampleSize,
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
                                    Log.d(TAG, "isSeekDone");
                                    mVideoState.isSeekDone = true;
                                } else if (outputBufIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                    Log.d(TAG, "INFO_TRY_AGAIN_LATER:" + bufferInfo.presentationTimeUs);
                                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED:" + bufferInfo.presentationTimeUs);
                                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                    Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED:" + bufferInfo.presentationTimeUs);
                                }
                            }

                            if (mVideoState.isFirstOpen) {
                                mVideoState.isFirstOpen = false;
                            }

                            freshUI();
                        } else {
                            if (mVideoState.status != VideoState.PLAY) {
                                continue;
                            }
                            if (!mVideoState.isInputEOF) {
                                int inputBufIdx = mediaCodec.dequeueInputBuffer(0);
                                if (inputBufIdx >= 0) {
                                    int sampleSize = mediaExtractor.readSampleData(sampleBuffer, 0);
                                    if (sampleSize < 0) {
                                        sampleSize = 0;
                                        mVideoState.isInputEOF = true;
                                        Log.d(TAG, "mVideoState.isInputEOF");
                                    }
                                    mediaCodec.getInputBuffer(inputBufIdx).put(sampleBuffer);
                                    mediaCodec.queueInputBuffer(inputBufIdx, 0,
                                            sampleSize,
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
                                    //音视频同步逻辑
                                    long now = System.nanoTime() / 1000;
                                    long delta = bufferInfo.presentationTimeUs - (mVideoState.videoTime + now);
                                    boolean needShown = mVideoState.videoTime == Long.MIN_VALUE || (delta > 0 && delta < 100000);
                                    if (needShown && mVideoState.videoTime != Long.MIN_VALUE) {
                                        Thread.sleep(delta / 1000, (int) (delta % 1000));
                                    }
                                    mediaCodec.releaseOutputBuffer(outputBufIdx, needShown);
                                    mVideoState.videoTime = bufferInfo.presentationTimeUs - System.nanoTime() / 1000;
                                    mVideoState.position = bufferInfo.presentationTimeUs;
                                    mSurfaceView.requestRender();
                                    freshUI();
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
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void videoPlay() {

    }

    private void videoPause() {

    }

    private void videoSeek(boolean isSeek, long position) {
        Log.d(TAG, "videoSeek#" + isSeek + "#position#" + position);
        mVideoState.isSeek = isSeek;
        if (isSeek) {
            if (position != -1) {
                mVideoState.position = position;
            }
            mVideoState.isInputEOF = false;
            mVideoState.isOutputEOF = false;
        }
    }

    private void dumpVideoState() {
        Log.d(TAG, "VideoState#Duration#" + mVideoState.duration + "\n" +
                "Position#" + mVideoState.position);
    }

}