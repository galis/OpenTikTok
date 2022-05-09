package com.galix.opentiktok.ui;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.galix.avcore.util.FileUtils;
import com.galix.avcore.util.VideoUtil;
import com.galix.opentiktok.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;


/**
 * 资源筛选Activity
 * 固定从/sdcard开始搜索
 *
 * @Author:Galis
 * @Date:2022.01.16
 */
public class VideoPickActivity extends BaseActivity {

    private static final String TAG = VideoPickActivity.class.getSimpleName();
    public static final int REQ_PICK = 0;
    private HandlerThread mLoadThread;
    private Handler mLoadHandler;
    private ArrayList<VideoUtil.FileEntry> mFileCache;
    private RecyclerView mRecyclerView;
    private ContentLoadingProgressBar mProgressBar;
    public static LinkedList<VideoUtil.FileEntry> mFiles = new LinkedList<>();

    public static void start(Activity context) {
        Intent intent = new Intent(context, VideoPickActivity.class);
        context.startActivityForResult(intent, REQ_PICK);
    }


    private static class ImageViewHolder extends RecyclerView.ViewHolder {

        public ImageView imageView;
        public ImageView pickBtn;
        public TextView textView;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFiles.clear();
        setContentView(R.layout.activity_video_pick);

        //Actionbar
        getSupportActionBar().setTitle(R.string.choose_video);
        mProgressBar = findViewById(R.id.pb_loading);
        mProgressBar.hide();
        mRecyclerView = findViewById(R.id.recyclerview_preview);
        mRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        mRecyclerView.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View layout = getLayoutInflater().inflate(R.layout.layout_video_info, parent, false);
                ImageViewHolder imageViewHolder = new ImageViewHolder(layout);
                imageViewHolder.itemView.getLayoutParams().width = parent.getMeasuredWidth() / 2;
                imageViewHolder.itemView.getLayoutParams().height = parent.getMeasuredWidth() / 2;
                imageViewHolder.imageView = layout.findViewById(R.id.image_video_thumb);
                imageViewHolder.pickBtn = layout.findViewById(R.id.image_pick);
                imageViewHolder.textView = layout.findViewById(R.id.text_video_info);
                return imageViewHolder;
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ImageViewHolder imageViewHolder = (ImageViewHolder) holder;
                imageViewHolder.imageView.setImageBitmap(mFileCache.get(position).thumb);
                VideoUtil.FileEntry fileEntry = mFileCache.get(position);
                imageViewHolder.pickBtn.setSelected(
                        mFiles.contains(fileEntry)
                );
                imageViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mFiles.contains(fileEntry)) {
                            mFiles.remove(fileEntry);
                        } else {
                            mFiles.add(fileEntry);
                        }
                        notifyDataSetChanged();
                    }
                });
                imageViewHolder.textView.setText(
                        String.format("width:%d\nheight:%d\nduration:%ds\npath:%s",
                                fileEntry.width, fileEntry.height, fileEntry.duration / 1000000, fileEntry.path));
            }

            @Override
            public int getItemCount() {
                return mFileCache.size();
            }

        });

        //创建线程开始加载
        mLoadThread = new HandlerThread("LoadResource");
        mLoadThread.start();
        mLoadHandler = new Handler(mLoadThread.getLooper());
        mLoadHandler.post(() -> {
            long now1 = System.currentTimeMillis();
            mFileCache = new ArrayList<>();
            List<String> targetPaths = new LinkedList<>();
            targetPaths.add(getCacheDir().toString());
            targetPaths.add(Environment.getExternalStorageDirectory().getPath());//搜索sdcard目录
            targetPaths.add(FileUtils.getCompositeDir(VideoPickActivity.this));//搜索cache composite目录
            List<File> mp4List = new LinkedList<>();
            for (String path : targetPaths) {
                File dir = new File(path);
                if (!dir.exists()) {
                    continue;
                }
                File[] mp4s = dir.listFiles((dir1, name) -> name.endsWith(".mp4"));
                if (mp4s != null && mp4s.length > 0) {
                    mp4List.addAll(Arrays.asList(mp4s));
                }
            }
//            List<File> mp4List = new LinkedList<>();
//            mp4List.add(new File("/sdcard/test.mp4"));
            for (File mp4 : mp4List) {
                MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                try {
                    mediaMetadataRetriever.setDataSource(mp4.getAbsolutePath());
                    if (Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)) > 0) {
                        Log.d(TAG, mp4.getAbsolutePath());
                        VideoUtil.FileEntry fileEntry = new VideoUtil.FileEntry();
                        fileEntry.duration = Integer.parseInt(mediaMetadataRetriever.extractMetadata
                                (MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;
                        fileEntry.thumb = mediaMetadataRetriever.getFrameAtIndex(0);
                        fileEntry.width = Integer.parseInt(mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                        fileEntry.height = Integer.parseInt(mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                        fileEntry.path = mp4.getAbsolutePath();
                        fileEntry.adjustPath = VideoUtil.getAdjustGopVideoPath(VideoPickActivity.this, fileEntry.path);
                        mFileCache.add(fileEntry);
                        getWindow().getDecorView().post(() -> mRecyclerView.getAdapter().notifyDataSetChanged());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                } finally {
                    mediaMetadataRetriever.release();
                }
            }
            long now2 = System.currentTimeMillis();
            Log.d(TAG, "Filter mp4 on /sdcard : Use#" + (now2 - now1));
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_video_pick, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        if (item.getItemId() == R.id.action_done) {
            handleVideo();
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mLoadThread != null) {
            try {
                mLoadHandler.getLooper().quit();
                mLoadThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleVideo() {
        //跳转前先处理资源
        if (!mProgressBar.isShown()) {
            mProgressBar.show();
        }
        VideoUtil.processVideo(VideoPickActivity.this, mFiles, msg -> {
            setResult(REQ_PICK);
            finish();
            return true;
        });
    }
}